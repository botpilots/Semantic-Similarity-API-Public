package org.acme.semsim.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.acme.semsim.model.Sentence;
import org.acme.semsim.model.SessionData;
import org.jboss.logging.Logger;

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
	public String startProcessing(String xmlContent) {
		return startProcessing(xmlContent, null);
	}

	/**
	 * Process an XML document and find similarity groups using a specific XPath
	 * expression.
	 * This method starts asynchronous processing and returns a session ID.
	 * 
	 * @param xmlContent      XML document content to process
	 * @param xpathExpression XPath expression to select elements for text
	 *                        extraction (null for default)
	 * @return Session ID to retrieve results later
	 */
	public String startProcessing(String xmlContent, String xpathExpression) {
		// Create a new session
		String sessionId = sessionService.createSession();
		LOG.info("Starting XML processing for session: " + sessionId +
				(xpathExpression != null ? " with XPath: " + xpathExpression : ""));

		// Start async processing
		CompletableFuture.runAsync(() -> processXmlContent(sessionId, xmlContent, xpathExpression), processingExecutor)
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
	 * Process XML content with a specific XPath expression and store results in the
	 * session.
	 */
	private void processXmlContent(String sessionId, String xmlContent, String xpathExpression) {
		try {
			LOG.debug("Processing XML for session: " + sessionId +
					(xpathExpression != null ? " with XPath: " + xpathExpression : ""));

			// Get session data
			SessionData sessionData = sessionService.getSession(sessionId);
			if (sessionData == null) {
				LOG.warn("Session not found or expired: " + sessionId);
				return;
			}

			// 1. Extract sentences from XML
			List<String> sentenceTexts;
			if (xpathExpression != null) {
				sentenceTexts = xmlProcessorService.extractSentencesFromXml(xmlContent, xpathExpression);
			} else {
				sentenceTexts = xmlProcessorService.extractSentencesFromXml(xmlContent);
			}
			LOG.info("Extracted " + sentenceTexts.size() + " sentences from XML for session " + sessionId);

			// 2. Generate embeddings for sentences
			List<Sentence> sentencesWithEmbeddings = embeddingService.generateEmbeddings(sentenceTexts);
			LOG.info("Generated embeddings for " + sentencesWithEmbeddings.size() +
					" sentences for session " + sessionId);

			// Store sentences with embeddings in session
			sentencesWithEmbeddings.forEach(sessionData::addSentence);

			// 3. Group similar sentences
			List<List<String>> similarityGroups = similarityService.groupSimilarSentences(sentencesWithEmbeddings);
			LOG.info("Found " + similarityGroups.size() + " similarity groups for session " + sessionId);

			// Store similarity groups in session
			similarityGroups.forEach(sessionData::addSimilarityGroup);

			LOG.info("Completed processing for session: " + sessionId);
		} catch (Exception e) {
			LOG.error("Error processing XML for session: " + sessionId, e);
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
}