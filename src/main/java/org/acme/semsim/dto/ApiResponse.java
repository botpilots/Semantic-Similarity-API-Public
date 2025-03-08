package org.acme.semsim.dto;

/**
 * Generic API response object.
 */
public class ApiResponse {
	private String message;
	private String error;
	private String sessionId;

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
}