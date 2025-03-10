package org.acme.semsim.resource;

import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.Cookie;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.NewCookie;
import jakarta.ws.rs.core.Response;
import org.acme.semsim.dto.ApiResponse;
import org.acme.semsim.model.SessionData;
import org.acme.semsim.service.SimilarityProcessingService;
import org.jboss.logging.Logger;

import io.quarkus.logging.Log;

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
	 * @param elements      A space-separated string of element names to extract text
	 *                   from (e.g., "p li div")
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
				// Validate that each element name starts with a letter or '_', and contains
				// only
				// letters, digits, '-', or '_'. Multiple elements can be separated by spaces.
				if (!elements.matches("^[a-zA-Z_][a-zA-Z0-9_-]*(\\s+[a-zA-Z_][a-zA-Z0-9_-]*)*$")) {
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
			// NOTE: This is async, refer to GET /api/similarity/results to get processing
			// status etc.
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

			// If no embeddings were generated, return a bad request
			if (status == org.acme.semsim.model.SessionData.ProcessingStatus.NO_EMBEDDINGS_GENERATED) {
				return Response.status(Response.Status.BAD_REQUEST)
						.entity(new ApiResponse(
								"No embeddings were generated. This may be because no matching elements were found in your XML. "
										+
										"The default element is 'p'. If your XML uses different elements, please specify them using the 'elements' query parameter, "
										+
										"for example: /api/similarity?elements=paragraph",
								"No sentences found in XML. Revise elements query parameter or check data.",
								null))
						.build();
			}

			// Processing is complete, get the results
			List<List<String>> similarityGroups = similarityProcessingService.getSimilarityResults(sessionId);

			if (similarityGroups == null) {
				LOG.info("Session was not found: " + sessionId);
				return Response.status(Response.Status.NOT_FOUND)
						.entity(new ApiResponse("Session was not found.", null))
						.build();
			}

			if (similarityGroups.isEmpty()) {
				LOG.info("Processing completed but no similarity groups were created for this session: " + sessionId);
				return Response.ok()
						.entity(new ApiResponse(
								"Processing completed but no similarity groups were created for this session.",
								sessionId))
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