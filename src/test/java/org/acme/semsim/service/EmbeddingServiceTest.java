package org.acme.semsim.service;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.acme.semsim.model.Sentence;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
public class EmbeddingServiceTest {

	@Inject
	EmbeddingService embeddingService;

	@Test
	public void testGenerateEmbedding() {
		// Test single sentence embedding
		String text = "This is a test sentence.";
		Sentence sentence = embeddingService.generateEmbedding(text);

		assertNotNull(sentence);
		assertEquals(text, sentence.getText());
		assertNotNull(sentence.getVector());
		assertTrue(sentence.getVector().length > 0);

		// Check that the vector is normalized (L2 norm should be close to 1.0)
		double norm = 0.0;
		for (double val : sentence.getVector()) {
			norm += val * val;
		}
		norm = Math.sqrt(norm);
		assertEquals(1.0, norm, 0.01);
	}

	@Test
	public void testGenerateEmbeddings() {
		// Test multiple sentence embeddings
		List<String> texts = Arrays.asList(
				"The quick brown fox jumps over the lazy dog.",
				"A sentence about artificial intelligence.",
				"Another completely different topic.");

		List<Sentence> sentences = embeddingService.generateEmbeddings(texts);

		assertNotNull(sentences);
		assertEquals(3, sentences.size());

		for (int i = 0; i < texts.size(); i++) {
			assertEquals(texts.get(i), sentences.get(i).getText());
			assertNotNull(sentences.get(i).getVector());
		}
	}

	@Test
	public void testCosineSimilarity() {
		// Test cosine similarity between similar and dissimilar sentences
		Sentence s1 = embeddingService.generateEmbedding("The weather is nice today.");
		Sentence s2 = embeddingService.generateEmbedding("It's a beautiful sunny day.");
		Sentence s3 = embeddingService.generateEmbedding("Artificial intelligence is transforming industries.");

		double sim1_2 = embeddingService.calculateCosineSimilarity(s1.getVector(), s2.getVector());
		double sim1_3 = embeddingService.calculateCosineSimilarity(s1.getVector(), s3.getVector());

		// Similar sentences should have higher similarity
		assertTrue(sim1_2 > sim1_3);

		// Similarity with self should be close to 1.0
		double selfSim = embeddingService.calculateCosineSimilarity(s1.getVector(), s1.getVector());
		assertEquals(1.0, selfSim, 0.0001);
	}
}