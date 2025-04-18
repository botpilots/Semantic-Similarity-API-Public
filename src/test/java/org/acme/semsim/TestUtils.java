package org.acme.semsim;

import static io.restassured.RestAssured.given;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.acme.semsim.dto.ApiResponse;

import io.quarkus.logging.Log;
import io.restassured.response.Response;

public class TestUtils {

	/**
	 * 
	 * Polls the results endpoint until it returns a 200 status code or times out.
	 * 
	 * @param sessionId The session ID to use for the request
	 * @return The response from the results endpoint
	 * @throws InterruptedException If the thread is interrupted while sleeping
	 */
	public static Response pollForResults(String sessionId)
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

	/**
	 * Gets the number of paragraphs in the JSON response body by counting the
	 * number of strings in the similarityGroups array.
	 * 
	 * @param response The response to get the paragraph count from
	 * @return The number of paragraphs in the response
	 */
	// NOTE: Will break test if similarityGroups data structure changes.
	public static int getParagraphCount(Response response) {
		ApiResponse apiResponse = response.getBody().as(ApiResponse.class);
		List<List<String>> similarityGroups = apiResponse.getSimilarityGroups();
		if (similarityGroups == null) {
			return 0;
		}
		int paragraphCount = 0;
		for (List<String> group : similarityGroups) {
			paragraphCount += group.size();
		}
		return paragraphCount;
	}

}
