package app.strata.community;

import app.strata.common.error.ApiException;
import app.strata.common.security.AuthUser;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * The single place community permissions are decided. Every service that touches community-scoped data
 * asks this class; the frontend only mirrors the result (viewer flags) for display.
 */
@Component
public class CommunityAccess {
    private final MembershipDao memberships;
    private final JdbcClient jdbc;

    public CommunityAccess(MembershipDao memberships, JdbcClient jdbc) {
        this.memberships = memberships;
        this.jdbc = jdbc;
    }

    /** An active ban. {@code until} is empty for permanent bans. */
    public record Ban(Optional<Instant> until) {}

    public Optional<CommunityRole> roleOf(Community c, AuthUser user) {
        return user == null ? Optional.empty() : memberships.role(c.getId(), user.id());
    }

    /** Active (unexpired) ban for this user in this community, if any. */
    public Optional<Ban> banOf(long communityId, AuthUser user) {
        if (user == null) return Optional.empty();
        return jdbc.sql("""
                        SELECT expires_at FROM community_bans
                        WHERE community_id = ? AND user_id = ? AND (expires_at IS NULL OR expires_at > now())""")
                .params(communityId, user.id())
                .query((rs, i) -> {
                    OffsetDateTime t = rs.getObject(1, OffsetDateTime.class);
                    return new Ban(Optional.ofNullable(t).map(OffsetDateTime::toInstant));
                })
                .optional();
    }

    public boolean canView(Community c, Optional<CommunityRole> role, AuthUser user) {
        if (c.visibility() != Community.Visibility.PRIVATE) return true;
        return role.isPresent() || (user != null && user.isAdmin());
    }

    public boolean canPost(Community c, Optional<CommunityRole> role, AuthUser user) {
        if (user == null || banOf(c.getId(), user).isPresent()) return false;
        return switch (c.visibility()) {
            case PUBLIC -> true;
            case RESTRICTED -> role.map(r -> r.atLeast(CommunityRole.CONTRIBUTOR)).orElse(false);
            case PRIVATE -> role.isPresent();
        };
    }

    public boolean canComment(Community c, Optional<CommunityRole> role, AuthUser user) {
        if (user == null || banOf(c.getId(), user).isPresent()) return false;
        return c.visibility() != Community.Visibility.PRIVATE || role.isPresent();
    }

    public boolean canModerate(Optional<CommunityRole> role, AuthUser user) {
        return role.map(CommunityRole::isModerator).orElse(false) || (user != null && user.isAdmin());
    }

    public void requireView(Community c, AuthUser user) {
        if (!canView(c, roleOf(c, user), user)) {
            throw ApiException.forbidden("This community is private");
        }
    }

    /** Throws 403 when the user is banned here; used by every participation path (post, comment, vote, pulse). */
    public void requireNotBanned(long communityId, AuthUser user) {
        if (banOf(communityId, user).isPresent()) throw ApiException.forbidden("You are banned from this community");
    }

    public CommunityRole requireModerator(Community c, AuthUser user) {
        Optional<CommunityRole> role = roleOf(c, user);
        if (!canModerate(role, user)) throw ApiException.forbidden("Moderator permission required");
        return role.orElse(CommunityRole.ADMIN); // platform admins act with community-admin rights
    }
}
