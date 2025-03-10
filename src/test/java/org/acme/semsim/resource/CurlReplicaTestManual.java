package org.acme.semsim.resource;

import io.quarkus.logging.Log;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import org.junit.jupiter.api.Test;

import static org.acme.semsim.TestUtils.pollForResults;
import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.*;

import java.io.File;

// NOTE: Has large samples, therefore only to be run manually.
// The following methods are used to suppress it:
// - "Manual" in name prevents Maven from running it with `./mvnw test`
// - Suppressed from Quarkus automated testing via application.properties.
@QuarkusTest
public class CurlReplicaTestManual {

	private static final String BASE_URL = "http://localhost:8080";

	/**
	 * Helper method to print section headers
	 * 
	 * @param sectionTitle The title of the section
	 */
	private void printSection(String sectionTitle) {
		Log.info("\n============================================================");
		Log.info("  " + sectionTitle);
		Log.info("============================================================\n");
	}

	/**
	 * Helper method to print the endpoint URL and pretty print the response
	 * 
	 * @param method   The HTTP method used
	 * @param endpoint The endpoint URL
	 * @param response The response object
	 */
	private void logResponse(String method, String endpoint, Response response) {
		Log.info("Testing endpoint: " + method + " " + endpoint);
		Log.info("Response status code: " + response.getStatusCode());
		Log.info("Response body:");
		Log.info(response.then().extract().body().asPrettyString());
		Log.info("\n");
	}

	@Test
	public void noEmbeddingsGenerated() throws Exception {
		printSection("No Embeddings Generated Test");

		// 1. Submit an XML document for processing
		String endpoint = "/api/similarity?elements=p";
		Response postResponse = given()
				.contentType(ContentType.XML)
				.body(new File("samples/sample_xs.xml"))
				.when()
				.post(endpoint);

		logResponse("POST", endpoint, postResponse);

		String sessionId = postResponse.getCookie("session_id");
		assertNotNull(sessionId, "Session ID should not be null");

		// 2. Retrieve similarity results
		endpoint = "/api/similarity/results";
		Response getResponse = pollForResults(sessionId);

		logResponse("GET", endpoint, getResponse);

		assertEquals(400, getResponse.getStatusCode(), "Expected status code 400");
		assertTrue(getResponse.getBody().asString().contains("No embeddings were generated"),
				"Response should indicate no embeddings were generated");
	}

	@Test
	public void triggerAcceptedGetResponse() throws Exception {
		printSection("Trigger Accepted GET Response Test");

		// Submit an XML document for processing
		String endpoint = "/api/similarity?elements=paragraph";
		Response postResponse = given()
				.contentType(ContentType.XML)
				.body(new File("samples/sample_xs.xml"))
				.when()
				.post(endpoint);

		logResponse("POST", endpoint, postResponse);

		String sessionId = postResponse.getCookie("session_id");
		assertNotNull(sessionId, "Session ID should not be null");

		// Retrieve similarity results directly, no polling
		endpoint = "/api/similarity/results";
		Response getResponse = given()
				.cookie("session_id", sessionId)
				.when()
				.get(endpoint);

		logResponse("GET", endpoint, getResponse);

		assertEquals(202, getResponse.getStatusCode(), "Expected status code 202");
		assertTrue(getResponse.getBody().asString().contains("Processing in progress"),
				"Response should indicate processing in progress");

	}

	@Test
	public void smallSampleWithPolling() throws Exception {
		printSection("Small Sample (DITA) Test");

		// Submit a DITA document for processing
		String endpoint = "/api/similarity/";
		Response postResponse = given()
				.contentType(ContentType.XML)
				.body(new File("samples/sample_s.dita"))
				.when()
				.post(endpoint);

		logResponse("POST", endpoint, postResponse);

		String sessionId = postResponse.getCookie("session_id");
		assertNotNull(sessionId, "Session ID should not be null");

		// Retrieve similarity results
		endpoint = "/api/similarity/results";
		Response getResponse = pollForResults(sessionId);

		logResponse("GET", endpoint, getResponse);

		assertEquals(200, getResponse.getStatusCode(), "Expected status code 200");
	}

	@Test
	public void largeSampleWithPolling() throws Exception {
		printSection("Small Sample (DITA) Test");

		// Submit a DITA document for processing
		String endpoint = "/api/similarity/";
		Response postResponse = given()
				.contentType(ContentType.XML)
				.body(new File("samples/sample_l.dita"))
				.when()
				.post(endpoint);

		logResponse("POST", endpoint, postResponse);

		String sessionId = postResponse.getCookie("session_id");
		assertNotNull(sessionId, "Session ID should not be null");

		// Retrieve similarity results
		endpoint = "/api/similarity/results";
		Response getResponse = pollForResults(sessionId);

		logResponse("GET", endpoint, getResponse);

		assertEquals(200, getResponse.getStatusCode(), "Expected status code 200");
	}


	@Test
	public void validationErrorForElementsParameterAndNonUUIDSessionCookie() throws Exception {
		printSection("Validation Error for Elements Parameter Test: Elements parameter validation failed");

		// URL-encoded example for //paragraph
		String endpoint = "/api/similarity?elements=%2F%2Fparagraph";
		Response postResponse = given()
				.contentType(ContentType.XML)
				.body(new File("samples/sample_xs.xml"))
				.when()
				.post(endpoint);

		logResponse("POST", endpoint, postResponse);

		assertEquals(400, postResponse.getStatusCode(), "Expected status code 400");
		assertTrue(postResponse.getBody().asString().contains("Elements parameter validation failed"),
				"Response should indicate validation failure");

		String sessionId = postResponse.getCookie("session_id");
		// Try to retrieve results without a valid session
		Response getResponse = pollForResults(sessionId);
		logResponse("GET", endpoint, getResponse);

		assertEquals(400, getResponse.getStatusCode(), "Expected status code 400");
		assertTrue(getResponse.getBody().asString().contains("Invalid session ID, not UUID format."),
				"Response should indicate invalid session");
	}

	@Test
	public void endToEndIntegration() throws Exception {
		printSection("End-to-End Integration Test");

		// 1. Submit an XML document for processing
		String endpoint = "/api/similarity?elements=paragraph";
		Response postResponse = given()
				.contentType(ContentType.XML)
				.body(new File("samples/sample_xs.xml"))
				.when()
				.post(endpoint);

		logResponse("POST", endpoint, postResponse);

		String sessionId = postResponse.getCookie("session_id");
		assertNotNull(sessionId, "Session ID should not be null");

		// 2. Poll for results
		endpoint = "/api/similarity/results";
		Response response = pollForResults(sessionId);

		logResponse("GET (polling)", endpoint, response);

		assertEquals(200, response.getStatusCode(), "Expected status code 200 after polling");
		assertNotNull(response.getBody(), "Response body should not be null");
	}

}