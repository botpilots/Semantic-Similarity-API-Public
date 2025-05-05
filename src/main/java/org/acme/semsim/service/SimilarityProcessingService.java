package org.acme.semsim.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.core.NewCookie;
import org.acme.semsim.model.Sentence;
import org.acme.semsim.model.SessionData;
import org.jboss.logging.Logger;
import org.w3c.dom.Document;
import org.w3c.dom.Text;
import org.xml.sax.SAXException;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.xpath.XPathExpressionException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import static org.acme.semsim.service.XmlProcessorService.createWorkingCopy;

/**
 * This class holds the upper business logic for the /api/similarity endpoint.
 * A main service that orchestrating Text extraction, embeddings generation, and similarity grouping.
 */
@ApplicationScoped
public class SimilarityProcessingService {

	private static final Logger LOG = Logger.getLogger(SimilarityProcessingService.class);

	private final Executor processingExecutor = Executors.newFixedThreadPool(2);

	public static final String SESSION_COOKIE_NAME = "session_id";

	@Inject
	XmlProcessorService xmlProcessorService;

	@Inject
	EmbeddingService embeddingService;

	@Inject
	GroupingService groupingService;

	@Inject
	SessionService sessionService;

	/**
	 *
	 * Process an XML document and find similarity groups using specific element
	 * names.
	 * This method starts asynchronous processing and returns a session ID.
	 * 
	 * @param xmlContent   XML document content to process
	 * @param elementNames Space-separated string of element names to extract text
	 *                     from (null for default)
	 * @param threshold    Optional similarity threshold (null for default)
	 * @return SessionCookie with sessionId to retrieve results later
	 */
	// TODO: Overload method that accepts a session cookie, so several XML documents can be processed in same session.
	public NewCookie startAsyncProcessing(String xmlContent, String elementNames, Double threshold) throws XPathExpressionException, ParserConfigurationException, IOException, SAXException {
		LOG.info("Creating groups for XML document with element names: " + elementNames + 
				(threshold != null ? " and threshold: " + threshold : ""));

		// First create a working copy of the XML document with added attributes
		Document document = createWorkingCopy(xmlContent, elementNames);

		// Create a new session
		String sessionId = sessionService.createSession();
		LOG.info("Starting XML async processing for session: " + sessionId +
				(elementNames != null ? " with element names: " + elementNames : "") +
				(threshold != null ? " and threshold: " + threshold : ""));

		// Start async processing
		CompletableFuture.runAsync(() -> processXmlContent(sessionId, document, elementNames, threshold), processingExecutor)
				// TODO: Rename to "unknown error in method processXmlContent()" and add more
				// specific handling inside method.
				.exceptionally(ex -> {
					LOG.error("Error processing XML for session " + sessionId, ex);
					return null;
				});

		// Create session cookie from session ID
		return new NewCookie.Builder(SESSION_COOKIE_NAME)
                .value(sessionId)
                .path("/")
                .httpOnly(true)
                .build();
	}

	/**
	 * Backward compatibility method
	 */
	public NewCookie startAsyncProcessing(String xmlContent, String elementNames) throws XPathExpressionException, ParserConfigurationException, IOException, SAXException {
		return startAsyncProcessing(xmlContent, elementNames, null);
	}

	/**
	 * Process XML content with specific element names and store results in the
	 * session.
	 *
	 */
	private void processXmlContent(String sessionId, Document document, String elementNames, Double threshold) {

		// Get session data, session is already in PROCESSING state by default
		SessionData sessionData = sessionService.getSession(sessionId);
		if (sessionData == null) {
			LOG.warn("Session not found or expired: " + sessionId);
			return;
		}
		try {
			// Initialize similarityGroup, allowed to be still empty after processing
			// TODO: Should be a custom object that can hold several Text objects.
			List<List<String>> similarityGroups;

			LOG.debug("Processing XML for session: " + sessionId +
					(elementNames != null ? " with element names: " + elementNames : "") +
					(threshold != null ? " and threshold: " + threshold : ""));


			// 1. Extract text elements from XML
			// TODO: extractTextElements should return a list of Text inheriting from Text, that has the added fields for 1. amount of duplicates found for that text in Document and 2. the embedding vector.
			List<String> textElements = xmlProcessorService.extractTextElements(document, elementNames);
			LOG.info("Extracted " + textElements.size() + " text elements from XML for session " + sessionId);
			if (textElements.isEmpty()) {
				LOG.warn("No textElements extracted for session: " + sessionId);
				sessionData.setProcessingStatus(SessionData.ProcessingStatus.NO_TEXT_EXTRACTED);
				return;
			}

			// 2. Generate and store embeddings in session
			// TODO: Modify Sentence to custom Text object, embeddings should be stored in that object.
			List<Sentence> textContentWithEmbeddings = embeddingService.generateEmbeddings(textElements);
			LOG.info("Generated embeddings for " + textContentWithEmbeddings.size() +
					" sentences for session " + sessionId);
			textContentWithEmbeddings.forEach(sessionData::addSentence);

			// 3. Group and store similarity groups in session
			// TODO: Create a class for the similarity groups with metadata about the group such as its similarity score, etc.
			if (threshold != null) {
				similarityGroups = groupingService.group(textContentWithEmbeddings, threshold);
			} else {
				similarityGroups = groupingService.group(textContentWithEmbeddings);
			}
			LOG.info("Found " + similarityGroups.size() + " similarity groups for session " + sessionId);
			similarityGroups.forEach(sessionData::addSimilarityGroup);

			// Set processing status to completed
			sessionData.setProcessingStatus(SessionData.ProcessingStatus.COMPLETED);
			LOG.info("Completed processing for session: " + sessionId);

		} catch (Exception e) {
			LOG.error("processXmlContent() failed: " + e.getMessage());
			sessionData.setProcessingStatus(SessionData.ProcessingStatus.ERROR);
		}
	}

	/**
	 * Backward compatibility method
	 */
	private void processXmlContent(String sessionId, Document document, String elementNames) {
		processXmlContent(sessionId, document, elementNames, null);
	}

}