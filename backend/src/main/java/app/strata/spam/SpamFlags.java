package app.strata.spam;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Writes spam flag rows. Rejections use their own transaction because the write that triggered them rolls back. */
@Component
public class SpamFlags {
    private final JdbcClient jdbc;

    public SpamFlags(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** Joins the caller's transaction: the flag exists exactly when the held content does. */
    public long recordHeld(long communityId, long authorId, String type, long postId, Long commentId, String rules,
                           String details, String excerpt) {
        return jdbc.sql("""
                        INSERT INTO spam_flags (community_id, author_id, target_type, post_id, comment_id, rules, outcome, details, excerpt)
                        VALUES (?, ?, ?, ?, ?, ?, 'HELD', ?, ?) RETURNING id""")
                .params(communityId, authorId, type, postId, commentId, rules, details, excerpt)
                .query(Long.class).single();
    }

    /** Nothing to review (the content was never stored), so the flag is closed on arrival; it's kept for the record. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordRejected(long communityId, long authorId, String type, String rules, String details, String excerpt) {
        jdbc.sql("""
                        INSERT INTO spam_flags (community_id, author_id, target_type, rules, outcome, details, excerpt, status)
                        VALUES (?, ?, ?, ?, 'REJECTED', ?, ?, 'CLOSED')""")
                .params(communityId, authorId, type, rules, details, excerpt).update();
    }
}
