package org.acme.semsim.resource;

import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.Cookie;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.NewCookie;
import jakarta.ws.rs.core.Response;
import org.acme.semsim.dto.ApiResponse;
import org.acme.semsim.service.SessionService;
import org.acme.semsim.service.SimilarityProcessingService;
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

	/**
	 * Submit an XML document for processing with specific element names.
	 *
	 * @param xmlContent The XML content to process
	 * @param elements      A space-separated string of element names to extract text
	 *                   from (e.g., "p li div")
	 *                   (default: "p")
	 * @return Response with a session cookie
	 */
	@POST
	@Consumes(MediaType.APPLICATION_XML)
	@Produces(MediaType.APPLICATION_JSON)
	public Response apiSimilarity(String xmlContent, @QueryParam("elements") @DefaultValue("p") String elements) {
		// We assume parameter elements is encoded, so we decode it
		try {
			elements = java.net.URLDecoder.decode(elements, StandardCharsets.UTF_8);
		}
		catch (Exception e) {
			LOG.error("Error decoding element names", e);
			return Response.status(Response.Status.BAD_REQUEST)
					.entity(new ApiResponse("Error processing request.", "URLDecoder.decode threw: " + e.getMessage(), null))
					.build();
		}
		// We validate parameter elements
		if (!elements.matches("^[a-zA-Z_][a-zA-Z0-9_-]*(\\s+[a-zA-Z_][a-zA-Z0-9_-]*)*$")) {
			LOG.error("Validation error for elements parameter: " + elements);
			return Response.status(Response.Status.BAD_REQUEST)
					.entity(new ApiResponse("Elements parameter validation failed.", "Elements parameter should be a space separated string of valid XML elements." , null))
					.build();
		}
		// We check if xmlContent is null or empty
		if (xmlContent == null) {
			LOG.warn("Received no xmlContent in request body");
			return Response.status(Response.Status.BAD_REQUEST)
					.entity(new ApiResponse("Error processing request.", "xmlContent was not provided in request body.", null))
					.build();
		}
		if (xmlContent.trim().isEmpty()) {
			LOG.warn("xmlContent in request body was the empty string");
			return Response.status(Response.Status.BAD_REQUEST)
					// TODO: Add a more specific error message, what is invalid about the XML?
					.entity(new ApiResponse("Error processing request.", "XML content is empty or invalid", null))
					.build();
		}
		return createSimilarityGroups(xmlContent, elements);
    }

	/**
	 * Internal method to process XML with optional element names.
	 * xmlContent and elementNames are assumed to be validated beforehand.
	 */
	private Response createSimilarityGroups(String xmlContent, String elementNames) {
		try {
			// Then start async xml processing and get a session ID
			NewCookie sessionCookie = similarityProcessingService.startAsyncProcessing(xmlContent, elementNames);

			// Return 202 Accepted with session ID in both cookie and body
			return Response.status(Response.Status.ACCEPTED)
					.cookie(sessionCookie)
					.entity(new ApiResponse(
							"Processing started. Results will be available for this session.",
							sessionCookie.getValue()))
					.build();

		} catch (Exception e) {
			LOG.error("error in createSimilarityGroups(): " + e.getMessage());
			return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
					.entity(new ApiResponse(null, "Internal Server Error: " + e.getClass().getSimpleName() + ": " + e.getMessage(), null))
					.build();
		}
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
			if (sessionCookie == null) {
				LOG.warn("/results was requested but Session cookie was null");
				return Response.status(Response.Status.BAD_REQUEST)
						.entity(new ApiResponse(null,"Session cookie was never sent.", null))
						.build();
			}
			LOG.info("/results was requested with sessionid: " + sessionCookie.getValue());

			String sessionId = sessionCookie.getValue();

			if (sessionId.isEmpty()) {
				LOG.warn("Session cookie's value was an empty string");
				return Response.status(Response.Status.BAD_REQUEST)
						.entity(new ApiResponse(null,"Session cookie's value was an empty string", null))
						.build();
			}

			// Validate session ID as UUID
			try {
				UUID uuid = UUID.fromString(sessionId);
				// Valid UUID format
				// Process the session
			} catch (IllegalArgumentException e) {
				LOG.warn("Session ID was not in UUID format: " + sessionId);
				return Response.status(Response.Status.BAD_REQUEST)
						.entity(new ApiResponse(null,"Invalid session ID, not UUID format.", sessionId))
						.build();
			}

			// Get session
			org.acme.semsim.model.SessionData sessionData = sessionService.getSession(sessionId);

			// If no session found, return 404 Not Found
			if (sessionData == null) {
				LOG.info("No session found for ID: " + sessionId);
				return Response.status(Response.Status.NOT_FOUND)
						.entity(new ApiResponse(null,"No session found for ID: " + sessionId, null))
						.build();
			}

			// Check processing status
			org.acme.semsim.model.SessionData.ProcessingStatus status = sessionData.getProcessingStatus();

			// Handle cases based on processing status
			Response.Status responseStatus;
			String message = null;
			String error = null;

			switch (status) {
			    case PROCESSING -> {
			        LOG.info("A results request was made but processing was still in progress for session: " + sessionId);
			        responseStatus = Response.Status.ACCEPTED;
			        message = "Processing in progress. Please try again later.";
			    }
			    case ERROR -> {
			        LOG.warn("A results request was made but an error had occurred during processing for session: " + sessionId);
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
				// Assume COMPLETED will always be the default case
				default -> {
					// Log a warning if the status is not COMPLETED
					if (status != org.acme.semsim.model.SessionData.ProcessingStatus.COMPLETED) {
						LOG.warn("Unexpected processing status! Should have been COMPLETED but was: " + status);
					}
					responseStatus = Response.Status.OK;
				}
			}

			// Return if not completed
			if (responseStatus != Response.Status.OK) {
				return Response.status(responseStatus)
						.entity(new ApiResponse(message, error, sessionId))
						.build();
			}

			// Continue with normal processing
			List<List<String>> similarityGroups = sessionData.getSimilaritySentenceGroups();

			if (similarityGroups == null) {
				LOG.warn("Unexpected error! Similarity groups was null for " + sessionId);;
			}


			if (message != null) {
				LOG.warn("The message was not null, but the status was OK. This should not happen.");
				LOG.warn("Message (that should have been null): " + message);
				error = "The message was not null, but the status was OK. This should not happen.";
				return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
						.entity(new ApiResponse(message, error, sessionId))
						.build();
			} else if (similarityGroups == null || similarityGroups.isEmpty()) {
				LOG.info("Processing completed but no similarity groups were created for this session: " + sessionId);
				message = "Processing completed but no similarity groups were created for this session.";
			} else {
				LOG.info("Returning " + similarityGroups.size() + " similarity groups for session: " + sessionId);
				message = "Processing completed. Similarity groups are available.";
			}

			// Return the ok response with status and message and the similarityGroups
			return Response.ok()
					.entity(new ApiResponse(message, error, sessionId, similarityGroups))
					.build();

		} catch (Exception e) {
			LOG.error("Error retrieving similarity results", e);
			return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
					.entity(new ApiResponse(e.getMessage(), null))
					.build();
		}
	}
}