package org.acme.semsim.resource;

import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.Cookie;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.NewCookie;
import jakarta.ws.rs.core.Response;
import org.acme.semsim.dto.ApiResponse;
import org.acme.semsim.service.SimilarityProcessingService;
import org.jboss.logging.Logger;

import java.util.List;

/**
 * REST API endpoint for similarity-related operations.
 */
@Path("/api/similarity")
public class SimilarityResource {

	private static final Logger LOG = Logger.getLogger(SimilarityResource.class);
	private static final String SESSION_COOKIE_NAME = "session_id";

	@Inject
	SimilarityProcessingService similarityProcessingService;

	/**
	 * Submit an XML document for processing.
	 * 
	 * @param xmlContent The XML content to process
	 * @return Response with a session cookie
	 */
	@POST
	@Consumes(MediaType.APPLICATION_XML)
	@Produces(MediaType.APPLICATION_JSON)
	public Response processXml(String xmlContent) {
		try {
			LOG.info("Received XML document for processing");

			if (xmlContent == null || xmlContent.trim().isEmpty()) {
				LOG.warn("Received empty XML content");
				return Response.status(Response.Status.BAD_REQUEST)
						.entity(new ApiResponse("Error processing request.", "XML content is empty or invalid", null))
						.build();
			}

			// Start processing and get a session ID
			String sessionId = similarityProcessingService.startProcessing(xmlContent);

			// Create session cookie
			NewCookie sessionCookie = new NewCookie.Builder(SESSION_COOKIE_NAME)
					.value(sessionId)
					.path("/")
					.httpOnly(true)
					.build();

			// Return 202 Accepted with session ID in both cookie and body
			return Response.status(Response.Status.ACCEPTED)
					.cookie(sessionCookie)
					.entity(new ApiResponse(
							"Processing started. Results will be available for this session.",
							sessionId))
					.build();

		} catch (Exception e) {
			LOG.error("Error processing XML", e);
			return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
					.entity(new ApiResponse("Internal server error.", e.getMessage(), null))
					.build();
		}
	}

	/**
	 * Retrieve similarity results using the session ID from the session cookie.
	 * 
	 * @param sessionCookie Session cookie containing the session ID
	 * @return Response with similarity groups or error
	 */
	@GET
	@Path("/results")
	@Produces(MediaType.APPLICATION_JSON)
	public Response getSimilarityResults(@CookieParam(SESSION_COOKIE_NAME) Cookie sessionCookie) {
		try {
			LOG.info("Received request for similarity results");

			if (sessionCookie == null) {
				LOG.warn("Session cookie missing");
				return Response.status(Response.Status.BAD_REQUEST)
						.entity(new ApiResponse("Session cookie missing or invalid.", null))
						.build();
			}

			String sessionId = sessionCookie.getValue();
			List<List<String>> similarityGroups = similarityProcessingService.getSimilarityResults(sessionId);

			if (similarityGroups == null) {
				LOG.info("No results found for session: " + sessionId);
				return Response.status(Response.Status.NOT_FOUND)
						.entity(new ApiResponse("Results not found for this session or session expired.", null))
						.build();
			}

			return Response.ok(similarityGroups).build();

		} catch (Exception e) {
			LOG.error("Error retrieving similarity results", e);
			return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
					.entity(new ApiResponse("Internal server error.", e.getMessage(), null))
					.build();
		}
	}
}