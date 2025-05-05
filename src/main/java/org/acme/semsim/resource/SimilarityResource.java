package org.acme.semsim.resource;

import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.Cookie;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.NewCookie;
import jakarta.ws.rs.core.Response;
import org.acme.semsim.dto.GroupingApiResponse;
import org.acme.semsim.dto.SearchApiResponse;
import org.acme.semsim.service.SessionService;
import org.acme.semsim.service.SimilarityProcessingService;
import org.acme.semsim.service.SearchService;
import org.jboss.logging.Logger;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

/**
 * REST API endpoint for similarity-related operations.
 */
@Path("/api/similarity")
public class SimilarityResource {

	private static final Logger LOG = Logger.getLogger(SimilarityResource.class);

	@Inject
	SimilarityProcessingService similarityProcessingService;

	@Inject
	SessionService sessionService;

	@Inject
	SearchService searchService;

	/**
	 * Submit an XML document for processing with specific element names.
	 *
	 * @param xmlContent The XML content to process
	 * @param elements      A space-separated string of element names to extract text
	 *                   from (e.g., "p li div")
	 *                   (default: "p")
	 * @param threshold  Optional similarity threshold value between 0.0 and 1.0
	 *                   (default: defined in configuration)
	 * @return Response with a session cookie
	 */
	@POST
	@Consumes(MediaType.APPLICATION_XML)
	@Produces(MediaType.APPLICATION_JSON)
	public Response apiSimilarity(
			String xmlContent, 
			@QueryParam("elements") @DefaultValue("p") String elements,
			@QueryParam("threshold") Double threshold,
			@QueryParam ("search") String search) {
		// We assume parameter elements is encoded, so we decode it
		try {
			elements = java.net.URLDecoder.decode(elements, StandardCharsets.UTF_8);
		}
		catch (Exception e) {
			LOG.error("Error decoding element names", e);
			return Response.status(Response.Status.BAD_REQUEST)
					.entity(new GroupingApiResponse("Error processing request.", "URLDecoder.decode threw: " + e.getMessage(), null))
					.build();
		}
		// We validate parameter elements
		if (!elements.matches("^[a-zA-Z_][a-zA-Z0-9_-]*(\\s+[a-zA-Z_][a-zA-Z0-9_-]*)*$")) {
			LOG.error("Validation error for elements parameter: " + elements);
			return Response.status(Response.Status.BAD_REQUEST)
					.entity(new GroupingApiResponse("Elements parameter validation failed.", "Elements parameter should be a space separated string of valid XML elements." , null))
					.build();
		}
		
		// Validate threshold if provided
		if (threshold != null && (threshold < 0.0 || threshold > 1.0)) {
			LOG.error("Validation error for threshold parameter: " + threshold);
			return Response.status(Response.Status.BAD_REQUEST)
					.entity(new GroupingApiResponse("Threshold parameter validation failed.", "Threshold must be between 0.0 and 1.0." , null))
					.build();
		}
		
		// We check if xmlContent is null or empty
		if (xmlContent == null) {
			LOG.warn("Received no xmlContent in request body");
			return Response.status(Response.Status.BAD_REQUEST)
					.entity(new GroupingApiResponse("Error processing request.", "xmlContent was not provided in request body.", null))
					.build();
		}
		if (xmlContent.trim().isEmpty()) {
			LOG.warn("xmlContent in request body was the empty string");
			return Response.status(Response.Status.BAD_REQUEST)
					.entity(new GroupingApiResponse("Error processing request.", "XML content is empty or invalid", null))
					.build();
		}
		return createSimilarityGroups(xmlContent, elements, threshold);
    }

	/**
	 * Internal method to process XML with optional element names.
	 * xmlContent and elementNames are assumed to be validated beforehand.
	 */
	private Response createSimilarityGroups(String xmlContent, String elementNames, Double threshold) {
		try {
			// Then start async xml processing and get a session ID
			NewCookie sessionCookie = similarityProcessingService.startAsyncProcessing(xmlContent, elementNames, threshold);

			// Return 202 Accepted with session ID in both cookie and body
			return Response.status(Response.Status.ACCEPTED)
					.cookie(sessionCookie)
					.entity(new GroupingApiResponse(
							"Processing started. Results will be available for this session.",
							sessionCookie.getValue()))
					.build();

		} catch (Exception e) {
			// Include the full stack trace and exception details in the log
			StringBuilder errorDetails = new StringBuilder();
			errorDetails.append("Exception in createSimilarityGroups(): ").append(e.getClass().getName()).append("\n");
			errorDetails.append("Message: ").append(e.getMessage()).append("\n");
			errorDetails.append("Stack trace:\n");
			for (StackTraceElement element : e.getStackTrace()) {
				errorDetails.append("  at ").append(element.toString()).append("\n");
			}
			if (e.getCause() != null) {
				errorDetails.append("Caused by: ").append(e.getCause().getClass().getName()).append("\n");
				errorDetails.append("Cause message: ").append(e.getCause().getMessage()).append("\n");
			}
			LOG.error(errorDetails.toString());
			return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
					.entity(new GroupingApiResponse(null, "Internal Server Error: " + e.getClass().getSimpleName() + ": " + e.getMessage(), null))
					.build();
		}
	}

	/**
	 * Endpoint to perform search on existing session data.
	 * @param sessionCookie Session cookie containing the session ID
	 * @param searchQuery The search query string
	 * @return Response with search results or status
	 */
	@GET
	@Path("/search")
	@Produces(MediaType.APPLICATION_JSON)
	public Response apiPerformSearch(
			@CookieParam(SimilarityProcessingService.SESSION_COOKIE_NAME) Cookie sessionCookie,
			@QueryParam("search") String searchQuery,
			@QueryParam("limit") @DefaultValue("10") int limit) {

		// Validate session and status using the helper method
		Response validationResponse = validateSessionAndStatus(sessionCookie, LOG, sessionService);
		if (validationResponse != null) {
			// If validation fails or status is not COMPLETED, return the response from the helper
			return validationResponse;
		}

		// Validation passed, session is valid and processing is COMPLETED.
		String sessionId = sessionCookie.getValue(); // We know cookie and value are not null/empty here

		// Validate search query parameter
		if (searchQuery == null || searchQuery.trim().isEmpty()) {
			LOG.warn("Search query parameter was missing or empty for session: " + sessionId);
			return Response.status(Response.Status.BAD_REQUEST)
					.entity(new SearchApiResponse(null, "Search query parameter 'search' is required.", sessionId, null))
					.build();
		}

		// Proceed with search logic
		try {
			return executeSearch(sessionId, searchQuery.trim(), limit);
		} catch (Exception e) {
			LOG.error("Error during search execution for session: " + sessionId, e);
			return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
					.entity(new SearchApiResponse(null, "Internal server error during search: " + e.getMessage(), sessionId, null))
					.build();
		}
	}

	/**
	 * Internal method to execute the search logic.
	 * @param sessionId The validated session ID
	 * @param searchQuery The search query
	 * @return Response with search results
	 */
	private Response executeSearch(String sessionId, String searchQuery, int limit) {
		// Use the SearchService to find similar sentences
		List<SearchService.SearchResult> searchResults = searchService.searchSimilarSentences(sessionId, searchQuery, limit);

		// Wrap the results in an SearchApiResponse
		return Response.ok()
				.entity(new SearchApiResponse("Search completed for query: " + searchQuery, null, sessionId, searchResults))
				.build();
	}

	/**
	 * Backward compatibility method
	 */
	private Response createSimilarityGroups(String xmlContent, String elementNames) {
		return createSimilarityGroups(xmlContent, elementNames, null);
	}

	/**
	 * Validates the session cookie and processing status.
	 * Returns a Response object if validation fails or processing is not complete,
	 * otherwise returns null.
	 * @param sessionCookie The session cookie
	 * @param LOG The logger instance
	 * @param sessionService The session service instance
	 * @return A Response object or null
	 */
	private Response validateSessionAndStatus(Cookie sessionCookie, Logger LOG, SessionService sessionService) {
		if (sessionCookie == null) {
			LOG.warn("Session cookie was null");
			return Response.status(Response.Status.BAD_REQUEST)
					.entity(new GroupingApiResponse(null, "Session cookie was never sent.", null))
					.build();
		}
		LOG.info("Session cookie received with value: " + sessionCookie.getValue());

		String sessionId = sessionCookie.getValue();

		if (sessionId.isEmpty()) {
			LOG.warn("Session cookie's value was an empty string");
			return Response.status(Response.Status.BAD_REQUEST)
					.entity(new GroupingApiResponse(null, "Session cookie's value was an empty string", null))
					.build();
		}

		// Validate session ID as UUID
		try {
			UUID.fromString(sessionId);
		} catch (IllegalArgumentException e) {
			LOG.warn("Session ID was not in UUID format: " + sessionId);
			return Response.status(Response.Status.BAD_REQUEST)
					.entity(new GroupingApiResponse(null, "Invalid session ID, not UUID format.", sessionId))
					.build();
		}

		// Get session
		org.acme.semsim.model.SessionData sessionData = sessionService.getSession(sessionId);

		// If no session found, return 404 Not Found
		if (sessionData == null) {
			LOG.info("No session found for ID: " + sessionId);
			return Response.status(Response.Status.NOT_FOUND)
					.entity(new GroupingApiResponse(null, "No session found for ID: " + sessionId, null))
					.build();
		}

		// Check processing status
		org.acme.semsim.model.SessionData.ProcessingStatus status = sessionData.getProcessingStatus();
		Response.Status responseStatus;
		String message = null;
		String error = null;

		switch (status) {
			case PROCESSING -> {
				LOG.info("Processing still in progress for session: " + sessionId);
				responseStatus = Response.Status.ACCEPTED;
				message = "Processing in progress. Please try again later.";
			}
			case ERROR -> {
				LOG.warn("An error occurred during processing for session: " + sessionId);
				responseStatus = Response.Status.INTERNAL_SERVER_ERROR;
				message = "An error occurred during processing.";
			}
			case NO_TEXT_EXTRACTED -> {
				responseStatus = Response.Status.BAD_REQUEST;
				message = "No embeddings were generated. This may be because no matching elements were found in your XML. " +
						"The default element is 'p'. If your XML uses different elements, please specify them using the 'elements' query parameter, " +
						"for example: /api/similarity?elements=paragraph";
				error = "No sentences found in XML. Revise elements query parameter or check data.";
			}
			case COMPLETED -> {
				// Processing is complete, validation passes for status check.
				return null; // Indicate success
			}
			default -> {
				// This case should technically not be reachable if COMPLETED is handled
				LOG.error("Unexpected processing status encountered: " + status + " for session: " + sessionId);
				responseStatus = Response.Status.INTERNAL_SERVER_ERROR;
				message = "Unexpected server state.";
				error = "Unexpected processing status: " + status;
			}
		}
		// If status was not COMPLETED, return the corresponding response
		return Response.status(responseStatus)
				.entity(new GroupingApiResponse(message, error, sessionId))
				.build();
	}

	/**
	 * Retrieve similarity results using the session ID from the session cookie.
	 * Supports polling - returns 202 Accepted if processing is still in progress.
	 * 
	 * @param sessionCookie Session cookie containing the session ID
	 * @return Response with similarity groups or processing status
	 */
	@GET
	@Path("/results")
	@Produces(MediaType.APPLICATION_JSON)
	public Response apiSimilarityResults(@CookieParam(SimilarityProcessingService.SESSION_COOKIE_NAME) Cookie sessionCookie) {
		try {
			// Validate session and status using the helper method
			Response validationResponse = validateSessionAndStatus(sessionCookie, LOG, sessionService);
			if (validationResponse != null) {
				// If validation fails or status is not COMPLETED, return the response from the helper
				return validationResponse;
			}

			// Validation passed, session is valid and processing is COMPLETED.
			String sessionId = sessionCookie.getValue(); // We know cookie and value are not null/empty here

			// Get session data again (since helper only checks status)
			org.acme.semsim.model.SessionData sessionData = sessionService.getSession(sessionId);

			// We already know sessionData is not null and status is COMPLETED from the helper method.
			// However, we log a warning if something unexpected happens (should not occur).
			if (sessionData == null || sessionData.getProcessingStatus() != org.acme.semsim.model.SessionData.ProcessingStatus.COMPLETED) {
				LOG.error("Unexpected state: Session data null or status not COMPLETED after validation for session: " + sessionId);
				return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
						.entity(new GroupingApiResponse(null, "Internal Server Error: Unexpected state after validation.", sessionId))
						.build();
			}

			// Continue with normal processing for COMPLETED status
			List<List<String>> similarityGroups = sessionData.getSimilaritySentenceGroups();
			String message;
			String error = null; // Error should be null in the OK case

			if (similarityGroups == null) {
				// This case might indicate an issue during the final storage phase, even if status is COMPLETED.
				LOG.warn("Processing status COMPLETED but similarity groups is null for session: " + sessionId);
				message = "Processing completed, but encountered an issue retrieving similarity groups.";
				// Optionally set a different response status like NO_CONTENT or keep OK with a specific message.
				// For now, keeping OK but providing an informative message.
                // Consider if returning an error status is more appropriate here.
			} else if (similarityGroups.isEmpty()) {
				LOG.info("Processing completed but no similarity groups were created for session: " + sessionId);
				message = "Processing completed but no similarity groups were created for this session.";
			} else {
				LOG.info("Returning " + similarityGroups.size() + " similarity groups for session: " + sessionId);
				message = "Processing completed. Similarity groups are available.";
			}

			// Return the OK response with status, message, and the similarityGroups
			return Response.ok()
					.entity(new GroupingApiResponse(message, error, sessionId, similarityGroups))
					.build();

		} catch (Exception e) {
			LOG.error("Error retrieving similarity results", e);
			return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
					.entity(new GroupingApiResponse(e.getMessage(), null))
					.build();
		}
	}
}