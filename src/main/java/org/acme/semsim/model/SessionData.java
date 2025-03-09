package org.acme.semsim.model;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Represents a session's data, including timestamp and similarity groups.
 */
public class SessionData {
	private final String sessionId;
	private final Instant timestamp;
	private final List<List<String>> similaritySentenceGroups;
	private final List<Sentence> allSentences;
	private ProcessingStatus processingStatus;

	public enum ProcessingStatus {
		PROCESSING,
		COMPLETED,
		ERROR,
		NO_EMBEDDINGS_GENERATED
	}

	public SessionData(String sessionId) {
		this.sessionId = sessionId;
		this.timestamp = Instant.now();
		this.similaritySentenceGroups = new ArrayList<>();
		this.allSentences = new ArrayList<>();
		this.processingStatus = ProcessingStatus.PROCESSING;
	}

	public String getSessionId() {
		return sessionId;
	}

	public Instant getTimestamp() {
		return timestamp;
	}

	public List<List<String>> getSimilaritySentenceGroups() {
		return similaritySentenceGroups;
	}

	public void addSimilarityGroup(List<String> group) {
		similaritySentenceGroups.add(group);
	}

	public List<Sentence> getAllSentences() {
		return allSentences;
	}

	public void addSentence(Sentence sentence) {
		allSentences.add(sentence);
	}

	public boolean isExpired(long timeoutMinutes) {
		Instant expiryTime = timestamp.plusSeconds(timeoutMinutes * 60);
		return Instant.now().isAfter(expiryTime);
	}

	public ProcessingStatus getProcessingStatus() {
		return processingStatus;
	}

	public void setProcessingStatus(ProcessingStatus processingStatus) {
		this.processingStatus = processingStatus;
	}
}