package org.acme.semsim.resource;

import io.quarkus.logging.Log;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import org.junit.jupiter.api.Test;

import static org.acme.semsim.TestUtils.pollForResults;
import static org.acme.semsim.TestUtils.getParagraphCount;
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

	// Create new sample for multiple elements list
	private static final String XML_SAMPLE_MULTIPLE_ELEMENTS = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
			"<document>\n" +
			"\t<title>Introduction to the Topic</title>\n" +
			"\t<title>Introduction of the Topic again</title>\n" +
			"\t<content>\n" +
			"\t\t<paragraph>This is a test paragraph with very similar content.</paragraph>\n" +
			"\t\t<paragraph>This is another test paragraph showing similarity.</paragraph>\n" +
			"\t</content>\n" +
			"</document>";

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

	
	@Test
	public void testEndToEndFlowMultipleElements() throws Exception {
		// Test the full flow: submit XML and get results

		// 1. Submit XML for processing
		String sessionId = given()
				.contentType(ContentType.XML)
				.body(XML_SAMPLE_MULTIPLE_ELEMENTS)
				.when()
				.post("/api/similarity?elements=paragraph%20title")
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
		//
		Log.info("Response body: " + response.getBody().asString());

		int paragraphCount = getParagraphCount(response);
		assertEquals(4, paragraphCount, "Expected 4 paragraphs in the response");
	}

}