package org.acme.semsim.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.acme.semsim.model.Sentence;
import org.acme.semsim.model.SessionData;
import org.jboss.logging.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * Main service that orchestrates XML processing, sentence vectorization, and
 * similarity grouping.
 */
@ApplicationScoped
public class SimilarityProcessingService {

	private static final Logger LOG = Logger.getLogger(SimilarityProcessingService.class);

	private final Executor processingExecutor = Executors.newFixedThreadPool(2);

	@Inject
	XmlProcessorService xmlProcessorService;

	@Inject
	EmbeddingService embeddingService;

	@Inject
	SimilarityService similarityService;

	@Inject
	SessionService sessionService;

	/**
	 * Process an XML document and find similarity groups.
	 * This method starts asynchronous processing and returns a session ID.
	 * 
	 * @param xmlContent XML document content to process
	 * @return Session ID to retrieve results later
	 */
	public String startAsyncProcessing(String xmlContent) {
		return startAsyncProcessing(xmlContent, null);
	}

	/**
	 * Process an XML document and find similarity groups using specific element
	 * names.
	 * This method starts asynchronous processing and returns a session ID.
	 * 
	 * @param xmlContent   XML document content to process
	 * @param elementNames Space-separated string of element names to extract text
	 *                     from (null for default)
	 * @return Session ID to retrieve results later
	 */
	public String startAsyncProcessing(String xmlContent, String elementNames) {
		// Create a new session
		String sessionId = sessionService.createSession();
		LOG.info("Starting XML async processing for session: " + sessionId +
				(elementNames != null ? " with element names: " + elementNames : ""));

		// Start async processing
		CompletableFuture.runAsync(() -> processXmlContent(sessionId, xmlContent, elementNames), processingExecutor)
				.exceptionally(ex -> {
					LOG.error("Error processing XML for session " + sessionId, ex);
					return null;
				});

		return sessionId;
	}

	/**
	 * Process XML content and store results in the session.
	 */
	private void processXmlContent(String sessionId, String xmlContent) {
		processXmlContent(sessionId, xmlContent, null);
	}

	/**
	 * Process XML content with specific element names and store results in the
	 * session.
	 */
	private void processXmlContent(String sessionId, String xmlContent, String elementNames) {
		try {
			LOG.debug("Processing XML for session: " + sessionId +
					(elementNames != null ? " with element names: " + elementNames : ""));

			// Get session data
			SessionData sessionData = sessionService.getSession(sessionId);
			if (sessionData == null) {
				LOG.warn("Session not found or expired: " + sessionId);
				return;
			}

			// Session is already in PROCESSING state by default

			// Check for empty XML content
			if (xmlContent == null || xmlContent.trim().isEmpty()) {
				LOG.warn("Empty XML content for session: " + sessionId);
				// Still create an empty result to avoid null pointer exceptions
				sessionData.addSimilarityGroup(new ArrayList<>());
				sessionData.setProcessingStatus(SessionData.ProcessingStatus.COMPLETED);
				return;
			}

			// 1. Extract text elements from XML
			List<String> textElements;
			try {
				textElements = xmlProcessorService.extractElementTextFromXml(xmlContent, elementNames);
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