package app.strata.user;

import app.strata.user.dto.UserDtos.Me;
import app.strata.user.dto.UserDtos.Settings;
import app.strata.user.dto.UserDtos.UserSummary;

public final class UserMapper {
    private UserMapper() {}

    public static Me toMe(User u) {
        return new Me(u.getId(), u.getUsername(), u.getEmail(), u.getDisplayName(), u.getAvatarUrl(), u.getBannerUrl(),
                u.getBio(), u.isEmailVerified(), u.isOnboardingCompleted(), u.getRole(), u.getPostKarma(),
                u.getCommentKarma(), u.getCreatedAt(),
                new Settings(u.getTheme(), u.isShowNsfw(), u.isProfilePublic(), u.isShowCommunities(),
                        u.isEmailNotifications()));
    }

    /** Deleted accounts are rendered as null authors by callers; this maps live accounts only. */
    public static UserSummary toSummary(User u) {
        if (u == null || u.getDeletedAt() != null) return null;
        return new UserSummary(u.getId(), u.getUsername(), u.getDisplayName(), u.getAvatarUrl());
    }
}
