package org.acme.semsim.service;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.acme.semsim.model.SessionData;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
public class SimilarityProcessingServiceTest {

	@Inject
	SimilarityProcessingService similarityProcessingService;

	@Inject
	SessionService sessionService;

	private static final String XML_SAMPLE = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
			"<document>\n" +
			"\t<title>Sample Document for Similarity Testing</title>\n" +
			"\t<content>\n" +
			"\t\t<paragraph>\n" +
			"            This is a test paragraph. It contains several sentences.\n" +
			"            Similar sentences should be grouped together.\n" +
			"            This paragraph demonstrates how the similarity service works.\n" +
			"\t\t</paragraph>\n" +
			"\t</content>\n" +
			"</document>";

	@Test
	public void testStartProcessing() {
		// Test that processing starts and returns a session ID
		String sessionId = similarityProcessingService.startProcessing(XML_SAMPLE);

		assertNotNull(sessionId, "Session ID should not be null");
		assertFalse(sessionId.isEmpty(), "Session ID should not be empty");

		// Verify that a session was created
		SessionData sessionData = sessionService.getSession(sessionId);
		assertNotNull(sessionData, "Session data should exist");
	}

	@Test
	public void testGetSimilarityResults() throws Exception {
		// Start processing
		String sessionId = similarityProcessingService.startProcessing(XML_SAMPLE);

		// Wait for processing to complete (since it's async)
		Thread.sleep(2000);

		// Get results
		List<List<String>> results = similarityProcessingService.getSimilarityResults(sessionId);

		assertNotNull(results, "Results should not be null");
		assertFalse(results.isEmpty(), "Results should not be empty");

		// Verify that results contain sentence groups
		assertTrue(results.size() > 0, "Should have at least one similarity group");
		assertTrue(results.get(0).size() > 0, "Each group should have at least one sentence");
	}

	@Test
	public void testGetSimilarityResultsWithInvalidSession() {
		// Test with an invalid session ID
		List<List<String>> results = similarityProcessingService.getSimilarityResults("invalid-session-id");

		assertNull(results, "Results should be null for invalid session");
	}

	@Test
	public void testProcessingWithEmptyXml() throws Exception {
		// Test with empty XML
		String sessionId = similarityProcessingService.startProcessing("");

		// Wait for processing to complete
		Thread.sleep(1000);

		// Get results - should still have a valid session but possibly empty results
		List<List<String>> results = similarityProcessingService.getSimilarityResults(sessionId);

		// The service should handle empty XML gracefully
		assertNotNull(sessionId, "Session ID should not be null even for empty XML");
	}
}