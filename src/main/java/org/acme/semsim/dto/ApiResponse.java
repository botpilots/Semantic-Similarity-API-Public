package org.acme.semsim.dto;

import java.util.List;

import io.quarkus.runtime.annotations.RegisterForReflection;

/**
 * Generic API response object.
 */
@RegisterForReflection
public class ApiResponse {
	private String message;
	private String error;
	private String sessionId;
	private List<List<String>> similarityGroups;

	public ApiResponse() {
	}

	public ApiResponse(String message) {
		this.message = message;
	}

	public ApiResponse(String message, String sessionId) {
		this.message = message;
		this.sessionId = sessionId;
	}

	public ApiResponse(String message, String error, String sessionId) {
		this.message = message;
		this.error = error;
		this.sessionId = sessionId;
	}

	// TODO: Make something better very soon...
	// Should deliver a JSON with the following structure:
	// - textDuplicateGroups: A a list of objects each having the follinwg
	// properties based on TextDuplicatesGroup.
	// - sentences: A list of objects each having the following properties based on
	// TextDuplicates.
	// - standardMeasure: The standardMeasure sentence for the group.
	// // - a : The similarity score for the group.
	public ApiResponse(String message, String error, String sessionId, List<List<String>> similarityGroups) {
		this.message = message;
		this.error = error;
		this.sessionId = sessionId;
		this.similarityGroups = similarityGroups;
	}

	public String getMessage() {
		return message;
	}

	public void setMessage(String message) {
		this.message = message;
	}

	public String getError() {
		return error;
	}

	public void setError(String error) {
		this.error = error;
	}

	public String getSessionId() {
		return sessionId;
	}

	public void setSessionId(String sessionId) {
		this.sessionId = sessionId;
	}

	public List<List<String>> getSimilarityGroups() {
		return similarityGroups;
	}

	public void setSimilarityGroups(List<List<String>> similarityGroups) {
		this.similarityGroups = similarityGroups;
	}
}