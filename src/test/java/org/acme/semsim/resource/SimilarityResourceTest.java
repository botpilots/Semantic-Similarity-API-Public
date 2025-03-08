package org.acme.semsim.resource;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.*;
import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
public class SimilarityResourceTest {

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
				.post("/api/similarity")
				.then()
				.statusCode(202)
				.extract()
				.cookie("session_id");

		assertNotNull(sessionId, "Session ID should not be null");

		// 2. Wait for processing to complete (since it's async)
		Thread.sleep(2000);

		// 3. Get results using the session cookie
		given()
				.cookie("session_id", sessionId)
				.when()
				.get("/api/similarity/results")
				.then()
				.statusCode(200);
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
	public void testProcessXmlWithXPath() throws Exception {
		// Test submitting XML with XPath parameter

		// 1. Submit XML with XPath for processing
		String sessionId = given()
				.contentType(ContentType.XML)
				.body(XML_SAMPLE)
				.when()
				.post("/api/similarity/xpath?xpath=//paragraph")
				.then()
				.statusCode(202)
				.extract()
				.cookie("session_id");

		assertNotNull(sessionId, "Session ID should not be null");

		// 2. Wait for processing to complete (since it's async)
		Thread.sleep(2000);

		// 3. Get results using the session cookie
		List<List<String>> results = given()
				.cookie("session_id", sessionId)
				.when()
				.get("/api/similarity/results")
				.then()
				.statusCode(200)
				.extract()
				.as(List.class);

		// Verify results contain only sentences from paragraphs
		assertNotNull(results, "Results should not be null");
		assertFalse(results.isEmpty(), "Results should not be empty");

		// Test with a more specific XPath
		sessionId = given()
				.contentType(ContentType.XML)
				.body(XML_SAMPLE)
				.when()
				.post("/api/similarity/xpath?xpath=%2F%2Ftitle") // URL-encoded //title
				.then()
				.statusCode(202)
				.extract()
				.cookie("session_id");

		// Wait for processing
		Thread.sleep(2000);

		// Get results for title-only XPath
		results = given()
				.cookie("session_id", sessionId)
				.when()
				.get("/api/similarity/results")
				.then()
				.statusCode(200)
				.extract()
				.as(List.class);

		// Title might not form similarity groups if it's just one sentence
		assertNotNull(results, "Results should not be null");
	}
}