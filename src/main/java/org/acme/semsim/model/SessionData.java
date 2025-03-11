package org.acme.semsim.model;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Represents a session's data, including timestamp and similarity groups.
 */
// TODO: Complement this class with the following:
// - field GroupsFound: Number of groups found
// - field candidatesToRemove: The number of sentences minus the number of groups
//   (i.e. the number of sentences that could potentially be removed as unique ones from
//   the original document), as a number.
//   - Make it store TextDuplicatesGroup objects instead of List<String> objects.
//   - Make it store TextDuplicates instead of Sentences objects.
// - similarity threshold used at processing.
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
		NO_TEXT_EXTRACTED
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