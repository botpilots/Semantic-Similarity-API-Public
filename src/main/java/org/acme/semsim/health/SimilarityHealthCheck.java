package org.acme.semsim.health;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.acme.semsim.service.EmbeddingService;
import org.eclipse.microprofile.health.HealthCheck;
import org.eclipse.microprofile.health.HealthCheckResponse;
import org.eclipse.microprofile.health.Readiness;

/**
 * Health check for the similarity service.
 */
@Readiness
@ApplicationScoped
public class SimilarityHealthCheck implements HealthCheck {

	@Inject
	EmbeddingService embeddingService;

	@Override
	public HealthCheckResponse call() {
		try {
			// Test the embedding service with a simple text
			String testText = "This is a test sentence for health check.";
			embeddingService.generateEmbedding(testText);

			return HealthCheckResponse.up("Similarity Service is ready");
		} catch (Exception e) {
			return HealthCheckResponse.down("Similarity Service is not ready: " + e.getMessage());
		}
	}
}