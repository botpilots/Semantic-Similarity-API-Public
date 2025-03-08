package org.acme.semsim.service;

import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import org.acme.semsim.model.Sentence;
import org.jboss.logging.Logger;

import java.io.IOException;
import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.LongBuffer;
import java.nio.FloatBuffer;
import java.util.Arrays;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.HashMap;
import java.io.BufferedReader;
import java.io.InputStreamReader;

import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtException;
import ai.onnxruntime.OrtSession;

/**
 * Service for generating sentence embeddings.
 * This implementation uses the all-MiniLM-L6-v2-onnx model.
 */
@ApplicationScoped
public class EmbeddingService {

	private static final Logger LOG = Logger.getLogger(EmbeddingService.class);
	private static final int VECTOR_SIZE = 384; // Size of embeddings from all-MiniLM-L6-v2
	private static final int MAX_SEQ_LENGTH = 512; // Maximum sequence length for the model

	private OrtEnvironment env;
	private OrtSession session;
	private Map<String, Integer> tokenToId;
	private Map<Integer, String> idToToken;

	@PostConstruct
	void initialize() {
		LOG.info("Initializing embedding model");
		try {
			initializeModel();
		} catch (Exception e) {
			LOG.error("Failed to initialize embedding model", e);
			throw new RuntimeException("Failed to initialize embedding model", e);
		}
	}

	private void initializeModel() throws IOException, OrtException {
		LOG.info("Loading all-MiniLM-L6-v2-onnx model");

		// Initialize ONNX Runtime environment
		env = OrtEnvironment.getEnvironment();

		// Load the model from resources
		// First, copy the model to a temporary file
		File modelFile = extractResourceToTempFile("/models/all-MiniLM-L6-v2-onnx/model.onnx", "model", ".onnx");

		// Create session options
		OrtSession.SessionOptions sessionOptions = new OrtSession.SessionOptions();

		// Create session with the model file
		session = env.createSession(modelFile.getAbsolutePath(), sessionOptions);

		// Load vocabulary
		loadVocabulary();

		LOG.info("Model loaded successfully");
	}

	private File extractResourceToTempFile(String resourcePath, String prefix, String suffix) throws IOException {
		File tempFile = File.createTempFile(prefix, suffix);
		tempFile.deleteOnExit();

		try (InputStream is = getClass().getResourceAsStream(resourcePath)) {
			if (is == null) {
				throw new IOException("Resource not found: " + resourcePath);
			}
			Files.copy(is, tempFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
		}

		return tempFile;
	}

	private void loadVocabulary() throws IOException {
		LOG.info("Loading vocabulary");
		tokenToId = new HashMap<>();
		idToToken = new HashMap<>();

		try (BufferedReader reader = new BufferedReader(
				new InputStreamReader(getClass().getResourceAsStream("/models/all-MiniLM-L6-v2-onnx/vocab.txt")))) {
			String line;
			int id = 0;
			while ((line = reader.readLine()) != null) {
				tokenToId.put(line, id);
				idToToken.put(id, line);
				id++;
			}
		}

		LOG.info("Vocabulary loaded: " + tokenToId.size() + " tokens");
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
		double[] vector = embedText(text);
		return new Sentence(text, vector);
	}

	/**
	 * Embed text using the all-MiniLM-L6-v2-onnx model.
	 */
	private double[] embedText(String text) {
		try {
			// Tokenize the input text
			List<Integer> tokens = tokenize(text);

			// Create input tensors
			Map<String, OnnxTensor> inputs = createInputTensors(tokens);

			// Run inference
			OrtSession.Result result = session.run(inputs);

			// Extract the last hidden state (embeddings)
			// The model outputs a tensor of shape [1, sequence_length, hidden_size]
			float[][][] lastHiddenState = (float[][][]) result.get(0).getValue();

			// Mean pooling to get sentence embedding
			double[] embedding = meanPooling(lastHiddenState[0], tokens.size());

			// Normalize the embedding
			normalize(embedding);

			// Close the result to free resources
			result.close();

			// Close the input tensors
			for (OnnxTensor tensor : inputs.values()) {
				tensor.close();
			}

			LOG.debug("Generated embedding vector for: " + text.substring(0, Math.min(20, text.length())) + "...");
			return embedding;

		} catch (Exception e) {
			LOG.error("Error generating embedding", e);
			// Return a zero vector in case of error
			return new double[VECTOR_SIZE];
		}
	}

	private List<Integer> tokenize(String text) {
		// Simple tokenization for demonstration
		// In a production system, you would use a proper tokenizer
		List<Integer> tokens = new ArrayList<>();

		// Add CLS token
		tokens.add(tokenToId.getOrDefault("[CLS]", 101));

		// Split by space and add tokens
		for (String word : text.split("\\s+")) {
			// If word is in vocabulary, add it
			// Otherwise, add unknown token
			tokens.add(tokenToId.getOrDefault(word.toLowerCase(), tokenToId.getOrDefault("[UNK]", 100)));

			// Check if we're approaching max sequence length
			if (tokens.size() >= MAX_SEQ_LENGTH - 1) {
				break;
			}
		}

		// Add SEP token
		tokens.add(tokenToId.getOrDefault("[SEP]", 102));

		return tokens;
	}

	private Map<String, OnnxTensor> createInputTensors(List<Integer> tokens) throws OrtException {
		Map<String, OnnxTensor> inputs = new HashMap<>();

		// Create input_ids tensor
		long[] inputIds = new long[tokens.size()];
		for (int i = 0; i < tokens.size(); i++) {
			inputIds[i] = tokens.get(i);
		}

		// Create attention_mask tensor
		long[] attentionMask = new long[tokens.size()];
		Arrays.fill(attentionMask, 1);

		// Create token_type_ids tensor
		long[] tokenTypeIds = new long[tokens.size()];
		Arrays.fill(tokenTypeIds, 0);

		// Create ONNX tensors with correct shapes
		// The model expects tensors of shape [batch_size, sequence_length]
		// where batch_size is 1 for a single sentence
		OnnxTensor inputIdsTensor = OnnxTensor.createTensor(env, new long[][] { inputIds });
		OnnxTensor attentionMaskTensor = OnnxTensor.createTensor(env, new long[][] { attentionMask });
		OnnxTensor tokenTypeIdsTensor = OnnxTensor.createTensor(env, new long[][] { tokenTypeIds });

		// Add tensors to input map
		inputs.put("input_ids", inputIdsTensor);
		inputs.put("attention_mask", attentionMaskTensor);
		inputs.put("token_type_ids", tokenTypeIdsTensor);

		return inputs;
	}

	private double[] meanPooling(float[][] lastHiddenState, int tokenCount) {
		double[] meanPooled = new double[VECTOR_SIZE];

		// Sum up the embeddings for all tokens (excluding padding)
		for (int i = 0; i < tokenCount; i++) {
			for (int j = 0; j < VECTOR_SIZE; j++) {
				meanPooled[j] += lastHiddenState[i][j];
			}
		}

		// Divide by token count to get mean
		for (int j = 0; j < VECTOR_SIZE; j++) {
			meanPooled[j] /= tokenCount;
		}

		return meanPooled;
	}

	private void normalize(double[] vector) {
		// Calculate magnitude
		double magnitude = 0.0;
		for (double v : vector) {
			magnitude += v * v;
		}
		magnitude = Math.sqrt(magnitude);

		// Normalize
		if (magnitude > 0) {
			for (int i = 0; i < vector.length; i++) {
				vector[i] /= magnitude;
			}
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
}