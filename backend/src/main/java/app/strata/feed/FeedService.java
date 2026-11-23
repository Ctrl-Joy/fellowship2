package app.strata.feed;

import app.strata.common.security.AuthUser;
import app.strata.common.web.CursorPage;
import app.strata.community.Community;
import app.strata.community.CommunityAccess;
import app.strata.community.CommunityService;
import app.strata.post.PostQueryService;
import app.strata.post.PostQueryService.Sort;
import app.strata.post.PostQueryService.Spec;
import app.strata.post.dto.PostDtos.PostSummary;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Feed tabs are thin compositions of a <em>scope</em> (which posts are eligible) and a <em>sort</em>
 * (see docs/RANKING.md). All filtering of private and hidden content happens in PostQueryService.
 */
@Service
public class FeedService {
    private final PostQueryService queries;
    private final CommunityService communities;
    private final CommunityAccess access;

    public FeedService(PostQueryService queries, CommunityService communities, CommunityAccess access) {
        this.queries = queries;
        this.communities = communities;
        this.access = access;
    }

    private static final String JOINED = "p.community_id IN (SELECT community_id FROM community_members WHERE user_id = :viewer)";
    private static final String FOLLOWED = "p.author_id IN (SELECT followee_id FROM user_follows WHERE follower_id = :viewer)";
    private static final String TOPICS = "c.topic_id IN (SELECT topic_id FROM user_topics WHERE user_id = :viewer)";
    private static final String PUBLIC_ONLY = "c.visibility <> 'PRIVATE'";

    @Transactional(readOnly = true)
    public CursorPage<PostSummary> home(String tab, String t, String cursor, int limit, AuthUser user) {
        Long viewer = user == null ? null : user.id();
        String normalized = tab == null ? "foryou" : tab.toLowerCase();
        if (viewer == null && (normalized.equals("foryou") || normalized.equals("following"))) normalized = "popular";
        return switch (normalized) {
            case "following" -> queries.page(new Spec(JOINED + " OR " + FOLLOWED, Map.of(), Sort.NEW, null, null, cursor, limit, viewer, false));
            case "latest" -> queries.page(new Spec(PUBLIC_ONLY, Map.of(), Sort.NEW, null, null, cursor, limit, viewer, false));
            case "rising" -> queries.page(new Spec(PUBLIC_ONLY, Map.of(), Sort.RISING, null, null, cursor, limit, viewer, false));
            case "top" -> queries.page(new Spec(PUBLIC_ONLY, Map.of(), Sort.TOP, window(t), null, cursor, limit, viewer, false));
            case "foryou" -> forYou(cursor, limit, viewer);
            default -> queries.page(new Spec(PUBLIC_ONLY, Map.of(), Sort.HOT, null, null, cursor, limit, viewer, false));
        };
    }

    /**
     * Personal relevance: joined communities, followed people, and communities in chosen topics, ranked
     * by hot. A brand-new account with no signals gets the popular feed rather than an empty page.
     */
    private CursorPage<PostSummary> forYou(String cursor, int limit, Long viewer) {
        CursorPage<PostSummary> page = queries.page(new Spec(JOINED + " OR " + FOLLOWED + " OR (" + TOPICS + " AND " + PUBLIC_ONLY + ")",
                Map.of(), Sort.HOT, null, null, cursor, limit, viewer, false));
        if (cursor == null && page.items().isEmpty()) {
            return queries.page(new Spec(PUBLIC_ONLY, Map.of(), Sort.HOT, null, null, null, limit, viewer, false));
        }
        return page;
    }

    @Transactional(readOnly = true)
    public CursorPage<PostSummary> community(String slug, String sort, String t, String cursor, int limit, AuthUser user) {
        Community c = communities.require(slug);
        access.requireView(c, user);
        Long viewer = user == null ? null : user.id();
        Sort s = switch (sort == null ? "hot" : sort.toLowerCase()) {
            case "new" -> Sort.NEW;
            case "top" -> Sort.TOP;
            case "rising" -> Sort.RISING;
            default -> Sort.HOT;
        };
        boolean pinFirst = s == Sort.HOT;
        CursorPage<PostSummary> page = queries.page(new Spec("p.community_id = :cid", Map.of("cid", c.getId()), s,
                window(t), null, cursor, limit, viewer, pinFirst));
        if (pinFirst && cursor == null) {
            List<PostSummary> pinned = queries.pinned(c.getId(), viewer);
            if (!pinned.isEmpty()) {
                List<PostSummary> merged = new ArrayList<>(pinned);
                merged.addAll(page.items());
                return new CursorPage<>(merged, page.nextCursor());
            }
        }
        return page;
    }

    @Transactional(readOnly = true)
    public CursorPage<PostSummary> byAuthor(long authorId, String cursor, int limit, AuthUser user) {
        return queries.page(new Spec("p.author_id = :aid", Map.of("aid", authorId), Sort.NEW, null, null, cursor, limit,
                user == null ? null : user.id(), false));
    }

    @Transactional(readOnly = true)
    public List<PostSummary> trending(int limit, AuthUser user) {
        return queries.page(new Spec(PUBLIC_ONLY + " AND p.created_at > now() - interval '3 days'", Map.of(), Sort.TOP, null,
                null, null, Math.min(Math.max(limit, 1), 10), user == null ? null : user.id(), false)).items();
    }

    static String window(String t) {
        if (t == null) return "1 day";
        return switch (t.toLowerCase()) {
            case "hour" -> "1 hour";
            case "week" -> "7 days";
            case "month" -> "1 month";
            case "year" -> "1 year";
            case "all" -> null;
            default -> "1 day";
        };
    }
}
