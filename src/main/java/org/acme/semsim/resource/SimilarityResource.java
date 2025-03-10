package org.acme.semsim.resource;

import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.Cookie;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.NewCookie;
import jakarta.ws.rs.core.Response;
import org.acme.semsim.dto.ApiResponse;
import org.acme.semsim.service.SimilarityProcessingService;
import org.acme.semsim.service.XmlProcessorService;
import org.jboss.logging.Logger;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.xpath.XPathExpressionException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.acme.semsim.service.XmlProcessorService.*;

/**
 * REST API endpoint for similarity-related operations.
 */
@Path("/api/similarity")
public class SimilarityResource {

	private static final Logger LOG = Logger.getLogger(SimilarityResource.class);

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
	public Response getSimilarityResults(@CookieParam(SimilarityProcessingService.SESSION_COOKIE_NAME) Cookie sessionCookie) {
		try {
			LOG.info("Received request for similarity results");

			if (sessionCookie == null) {
				LOG.warn("Session cookie missing");
				return Response.status(Response.Status.BAD_REQUEST)
						.entity(new ApiResponse(null,"Session cookie missing or invalid.", null))
						.build();
			}

			String sessionId = sessionCookie.getValue();

			// Get the processing status
			org.acme.semsim.model.SessionData.ProcessingStatus status = similarityProcessingService
					.getProcessingStatus(sessionId);

			// If no session found, return 404 Not Found
			if (status == null) {
				LOG.info("No session found for ID: " + sessionId);
				return Response.status(Response.Status.NOT_FOUND)
						.entity(new ApiResponse(null,"No results found for this session.",null))
						.build();
			}

			// If still processing, return 202 Accepted
			if (status == org.acme.semsim.model.SessionData.ProcessingStatus.PROCESSING) {
				LOG.info("Processing still in progress for session: " + sessionId);
				return Response.status(Response.Status.ACCEPTED)
						.entity(new ApiResponse("Processing in progress. Please try again later.", sessionId))
						.build();
			}

			// If error occurred during processing, return 500 Internal Server Error
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