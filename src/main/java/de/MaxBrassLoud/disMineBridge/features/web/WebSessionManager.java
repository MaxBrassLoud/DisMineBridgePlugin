package de.MaxBrassLoud.disMineBridge.features.web;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * In-memory session store for authenticated web users.
 * Sessions expire after 24 hours of inactivity.
 */
public class WebSessionManager {

    public static class Session {
        public final String token;
        public final String discordId;
        public final String discordUsername;
        public final String discordAvatar;
        public long lastAccess;

        public Session(String token, String discordId, String discordUsername, String discordAvatar) {
            this.token = token;
            this.discordId = discordId;
            this.discordUsername = discordUsername;
            this.discordAvatar = discordAvatar;
            this.lastAccess = System.currentTimeMillis();
        }

        public void touch() {
            this.lastAccess = System.currentTimeMillis();
        }

        public boolean isExpired() {
            return System.currentTimeMillis() - lastAccess > TimeUnit.HOURS.toMillis(24);
        }
    }

    // Pending OAuth states (state → redirect info)
    private final Map<String, Long> pendingStates = new ConcurrentHashMap<>();

    // Active sessions (token → Session)
    private final Map<String, Session> sessions = new ConcurrentHashMap<>();

    private final SecureRandom random = new SecureRandom();
    private final ScheduledExecutorService cleaner;

    public WebSessionManager() {
        // Clean expired sessions every 30 minutes
        cleaner = Executors.newSingleThreadScheduledExecutor();
        cleaner.scheduleAtFixedRate(this::cleanup, 30, 30, TimeUnit.MINUTES);
    }

    // ────────────────────────────────────────────────
    //  OAuth State
    // ────────────────────────────────────────────────

    public String generateState() {
        byte[] bytes = new byte[24];
        random.nextBytes(bytes);
        String state = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        pendingStates.put(state, System.currentTimeMillis());
        return state;
    }

    public boolean consumeState(String state) {
        Long ts = pendingStates.remove(state);
        if (ts == null) return false;
        // State must be used within 10 minutes
        return System.currentTimeMillis() - ts < TimeUnit.MINUTES.toMillis(10);
    }

    // ────────────────────────────────────────────────
    //  Sessions
    // ────────────────────────────────────────────────

    public String createSession(String discordId, String discordUsername, String discordAvatar) {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        sessions.put(token, new Session(token, discordId, discordUsername, discordAvatar));
        return token;
    }

    public Session getSession(String token) {
        if (token == null) return null;
        Session s = sessions.get(token);
        if (s == null) return null;
        if (s.isExpired()) {
            sessions.remove(token);
            return null;
        }
        s.touch();
        return s;
    }

    public void invalidateSession(String token) {
        sessions.remove(token);
    }

    // ────────────────────────────────────────────────
    //  Token extraction from Cookie header
    // ────────────────────────────────────────────────

    public String extractTokenFromCookie(String cookieHeader) {
        if (cookieHeader == null) return null;
        for (String part : cookieHeader.split(";")) {
            part = part.strip();
            if (part.startsWith("dmb_session=")) {
                return part.substring("dmb_session=".length());
            }
        }
        return null;
    }

    // ────────────────────────────────────────────────
    //  Cleanup
    // ────────────────────────────────────────────────

    private void cleanup() {
        sessions.entrySet().removeIf(e -> e.getValue().isExpired());
        // Clean stale states older than 15 minutes
        long now = System.currentTimeMillis();
        pendingStates.entrySet().removeIf(e -> now - e.getValue() > TimeUnit.MINUTES.toMillis(15));
    }

    public void shutdown() {
        cleaner.shutdownNow();
    }
}