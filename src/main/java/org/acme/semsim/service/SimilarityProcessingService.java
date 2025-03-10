package org.acme.semsim.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.core.NewCookie;
import org.acme.semsim.model.Sentence;
import org.acme.semsim.model.SessionData;
import org.jboss.logging.Logger;
import org.w3c.dom.Document;
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
 * Main service that orchestrates XML processing, sentence vectorization, and
 * similarity grouping.
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
	SimilarityService similarityService;

	@Inject
	SessionService sessionService;

	/**
	 * Process an XML document and find similarity groups using specific element
	 * names.
	 * This method starts asynchronous processing and returns a session ID.
	 * 
	 * @param xmlContent   XML document content to process
	 * @param elementNames Space-separated string of element names to extract text
	 *                     from (null for default)
	 * @return SessionCookie with sessionId to retrieve results later
	 */
	public NewCookie startAsyncProcessing(String xmlContent, String elementNames) throws XPathExpressionException, ParserConfigurationException, IOException, SAXException {
		LOG.info("Creating groups for XML document with element names: " + elementNames);

		// First create a working copy of the XML document with added attributes
		Document document = createWorkingCopy(xmlContent, elementNames);

		// Create a new session
		String sessionId = sessionService.createSession();
		LOG.info("Starting XML async processing for session: " + sessionId +
				(elementNames != null ? " with element names: " + elementNames : ""));

		// Start async processing
		CompletableFuture.runAsync(() -> processXmlContent(sessionId, document, elementNames), processingExecutor)
				// TODO: Rename to "unknown error in method processXmlContent()" and add more
				// specific handling inside method."
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
	 * Process XML content and store results in the session.
	 */
	private void processXmlContent(String sessionId, Document document) {
		processXmlContent(sessionId, document, null);
	}

	/**
	 * Process XML content with specific element names and store results in the
	 * session.
	 */
	private void processXmlContent(String sessionId, Document document, String elementNames) {
		try {
			LOG.debug("Processing XML for session: " + sessionId +
					(elementNames != null ? " with element names: " + elementNames : ""));

			// Get session data, session is already in PROCESSING state by default
			SessionData sessionData = sessionService.getSession(sessionId);
			if (sessionData == null) {
				LOG.warn("Session not found or expired: " + sessionId);
				return;
			}

			// 1. Extract text elements from XML
			List<String> textElements;
			try {
                textElements = xmlProcessorService.extractElementTextFromXml(document, elementNames);
				LOG.info("Extracted " + textElements.size() + " text elements from XML for session " + sessionId);
			} catch (Exception e) {
				LOG.error("Error extracting text from XML for session: " + sessionId, e);
				// Create an empty result to avoid null pointer exceptions
				sessionData.addSimilarityGroup(new ArrayList<>());
				sessionData.setProcessingStatus(SessionData.ProcessingStatus.ERROR);
				return;
			}

			// 2. Generate embeddings for sentences
			List<Sentence> sentencesWithEmbeddings = embeddingService.generateEmbeddings(textElements);
			LOG.info("Generated embeddings for " + sentencesWithEmbeddings.size() +
					" sentences for session " + sessionId);

			if (sentencesWithEmbeddings.isEmpty()) {
				LOG.warn("No embeddings generated for session: " + sessionId);
				sessionData.setProcessingStatus(SessionData.ProcessingStatus.NO_EMBEDDINGS_GENERATED);
				return;
			}

			// Store sentences with embeddings in session
			sentencesWithEmbeddings.forEach(sessionData::addSentence);

			// 3. Group similar sentences
			// TODO: Create a class for the similarity groups with metadata about the group
			// such as its similarity score, etc.
			List<List<String>> similarityGroups = similarityService.groupSimilarSentences(sentencesWithEmbeddings);
			LOG.info("Found " + similarityGroups.size() + " similarity groups for session " + sessionId);

			// Store similarity groups in session
			similarityGroups.forEach(sessionData::addSimilarityGroup);

			// Set processing status to completed
			sessionData.setProcessingStatus(SessionData.ProcessingStatus.COMPLETED);
			LOG.info("Completed processing for session: " + sessionId);
		} catch (Exception e) {
			LOG.error("Error processing XML for session: " + sessionId, e);
			// Get session data and set error status
			SessionData sessionData = sessionService.getSession(sessionId);
			if (sessionData != null) {
				sessionData.setProcessingStatus(SessionData.ProcessingStatus.ERROR);
			}
		}
	}

	/**
	 * Retrieve the similarity results for a session.
	 * 
	 * @param sessionId Session ID
	 * @return List of similar sentence groups, or null if session not found
	 */
	public List<List<String>> getSimilarityResults(String sessionId) {
		SessionData sessionData = sessionService.getSession(sessionId);
		if (sessionData == null) {
			LOG.debug("No results found for session: " + sessionId);
			return null;
		}

		return sessionData.getSimilaritySentenceGroups();
	}

	/**
	 * Get the processing status for a session.
	 * 
	 * @param sessionId Session ID
	 * @return Processing status or null if session not found
	 */
	public SessionData.ProcessingStatus getProcessingStatus(String sessionId) {
		SessionData sessionData = sessionService.getSession(sessionId);
		if (sessionData == null) {
			LOG.debug("No session found for ID: " + sessionId);
			return null;
		}

		return sessionData.getProcessingStatus();
	}

	/**
	 * Get both the processing status and results for a session.
	 * 
	 * @param sessionId Session ID
	 * @return SessionData or null if session not found
	 */
	public SessionData getSessionData(String sessionId) {
		return sessionService.getSession(sessionId);
	}
}