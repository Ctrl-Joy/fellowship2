package app.strata.common.security;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * Short-lived cache of each account's status and role, consulted on every authenticated request (D-021). Access
 * tokens live 15 minutes; without this, a suspended user or a demoted admin would keep their rights until the token
 * expired. Entries expire after {@link #TTL} and are evicted immediately when an admin changes the account, so on a
 * single node changes apply at once and on several nodes within the TTL.
 */
@Component
public class AccountStatusCache {
    static final Duration TTL = Duration.ofSeconds(30);

    public record State(String status, String role) {
        public boolean active() {
            return "ACTIVE".equals(status);
        }
    }

    private record Entry(State state, long expiresAtNanos) {}

    private final JdbcClient jdbc;
    private final ConcurrentHashMap<Long, Entry> cache = new ConcurrentHashMap<>();

    public AccountStatusCache(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** Empty when the account no longer exists. */
    public Optional<State> state(long userId) {
        long now = System.nanoTime();
        Entry e = cache.get(userId);
        if (e != null && e.expiresAtNanos() - now > 0) return Optional.of(e.state());
        Optional<State> loaded = jdbc.sql("SELECT status, role FROM users WHERE id = ?").param(userId)
                .query((rs, i) -> new State(rs.getString(1), rs.getString(2))).optional();
        if (loaded.isPresent()) {
            if (cache.size() > 50_000) cache.clear(); // crude bound; entries are tiny and refill on demand
            cache.put(userId, new Entry(loaded.get(), now + TTL.toNanos()));
        } else {
            cache.remove(userId);
        }
        return loaded;
    }

    public void evict(long userId) {
        cache.remove(userId);
    }
}
