package org.acme.semsim.resource;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
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
		Thread.sleep(250);

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

		// Test with a url encoded XPath
		String sessionId = given()
				.contentType(ContentType.XML)
				.body(XML_SAMPLE)
				.when()
				.post("/api/similarity?xpath=%2F%2Fparagraph") // URL-encoded //paragraph
				.then()
				.statusCode(202)
				.extract()
				.cookie("session_id");

		// Wait for processing
		Thread.sleep(250);

		// Get results for title-only XPath
		List<List<String>> results = given()
				.cookie("session_id", sessionId)
				.when()
				.get("/api/similarity/results")
				.then()
				.statusCode(200)
				.extract()
				.as(new io.restassured.common.mapper.TypeRef<List<List<String>>>() {
				});

		// Title might not form similarity groups if it's just one sentence
		assertNotNull(results, "Results should not be null");
	}
}