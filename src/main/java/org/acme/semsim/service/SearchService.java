package org.acme.semsim.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.acme.semsim.model.Sentence;
import org.acme.semsim.model.SessionData;
import org.jboss.logging.Logger;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;


/**
 * Service for searching similar sentences within a session based on a query.
 */
@ApplicationScoped
public class SearchService {

    private static final Logger LOG = Logger.getLogger(SearchService.class);
    private static final int DEFAULT_SEARCH_LIMIT = 10;

    @Inject
    EmbeddingService embeddingService;

    @Inject
    SessionService sessionService;

    /**
     * Represents a search result containing a sentence and its similarity score to the query.
     * Implements Comparable to allow sorting by similarity score in descending order.
     */
    public static class SearchResult implements Comparable<SearchResult> {
        private final Sentence sentence;
        private final double similarityScore;

        public SearchResult(Sentence sentence, double similarityScore) {
            this.sentence = sentence;
            this.similarityScore = similarityScore;
        }

        public Sentence getSentence() {
            return sentence;
        }

        public double getSimilarityScore() {
            return similarityScore;
        }

        @Override
        public int compareTo(SearchResult other) {
            // Sort descending (higher score first)
            return Double.compare(other.similarityScore, this.similarityScore);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            SearchResult that = (SearchResult) o;
            return Double.compare(that.similarityScore, similarityScore) == 0 && Objects.equals(sentence, that.sentence);
        }

        @Override
        public int hashCode() {
            return Objects.hash(sentence, similarityScore);
        }

        @Override
        public String toString() {
            return "SearchResult{" +
                   "sentence=" + sentence.getText().substring(0, Math.min(20, sentence.getText().length())) + "..." +
                   ", similarityScore=" + similarityScore +
                   '}';
        }
    }

    // Search methods will be added here

    /**
     * Searches for sentences in a session that are most similar to the given query.
     *
     * @param sessionId   The ID of the session to search within.
     * @param searchQuery The text query to compare against.
     * @param limit       The maximum number of results to return.
     * @return A list of Sentence objects, ordered by similarity score (descending), limited by the specified limit.
     * Returns an empty list if the session is not found, has no sentences, or if an error occurs.
     */
    public List<SearchResult> searchSimilarSentences(String sessionId, String searchQuery, int limit) {
        LOG.info("Performing search in session " + sessionId + " for query: '" + searchQuery + "' with limit " + limit);

        SessionData sessionData = sessionService.getSession(sessionId);
        if (sessionData == null) {
            LOG.warn("Session not found for ID: " + sessionId);
            return Collections.emptyList();
        }

        List<Sentence> sessionSentences = sessionData.getAllSentences();
        if (sessionSentences == null || sessionSentences.isEmpty()) {
            LOG.warn("No sentences found in session: " + sessionId);
            return Collections.emptyList();
        }

        // Generate embedding for the search query
        Sentence querySentence = embeddingService.generateEmbedding(searchQuery);
        double[] queryVector = querySentence.getVector();

        // Calculate similarity for each sentence in the session
        List<SearchResult> searchResults = new ArrayList<>();
        for (Sentence sessionSentence : sessionSentences) {
            double similarity = embeddingService.calculateCosineSimilarity(queryVector, sessionSentence.getVector());
            searchResults.add(new SearchResult(sessionSentence, similarity));
        }

        // Sort results by similarity score (descending)
        Collections.sort(searchResults);

        // Apply limit
        List<SearchResult> limitedResults = searchResults.stream()
                .limit(limit)
                .collect(Collectors.toList());

        LOG.info("Search completed for session " + sessionId + ". Found " + limitedResults.size() + " results (limited to " + limit + ").");
        if (!limitedResults.isEmpty()) {
             LOG.debug("Top result: " + limitedResults.get(0));
        }

        return limitedResults;
    }

    /**
     * Searches for sentences in a session that are most similar to the given query, using the default limit.
     *
     * @param sessionId   The ID of the session to search within.
     * @param searchQuery The text query to compare against.
     * @return A list of Sentence objects, ordered by similarity score (descending), limited to the default limit (10).
     */
    public List<SearchResult> searchSimilarSentences(String sessionId, String searchQuery) {
        return searchSimilarSentences(sessionId, searchQuery, DEFAULT_SEARCH_LIMIT);
    }
} 