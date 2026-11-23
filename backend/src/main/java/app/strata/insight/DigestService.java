package app.strata.insight;

import app.strata.common.error.ApiException;
import app.strata.common.ratelimit.RateLimiter;
import app.strata.common.security.AuthUser;
import app.strata.insight.InsightDtos.DigestSections;
import app.strata.insight.InsightDtos.DigestView;
import app.strata.insight.ThreadLoader.Snapshot;
import app.strata.post.PostService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.Set;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Thread Digest with caching. A digest is cached per (post, length, focus) and marked stale once the thread
 * has grown materially (≥ 5 new comments and ≥ 10%). Refreshing is explicit and rate-limited.
 */
@Service
public class DigestService {
    public static final Set<String> LENGTHS = Set.of("short", "standard", "detailed");
    public static final Set<String> FOCUSES = Set.of("balanced", "top", "new", "beginner", "technical");

    private final ThreadLoader loader;
    private final DigestEngine engine;
    private final JdbcClient jdbc;
    private final ObjectMapper json;
    private final RateLimiter limiter;
    private final PostService posts;

    public DigestService(ThreadLoader loader, DigestEngine engine, JdbcClient jdbc, ObjectMapper json, RateLimiter limiter,
                         PostService posts) {
        this.loader = loader;
        this.engine = engine;
        this.jdbc = jdbc;
        this.json = json;
        this.limiter = limiter;
        this.posts = posts;
    }

    private record Cached(String engine, String payload, int commentCount, Instant generatedAt) {}

    @Transactional
    public DigestView get(long postId, String length, String focus, AuthUser viewer) {
        validate(length, focus);
        posts.context(postId, viewer); // permission check before touching the cache
        int current = liveCount(postId);
        Optional<Cached> cached = jdbc.sql("""
                        SELECT engine, payload::text, comment_count, generated_at FROM thread_digests
                        WHERE post_id = ? AND length = ? AND focus = ?""")
                .params(postId, length, focus)
                .query((rs, i) -> new Cached(rs.getString(1), rs.getString(2), rs.getInt(3),
                        rs.getObject(4, OffsetDateTime.class).toInstant()))
                .optional();
        if (cached.isPresent() && cached.get().engine().equals(engine.id())) {
            Cached c = cached.get();
            return new DigestView(postId, length, focus, c.engine(), engine.note(), c.generatedAt(), c.commentCount(), current,
                    isStale(c.commentCount(), current), read(c.payload()));
        }
        return generate(postId, length, focus, viewer);
    }

    @Transactional
    public DigestView refresh(long postId, String length, String focus, AuthUser viewer) {
        validate(length, focus);
        limiter.check("digest:" + (viewer == null ? "anon" : viewer.id()), 10, Duration.ofHours(1));
        return generate(postId, length, focus, viewer);
    }

    private DigestView generate(long postId, String length, String focus, AuthUser viewer) {
        Snapshot s = loader.load(postId, viewer);
        if (s.nodes().isEmpty()) throw ApiException.badRequest("There are no comments to summarise yet");
        DigestSections sections = engine.generate(s, length, focus);
        Instant now = Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MICROS); // match PostgreSQL precision
        jdbc.sql("""
                        INSERT INTO thread_digests (post_id, length, focus, engine, payload, comment_count, generated_at)
                        VALUES (?, ?, ?, ?, CAST(? AS jsonb), ?, ?)
                        ON CONFLICT (post_id, length, focus) DO UPDATE SET engine = EXCLUDED.engine, payload = EXCLUDED.payload,
                          comment_count = EXCLUDED.comment_count, generated_at = EXCLUDED.generated_at""")
                .params(postId, length, focus, engine.id(), write(sections), s.totalLive(), java.sql.Timestamp.from(now))
                .update();
        return new DigestView(postId, length, focus, engine.id(), engine.note(), now, s.totalLive(), s.totalLive(), false, sections);
    }

    static boolean isStale(int atGeneration, int current) {
        int grown = current - atGeneration;
        return grown >= 5 && grown >= Math.ceil(atGeneration * 0.10);
    }

    private int liveCount(long postId) {
        return jdbc.sql("SELECT count(*) FROM comments WHERE post_id = ? AND deleted_at IS NULL AND removed = FALSE")
                .param(postId).query(Integer.class).single();
    }

    private static void validate(String length, String focus) {
        if (!LENGTHS.contains(length)) throw ApiException.field("length", "Unknown digest length");
        if (!FOCUSES.contains(focus)) throw ApiException.field("focus", "Unknown digest focus");
    }

    private String write(DigestSections s) {
        try {
            return json.writeValueAsString(s);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    private DigestSections read(String payload) {
        try {
            return json.readValue(payload, DigestSections.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }
}
