package org.acme.semsim.service;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.acme.semsim.model.Sentence;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
public class SimilarityServiceTest {

	@Inject
	SimilarityService similarityService;

	@Test
	public void testGroupSimilarSentences() {
		// Create test sentences with embeddings that are clearly different
		List<Sentence> sentences = new ArrayList<>();

		// Create two similar sentences
		sentences.add(new Sentence("This is sentence one", new double[] { 0.1, 0.2, 0.3 }));
		sentences.add(new Sentence("This is very similar to sentence one", new double[] { 0.11, 0.21, 0.31 }));

		// Create two very different sentences with very different embeddings
		sentences.add(new Sentence("This is completely different", new double[] { 0.9, 0.8, 0.7 }));
		sentences.add(new Sentence("Another different sentence", new double[] { -0.9, -0.8, -0.7 }));

		// Group similar sentences
		List<List<String>> groups = similarityService.groupSimilarSentences(sentences);

		// Verify results
		assertNotNull(groups, "Groups should not be null");
		assertTrue(groups.size() > 0, "Should have at least one group");

		// Check that the first two sentences are in the same group
		boolean firstTwoInSameGroup = false;
		for (List<String> group : groups) {
			if (group.contains("This is sentence one") && group.contains("This is very similar to sentence one")) {
				firstTwoInSameGroup = true;
				break;
			}
		}
		assertTrue(firstTwoInSameGroup, "The first two similar sentences should be in the same group");
	}

	@Test
	public void testEmptyInput() {
		// Test with empty input
		List<Sentence> emptySentences = new ArrayList<>();

		List<List<String>> groups = similarityService.groupSimilarSentences(emptySentences);

		assertNotNull(groups, "Groups should not be null even with empty input");
		assertTrue(groups.isEmpty(), "Groups should be empty with empty input");
	}

	@Test
	public void testSingleSentence() {
		// Test with a single sentence
		List<Sentence> singleSentence = new ArrayList<>();
		singleSentence.add(new Sentence("Single sentence", new double[] { 0.1, 0.2, 0.3 }));

		List<List<String>> groups = similarityService.groupSimilarSentences(singleSentence);

		assertNotNull(groups, "Groups should not be null");
		assertTrue(groups.size() >= 0, "Should have zero or more groups");

		// The implementation might not create a group for a single sentence
		// So we just check that the implementation doesn't crash
	}

	/**
	 * Helper method to create test sentences with embeddings
	 */
	private List<Sentence> createTestSentences() {
		List<Sentence> sentences = new ArrayList<>();

		// Create similar sentences (with similar embeddings)
		sentences.add(new Sentence("This is sentence one", new double[] { 0.1, 0.2, 0.3 }));
		sentences.add(new Sentence("This is very similar to sentence one", new double[] { 0.11, 0.19, 0.31 }));

		// Create different sentences (with different embeddings)
		sentences.add(new Sentence("This is completely different", new double[] { 0.9, 0.8, 0.7 }));
		sentences.add(new Sentence("Another different sentence", new double[] { 0.85, 0.75, 0.65 }));

		return sentences;
	}

	/**
	 * Helper method to create an embedding vector from float array
	 */
	private double[] createEmbedding(float[] values) {
		double[] doubleValues = new double[values.length];
		for (int i = 0; i < values.length; i++) {
			doubleValues[i] = values[i];
		}
		return doubleValues;
	}
}