package org.acme.semsim.service;

import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.acme.semsim.model.SessionData;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Service for managing user sessions.
 */
@ApplicationScoped
public class SessionService {

	private static final Logger LOG = Logger.getLogger(SessionService.class);

	private final Map<String, SessionData> sessions = new ConcurrentHashMap<>();
	private ScheduledExecutorService cleanupExecutor;

	@ConfigProperty(name = "semsim.session.timeout.minutes", defaultValue = "60")
	long sessionTimeoutMinutes;

	@PostConstruct
	void initialize() {
		LOG.info("Initializing session service with timeout: " + sessionTimeoutMinutes + " minutes");

		// Schedule cleanup of expired sessions
		cleanupExecutor = Executors.newSingleThreadScheduledExecutor();
		cleanupExecutor.scheduleAtFixedRate(
				this::cleanupExpiredSessions,
				10,
				10,
				TimeUnit.MINUTES);
	}

	/**
	 * Create a new session and return its ID.
	 * 
	 * @return New session ID
	 */
	public String createSession() {
		String sessionId = generateSessionId();
		sessions.put(sessionId, new SessionData(sessionId));
		LOG.debug("Created new session: " + sessionId);
		return sessionId;
	}

	/**
	 * Get session data by session ID.
	 * 
	 * @param sessionId The session ID
	 * @return SessionData or null if not found
	 */
	public SessionData getSession(String sessionId) {
		if (sessionId == null) {
			return null;
		}

		SessionData session = sessions.get(sessionId);

		if (session != null && session.isExpired(sessionTimeoutMinutes)) {
			LOG.debug("Session expired: " + sessionId);
			sessions.remove(sessionId);
			return null;
		}

		return session;
	}

	/**
	 * Remove a session by ID.
	 * 
	 * @param sessionId The session ID to remove
	 */
	public void removeSession(String sessionId) {
		sessions.remove(sessionId);
		LOG.debug("Removed session: " + sessionId);
	}

	/**
	 * Generate a unique session ID.
	 * 
	 * @return Unique session ID string
	 */
	private String generateSessionId() {
		return UUID.randomUUID().toString();
	}

	/**
	 * Cleanup expired sessions.
	 */
	private void cleanupExpiredSessions() {
		LOG.debug("Running cleanup of expired sessions");
		int before = sessions.size();

		sessions.entrySet().removeIf(entry -> {
			boolean expired = entry.getValue().isExpired(sessionTimeoutMinutes);
			if (expired) {
				LOG.debug("Removing expired session: " + entry.getKey());
			}
			return expired;
		});

		int removed = before - sessions.size();
		LOG.info("Session cleanup: removed " + removed + " expired sessions, remaining: " + sessions.size());
	}
}