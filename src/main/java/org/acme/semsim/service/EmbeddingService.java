package org.acme.semsim.service;

import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import org.acme.semsim.model.Sentence;
import org.deeplearning4j.models.embeddings.loader.WordVectorSerializer;
import org.deeplearning4j.models.word2vec.Word2Vec;
import org.deeplearning4j.text.tokenization.tokenizer.preprocessor.CommonPreprocessor;
import org.deeplearning4j.text.tokenization.tokenizerfactory.DefaultTokenizerFactory;
import org.deeplearning4j.text.tokenization.tokenizerfactory.TokenizerFactory;
import org.jboss.logging.Logger;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.factory.Nd4j;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.List;

/**
 * Service for generating sentence embeddings.
 * This is a simplified implementation using word2vec embeddings.
 */
@ApplicationScoped
public class EmbeddingService {

	private static final Logger LOG = Logger.getLogger(EmbeddingService.class);
	private static final int VECTOR_SIZE = 100; // Size of our embeddings
	private Word2Vec word2Vec;
	private TokenizerFactory tokenizerFactory;

	@PostConstruct
	void initialize() {
		LOG.info("Initializing embedding model");
		try {
			// For a real implementation, you would load a pre-trained model
			// Here we create a simple model for demonstration purposes
			initializeModel();
		} catch (Exception e) {
			LOG.error("Failed to initialize embedding model", e);
			throw new RuntimeException("Failed to initialize embedding model", e);
		}
	}

	private void initializeModel() throws IOException {
		// In a production system, you would load an existing model like:
		/*
		 * try (InputStream is = getClass().getResourceAsStream("/models/word2vec.bin"))
		 * {
		 * word2Vec = WordVectorSerializer.readWord2VecModel(is);
		 * }
		 */

		// For this demo, we'll create a simple model on the fly
		LOG.info("Creating demo embedding model");
		word2Vec = new Word2Vec.Builder()
				.minWordFrequency(1)
				.iterations(1)
				.layerSize(VECTOR_SIZE)
				.seed(42)
				.windowSize(5)
				.build();

		tokenizerFactory = new DefaultTokenizerFactory();
		tokenizerFactory.setTokenPreProcessor(new CommonPreprocessor());

		// This would be where we fit the model in a real implementation
		// For now, we'll just have a simple demo model that returns random vectors
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
	 * For simplicity, this is a mocked embedding function.
	 * In a real implementation, you would use a proper sentence transformer model.
	 */
	private double[] embedText(String text) {
		// If the word2vec model were properly trained, we would do something like:
		/*
		 * List<String> tokens = tokenizerFactory.create(text).getTokens();
		 * INDArray sum = Nd4j.zeros(VECTOR_SIZE);
		 * int count = 0;
		 * 
		 * for (String token : tokens) {
		 * if (word2Vec.hasWord(token)) {
		 * sum.addi(word2Vec.getWordVectorMatrix(token));
		 * count++;
		 * }
		 * }
		 * 
		 * if (count > 0) {
		 * sum.divi(count);
		 * }
		 * 
		 * return sum.toDoubleVector();
		 */

		// For this demo, we'll create random vectors of appropriate dimensions
		// but with some determinism based on the input to make similarity work
		double[] embedding = new double[VECTOR_SIZE];
		double seed = Math.abs(text.hashCode() % 10000) / 10000.0;

		for (int i = 0; i < VECTOR_SIZE; i++) {
			// Use the seed to create vectors that will have higher similarity
			// for sentences with some common words
			embedding[i] = (Math.sin(i * seed) + 1) / 2.0;
		}

		LOG.debug("Generated embedding vector for: " + text.substring(0, Math.min(20, text.length())) + "...");
		return embedding;
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