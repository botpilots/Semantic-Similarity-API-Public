package org.acme.semsim.dto;

import io.quarkus.runtime.annotations.RegisterForReflection;
import org.acme.semsim.service.SearchService;

import java.util.List;

/**
 * API response object for search results.
 */
@RegisterForReflection
public class SearchApiResponse {
    private String message;
    private String error;
    private String sessionId;
    private List<SearchService.SearchResult> searchResults; // Specific field for search results

    public SearchApiResponse() {
    }

    public SearchApiResponse(String message, String error, String sessionId, List<SearchService.SearchResult> searchResults) {
        this.message = message;
        this.error = error;
        this.sessionId = sessionId;
        this.searchResults = searchResults;
    }

    // Getters and Setters

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getError() {
        return error;
    }

    public void setError(String error) {
        this.error = error;
    }

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public List<SearchService.SearchResult> getSearchResults() {
        return searchResults;
    }

    public void setSearchResults(List<SearchService.SearchResult> searchResults) {
        this.searchResults = searchResults;
    }
} 