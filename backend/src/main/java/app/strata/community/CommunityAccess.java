package app.strata.community;

import app.strata.common.error.ApiException;
import app.strata.common.security.AuthUser;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * The single place community permissions are decided. Every service that touches community-scoped data
 * asks this class; the frontend only mirrors the result (viewer flags) for display.
 */
@Component
public class CommunityAccess {
    private final MembershipDao memberships;

    public CommunityAccess(MembershipDao memberships) {
        this.memberships = memberships;
    }

    public Optional<CommunityRole> roleOf(Community c, AuthUser user) {
        return user == null ? Optional.empty() : memberships.role(c.getId(), user.id());
    }

    public boolean canView(Community c, Optional<CommunityRole> role, AuthUser user) {
        if (c.visibility() != Community.Visibility.PRIVATE) return true;
        return role.isPresent() || (user != null && user.isAdmin());
    }

    public boolean canPost(Community c, Optional<CommunityRole> role, AuthUser user) {
        if (user == null) return false;
        return switch (c.visibility()) {
            case PUBLIC -> true;
            case RESTRICTED -> role.map(r -> r.atLeast(CommunityRole.CONTRIBUTOR)).orElse(false);
            case PRIVATE -> role.isPresent();
        };
    }

    public boolean canComment(Community c, Optional<CommunityRole> role, AuthUser user) {
        if (user == null) return false;
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

    public CommunityRole requireModerator(Community c, AuthUser user) {
        Optional<CommunityRole> role = roleOf(c, user);
        if (!canModerate(role, user)) throw ApiException.forbidden("Moderator permission required");
        return role.orElse(CommunityRole.ADMIN); // platform admins act with community-admin rights
    }
}
