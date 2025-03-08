package org.acme.semsim.service;

import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.acme.semsim.model.Sentence;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import ai.djl.huggingface.tokenizers.HuggingFaceTokenizer;
import ai.djl.huggingface.tokenizers.Encoding;
import ai.djl.inference.Predictor;
import ai.djl.ndarray.NDArray;
import ai.djl.ndarray.NDList;
import ai.djl.ndarray.NDManager;
import ai.djl.ndarray.types.Shape;
import ai.djl.ndarray.types.DataType;
import ai.djl.onnxruntime.engine.OrtEngine;
import ai.djl.repository.zoo.Criteria;
import ai.djl.repository.zoo.ZooModel;
import ai.djl.translate.Batchifier;
import ai.djl.translate.Translator;
import ai.djl.translate.TranslatorContext;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * Service for generating sentence embeddings using the all-MiniLM-L6-v2 ONNX
 * model.
 */
@ApplicationScoped
public class EmbeddingService {

	private static final Logger LOG = Logger.getLogger(EmbeddingService.class);

	@ConfigProperty(name = "semsim.embedding.vector.size", defaultValue = "384")
	int vectorSize;

	@ConfigProperty(name = "semsim.embedding.model.path", defaultValue = "models/all-MiniLM-L6-v2-onnx/model.onnx")
	String modelPath;

	@ConfigProperty(name = "semsim.embedding.tokenizer.path", defaultValue = "models/all-MiniLM-L6-v2-onnx")
	String tokenizerPath;

	private ZooModel<String, float[]> model;
	private Predictor<String, float[]> predictor;
	private NDManager manager;

	@PostConstruct
	void initialize() {
		LOG.info("Initializing embedding model: all-MiniLM-L6-v2");
		try {
			// Initialize the ONNX model
			initializeModel();
		} catch (Exception e) {
			LOG.error("Failed to initialize embedding model", e);
			throw new RuntimeException("Failed to initialize embedding model", e);
		}
	}

	private void initializeModel() throws IOException {
		LOG.info("Loading all-MiniLM-L6-v2 ONNX model from: " + modelPath);

		// Create NDManager
		manager = NDManager.newBaseManager();

		// Create criteria for loading the model
		Path modelFilePath = Paths.get(getClass().getClassLoader().getResource(modelPath).getPath());
		Path tokenizerDirPath = Paths.get(getClass().getClassLoader().getResource(tokenizerPath).getPath());

		Criteria<String, float[]> criteria = Criteria.builder()
				.setTypes(String.class, float[].class)
				.optModelPath(modelFilePath)
				.optEngine(OrtEngine.ENGINE_NAME)
				.optTranslator(new SentenceTransformerTranslator(tokenizerDirPath))
				.build();

		try {
			model = criteria.loadModel();
			predictor = model.newPredictor();
			LOG.info("Successfully loaded all-MiniLM-L6-v2 ONNX model");
		} catch (Exception e) {
			LOG.error("Error loading model", e);
			throw new IOException("Failed to load model", e);
		}
	}

	/**
	 * Generate embeddings for a list of sentences.
	 * 
	 * @param sentenceTexts List of sentence texts
	 * @return List of Sentence objects with vector embeddings
	 */
	public List<Sentence> generateEmbeddings(List<String> sentenceTexts) {
		return sentenceTexts.stream()
				.map(this::generateEmbedding)
				.toList();
	}

	/**
	 * Generate an embedding for a single sentence.
	 * 
	 * @param text The sentence text
	 * @return A Sentence object with the vector embedding
	 */
	public Sentence generateEmbedding(String text) {
		try {
			float[] embedding = predictor.predict(text);
			double[] doubleEmbedding = new double[embedding.length];
			for (int i = 0; i < embedding.length; i++) {
				doubleEmbedding[i] = embedding[i];
			}
			LOG.debug("Generated embedding vector for: " + text.substring(0, Math.min(20, text.length())) + "...");
			return new Sentence(text, doubleEmbedding);
		} catch (Exception e) {
			LOG.error("Error generating embedding for text: " + text, e);
			// Fallback to a zero vector in case of error
			double[] zeroVector = new double[vectorSize];
			return new Sentence(text, zeroVector);
		}
	}

	/**
	 * Calculate cosine similarity between two vectors.
	 * 
	 * @param vector1 First vector
	 * @param vector2 Second vector
	 * @return Cosine similarity value (between -1 and 1)
	 */
	public double calculateCosineSimilarity(double[] vector1, double[] vector2) {
		if (vector1.length != vector2.length) {
			throw new IllegalArgumentException("Vectors must have the same dimensions");
		}

		double dotProduct = 0.0;
		double magnitude1 = 0.0;
		double magnitude2 = 0.0;

		for (int i = 0; i < vector1.length; i++) {
			dotProduct += vector1[i] * vector2[i];
			magnitude1 += vector1[i] * vector1[i];
			magnitude2 += vector2[i] * vector2[i];
		}

		magnitude1 = Math.sqrt(magnitude1);
		magnitude2 = Math.sqrt(magnitude2);

		if (magnitude1 == 0 || magnitude2 == 0) {
			return 0.0; // Handle division by zero
		}

		return dotProduct / (magnitude1 * magnitude2);
	}

	/**
	 * Custom translator for the all-MiniLM-L6-v2 ONNX model.
	 */
	private static class SentenceTransformerTranslator implements Translator<String, float[]> {
		private HuggingFaceTokenizer tokenizer;
		private final Path tokenizerPath;

		public SentenceTransformerTranslator(Path tokenizerPath) {
			this.tokenizerPath = tokenizerPath;
		}

		@Override
		public void prepare(TranslatorContext ctx) throws IOException {
			// Initialize the tokenizer with the model name/path
			tokenizer = HuggingFaceTokenizer.newInstance(tokenizerPath.toString());
		}

		@Override
		public NDList processInput(TranslatorContext ctx, String input) {
			// Tokenize the input text
			NDManager manager = ctx.getNDManager();

			// Create input tensors
			Encoding encoding = tokenizer.encode(input);
			long[] indices = encoding.getIds();
			long[] attentionMask = encoding.getAttentionMask();

			NDArray inputIds = manager.create(indices);
			NDArray attention = manager.create(attentionMask);
			NDArray tokenTypeIds = manager.zeros(new Shape(indices.length), DataType.INT64);

			return new NDList(inputIds, attention, tokenTypeIds);
		}

		@Override
		public float[] processOutput(TranslatorContext ctx, NDList list) {
			// The ONNX model output is the token embeddings
			// We need to perform mean pooling to get the sentence embedding
			NDArray tokenEmbeddings = list.get(0);

			// Get the last hidden state
			NDArray lastHiddenState = tokenEmbeddings;

			// Mean pooling
			NDArray meanPooled = lastHiddenState.mean(new int[] { 1 });

			// Normalize the embeddings (L2 norm)
			float[] embeddings = meanPooled.toFloatArray();
			float norm = 0.0f;
			for (float val : embeddings) {
				norm += val * val;
			}
			norm = (float) Math.sqrt(norm);

			// Avoid division by zero
			if (norm > 1e-9) {
				for (int i = 0; i < embeddings.length; i++) {
					embeddings[i] /= norm;
				}
			}

			return embeddings;
		}

		@Override
		public Batchifier getBatchifier() {
			return Batchifier.STACK;
		}
	}
}