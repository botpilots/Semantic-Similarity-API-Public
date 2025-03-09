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
import java.io.UnsupportedEncodingException;

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
	 * Submit an XML document for processing with specific element names.
	 * 
	 * @param xmlContent The XML content to process
	 * @param xpath      A space-separated string of element names to extract text
	 *                   from
	 *                   (default: "p")
	 * @return Response with a session cookie
	 */
	@POST
	@Consumes(MediaType.APPLICATION_XML)
	@Produces(MediaType.APPLICATION_JSON)
	public Response processXmlWithXPath(String xmlContent, @QueryParam("elements") @DefaultValue("p") String elements) {
		try {
			// Always URL decode the element names as we assume it's encoded
			if (elements != null) {
				elements = java.net.URLDecoder.decode(elements, "UTF-8");
				LOG.debug("Decoded element names: " + elements);
				// Validate that the element name starts with a letter or '_', and contains only
				// letters, digits, '-', '_', or '.'.
				if (!elements.matches("^[a-zA-Z_][a-zA-Z0-9_-]*$")) {
					throw new IllegalArgumentException(elements);
				}
			}
			return processXml(xmlContent, elements);
		} catch (UnsupportedEncodingException e) {
			LOG.error("Error decoding element names: " + elements, e);
			return Response.status(Response.Status.BAD_REQUEST)
					.entity(new ApiResponse("Error processing request.",
							"Error decoding element names: " + e.getMessage(),
							null))
					.build();
		} catch (IllegalArgumentException e) {
			LOG.error("Validation error for elements parameter: " + elements, e);
			return Response.status(Response.Status.BAD_REQUEST)
					.entity(new ApiResponse("Error processing request.",
							"Invalid elements names: " + elements + " " + e.getMessage(),
							null))
					.build();
		}
	}

	/**
	 * Internal method to process XML with optional element names.
	 */
	private Response processXml(String xmlContent, String elementNames) {
		try {
			LOG.info("Received XML document for processing"
					+ (elementNames != null ? " with element names: " + elementNames : ""));

			if (xmlContent == null || xmlContent.trim().isEmpty()) {
				LOG.warn("Received empty XML content");
				return Response.status(Response.Status.BAD_REQUEST)
						.entity(new ApiResponse("Error processing request.", "XML content is empty or invalid", null))
						.build();
			}

			// Validate XML before processing
			try {
				// Create a DocumentBuilderFactory
				javax.xml.parsers.DocumentBuilderFactory factory = javax.xml.parsers.DocumentBuilderFactory
						.newInstance();
				// Create a DocumentBuilder
				javax.xml.parsers.DocumentBuilder builder = factory.newDocumentBuilder();
				// Parse the XML
				builder.parse(new java.io.ByteArrayInputStream(xmlContent.getBytes()));
			} catch (Exception e) {
				LOG.warn("Invalid XML content: " + e.getMessage());
				return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
						.entity(new ApiResponse("Error processing request.", "Invalid XML: " + e.getMessage(), null))
						.build();
			}

			// Start processing and get a session ID
			String sessionId = similarityProcessingService.startProcessing(xmlContent, elementNames);

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
	 * Supports polling - returns 202 Accepted if processing is still in progress.
	 * 
	 * @param sessionCookie Session cookie containing the session ID
	 * @return Response with similarity groups or processing status
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

			// Get the processing status
			org.acme.semsim.model.SessionData.ProcessingStatus status = similarityProcessingService
					.getProcessingStatus(sessionId);

			if (status == null) {
				LOG.info("No session found for ID: " + sessionId);
				return Response.status(Response.Status.NOT_FOUND)
						.entity(new ApiResponse("No results found for this session.", null))
						.build();
			}

			// If still processing, return 202 Accepted
			if (status == org.acme.semsim.model.SessionData.ProcessingStatus.PROCESSING) {
				LOG.info("Processing still in progress for session: " + sessionId);
				return Response.status(Response.Status.ACCEPTED)
						.entity(new ApiResponse("Processing in progress. Please try again later.", sessionId))
						.build();
			}

			// If error occurred during processing
			if (status == org.acme.semsim.model.SessionData.ProcessingStatus.ERROR) {
				LOG.warn("Processing error for session: " + sessionId);
				return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
						.entity(new ApiResponse("An error occurred during processing.", sessionId))
						.build();
			}

			// Processing is complete, get the results
			List<List<String>> similarityGroups = similarityProcessingService.getSimilarityResults(sessionId);

			if (similarityGroups == null || similarityGroups.isEmpty()) {
				LOG.info("No results found for session: " + sessionId);
				return Response.status(Response.Status.NOT_FOUND)
						.entity(new ApiResponse("No similarity groups found for this session.", null))
						.build();
			}

			LOG.info("Returning " + similarityGroups.size() + " similarity groups for session: " + sessionId);
			return Response.ok(similarityGroups).build();

		} catch (Exception e) {
			LOG.error("Error retrieving similarity results", e);
			return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
					.entity(new ApiResponse("Internal server error.", e.getMessage(), null))
					.build();
		}
	}
}