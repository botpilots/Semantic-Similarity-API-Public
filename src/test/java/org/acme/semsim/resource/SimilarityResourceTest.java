package org.acme.semsim.resource;

import io.quarkus.logging.Log;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import org.junit.jupiter.api.Test;

import java.util.List;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
public class SimilarityResourceTest {

	private static final String XML_SAMPLE = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
			"<document>\n" +
			"\t<title>Sample Document for Similarity Testing</title>\n" +
			"\t<content>\n" +
			"\t\t<paragraph>\n" +
			"            This is a test paragraph with very similar content. \n" +
			"            This is a test paragraph with very similar content. \n" +
			"            This paragraph demonstrates similarity testing.\n" +
			"            Another paragraph with similar content for testing.\n" +
			"            Yet another paragraph with similar content for testing.\n" +
			"            This paragraph also tests similarity with slight variations.\n" +
			"            Similarity testing is demonstrated in this paragraph as well.\n" +
			"            The content of this paragraph is similar to the others.\n" +
			"            Testing similarity with multiple paragraphs is the goal here.\n" +
			"\t\t</paragraph>\n" +
			"\t</content>\n" +
			"</document>";

	/**
	 * Polls the results endpoint until it returns a 200 status code or times out.
	 * 
	 * @param sessionId      The session ID to use for the request
	 * @param maxAttempts    Maximum number of polling attempts
	 * @param pollIntervalMs Time to wait between polling attempts in milliseconds
	 * @return The response from the results endpoint
	 * @throws InterruptedException If the thread is interrupted while sleeping
	 */
	private Response pollForResults(String sessionId)
			throws InterruptedException {
		Response response = null;
		int attempts = 0;
		Response previousResponse = null;
		long pollIntervalMs = 50;

		while (true) {
			response = given()
					.cookie("session_id", sessionId)
					.when()
					.get("/api/similarity/results");

			if (response.getStatusCode() == 200 && !response.equals(previousResponse)) {
				Log.info("New response received after " + attempts + " attempts, returning response");
				// New response received, return the response
				return response;
			} else if (response.getStatusCode() == 202) {
				// Still processing, wait and try again
				attempts++;
				Thread.sleep(pollIntervalMs);
				pollIntervalMs += 50; // Increase pollIntervalMs by 50 ms
				Log.info("Still processing, waiting for " + pollIntervalMs + " ms and trying again");
			} else {

				// Unexpected status code, return the response
				return response;
			}
			previousResponse = response;
		}
	}

	@Test
	public void testProcessXml() {
		// Test submitting XML for processing
		given()
				.contentType(ContentType.XML)
				.body(XML_SAMPLE)
				.when()
				.post("/api/similarity")
				.then()
				.statusCode(202)
				.cookie("session_id")
				.contentType(ContentType.JSON);
	}

	@Test
	public void testGetResultsWithoutSession() {
		// Test getting results without a session cookie
		given()
				.when()
				.get("/api/similarity/results")
				.then()
				.statusCode(400)
				.contentType(ContentType.JSON);
	}

	@Test
	public void testEndToEndFlow() throws Exception {
		// Test the full flow: submit XML and get results

		// 1. Submit XML for processing
		String sessionId = given()
				.contentType(ContentType.XML)
				.body(XML_SAMPLE)
				.when()
				.post("/api/similarity?elements=paragraph")
				.then()
				.statusCode(202)
				.extract()
				.cookie("session_id");

		assertNotNull(sessionId, "Session ID should not be null");

		// 2. Poll for results using the session cookie
		Response response = pollForResults(sessionId);

		// 3. Verify the response
		assertEquals(200, response.getStatusCode(), "Expected status code 200 after polling");
		assertNotNull(response.getBody(), "Response body should not be null");
	}

	@Test
	public void testInvalidXml() {
		// Test submitting invalid XML
		given()
				.contentType(ContentType.XML)
				.body("<invalid>xml")
				.when()
				.post("/api/similarity")
				.then()
				.statusCode(500) // Should return 500 for invalid XML
				.contentType(ContentType.JSON);
	}

	@Test
	public void testEmptyXml() {
		// Test submitting empty XML
		given()
				.contentType(ContentType.XML)
				.body("")
				.when()
				.post("/api/similarity")
				.then()
				.statusCode(400)
				.contentType(ContentType.JSON);
	}

	@Test
	public void testProcessXmlWithXPath() {

		// Test with a url encoded XPath
		given()
				.contentType(ContentType.XML)
				.body(XML_SAMPLE)
				.when()
				.post("/api/similarity?elements=%2F%2Fparagraph") // URL-encoded //paragraph
				.then()
				// Should produce Bad request status code 400.
				.statusCode(400)
				.extract()
				.cookie("session_id");
	}
}