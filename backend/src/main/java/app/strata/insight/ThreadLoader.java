package app.strata.insight;

import app.strata.common.security.AuthUser;
import app.strata.post.PostService;
import app.strata.post.PostService.PostContext;
import app.strata.user.UserDirectory;
import app.strata.user.dto.UserDtos.UserSummary;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * Loads a bounded, permission-checked snapshot of a thread for analysis: live comments only (deleted and
 * removed content never feeds Context Mode or digests), best-ranked first, capped so cost stays bounded.
 */
@Component
public class ThreadLoader {
    public static final int MAX_COMMENTS = 500;

    private final JdbcClient jdbc;
    private final PostService posts;
    private final UserDirectory users;

    public ThreadLoader(JdbcClient jdbc, PostService posts, UserDirectory users) {
        this.jdbc = jdbc;
        this.posts = posts;
        this.users = users;
    }

    public record Node(long id, Long parentId, int depth, String body, Long authorId, UserSummary author, int score,
                       int up, int down, double best, double controversy, int replyCount, Instant createdAt,
                       Stance stance, boolean suggested, Map<Stance, Integer> labelWeights, Stance myLabel) {}

    public record Snapshot(PostContext post, String postText, List<Node> nodes, int totalLive) {}

    public Snapshot load(long postId, AuthUser viewer) {
        PostContext ctx = posts.context(postId, viewer);
        record Raw(long id, Long parentId, int depth, String body, Long authorId, int score, int up, int down,
                   double best, double controversy, int replyCount, Instant createdAt) {}
        List<Raw> raw = jdbc.sql("""
                        SELECT id, parent_id, depth, body, author_id, score, upvotes, downvotes, best_rank, controversy,
                               reply_count, created_at
                        FROM comments WHERE post_id = ? AND deleted_at IS NULL AND removed = FALSE
                        ORDER BY best_rank DESC, score DESC, id LIMIT ?""")
                .params(postId, MAX_COMMENTS)
                .query((rs, i) -> new Raw(rs.getLong(1), (Long) rs.getObject(2), rs.getInt(3), rs.getString(4),
                        (Long) rs.getObject(5), rs.getInt(6), rs.getInt(7), rs.getInt(8), rs.getDouble(9), rs.getDouble(10),
                        rs.getInt(11), rs.getObject(12, OffsetDateTime.class).toInstant()))
                .list();
        int total = jdbc.sql("SELECT count(*) FROM comments WHERE post_id = ? AND deleted_at IS NULL AND removed = FALSE")
                .param(postId).query(Integer.class).single();

        Map<Long, Map<Stance, Integer>> weights = new HashMap<>();
        jdbc.sql("""
                        SELECT s.comment_id, s.stance, sum(CASE WHEN s.user_id = c.author_id THEN 2 ELSE 1 END)
                        FROM comment_stances s JOIN comments c ON c.id = s.comment_id
                        WHERE c.post_id = ? GROUP BY s.comment_id, s.stance""")
                .param(postId)
                .query((rs, i) -> {
                    weights.computeIfAbsent(rs.getLong(1), k -> new EnumMap<>(Stance.class))
                            .put(Stance.valueOf(rs.getString(2)), rs.getInt(3));
                    return null;
                }).list();
        Map<Long, Stance> mine = new HashMap<>();
        if (viewer != null) {
            jdbc.sql("""
                            SELECT s.comment_id, s.stance FROM comment_stances s JOIN comments c ON c.id = s.comment_id
                            WHERE c.post_id = ? AND s.user_id = ?""")
                    .params(postId, viewer.id())
                    .query((rs, i) -> mine.put(rs.getLong(1), Stance.valueOf(rs.getString(2)))).list();
        }
        Map<Long, UserSummary> authors = users.summaries(raw.stream().map(Raw::authorId).toList());

        List<Node> nodes = raw.stream().map(r -> {
            Map<Stance, Integer> w = weights.getOrDefault(r.id(), Map.of());
            Stance stance;
            boolean suggested;
            if (w.isEmpty()) {
                stance = TextSignals.suggestStance(r.body());
                suggested = true;
            } else {
                stance = w.entrySet().stream().max(Map.Entry.<Stance, Integer>comparingByValue()
                        .thenComparing(e -> -e.getKey().ordinal())).orElseThrow().getKey();
                suggested = false;
            }
            return new Node(r.id(), r.parentId(), r.depth(), r.body(), r.authorId(), authors.get(r.authorId()), r.score(),
                    r.up(), r.down(), r.best(), r.controversy(), r.replyCount(), r.createdAt(), stance, suggested, w,
                    mine.get(r.id()));
        }).toList();
        String postText = ctx.post().getBodyText();
        return new Snapshot(ctx, postText, nodes, total);
    }
}
