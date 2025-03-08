package org.acme.semsim.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.acme.semsim.model.Sentence;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Service for grouping similar sentences based on their vector embeddings.
 */
@ApplicationScoped
public class SimilarityService {

	private static final Logger LOG = Logger.getLogger(SimilarityService.class);

	@Inject
	EmbeddingService embeddingService;

	@ConfigProperty(name = "semsim.similarity.threshold", defaultValue = "0.75")
	double similarityThreshold;

	/**
	 * Group similar sentences based on cosine similarity.
	 * 
	 * @param sentences List of sentences with their vector embeddings
	 * @return List of lists of similar sentences (groups)
	 */
	public List<List<String>> groupSimilarSentences(List<Sentence> sentences) {
		LOG.debug("Grouping " + sentences.size() + " sentences with similarity threshold " + similarityThreshold);

		List<List<String>> groups = new ArrayList<>();
		Set<Integer> processedIndices = new HashSet<>();

		for (int i = 0; i < sentences.size(); i++) {
			if (processedIndices.contains(i)) {
				continue; // Skip already processed sentences
			}

			Sentence currentSentence = sentences.get(i);
			List<String> similarSentences = new ArrayList<>();
			similarSentences.add(currentSentence.getText());
			processedIndices.add(i);

			// Find similar sentences
			for (int j = 0; j < sentences.size(); j++) {
				if (i == j || processedIndices.contains(j)) {
					continue; // Skip self or already processed sentences
				}

				Sentence candidateSentence = sentences.get(j);
				double similarity = embeddingService.calculateCosineSimilarity(
						currentSentence.getVector(), candidateSentence.getVector());

				if (similarity >= similarityThreshold) {
					similarSentences.add(candidateSentence.getText());
					processedIndices.add(j);
					LOG.debug("Found similar sentences with similarity " + similarity + ": " +
							truncateText(currentSentence.getText()) + " and " +
							truncateText(candidateSentence.getText()));
				}
			}

			// Add all groups, even those with only one sentence
			groups.add(similarSentences);
		}

		LOG.info("Created " + groups.size() + " similarity groups");
		return groups;
	}

	/**
	 * Truncate text for logging purposes.
	 */
	private String truncateText(String text) {
		int maxLength = 20;
		if (text.length() <= maxLength) {
			return text;
		}
		return text.substring(0, maxLength) + "...";
	}
}