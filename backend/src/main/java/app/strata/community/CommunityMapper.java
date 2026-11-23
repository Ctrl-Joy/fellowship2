package app.strata.community;

import app.strata.community.dto.CommunityDtos.CommunityRef;
import app.strata.community.dto.CommunityDtos.CommunitySummary;
import app.strata.community.dto.CommunityDtos.FlairDto;
import app.strata.topic.Topic.TopicDto;

public final class CommunityMapper {
    private CommunityMapper() {}

    public static CommunityRef ref(Community c) {
        return new CommunityRef(c.getId(), c.getSlug(), c.getName(), c.getIconUrl());
    }

    public static CommunitySummary summary(Community c, boolean isMember) {
        return new CommunitySummary(c.getId(), c.getSlug(), c.getName(), c.getDescription(), c.getIconUrl(),
                c.getBannerUrl(), TopicDto.of(c.getTopic()), c.visibility().name(), c.getMemberCount(),
                c.getTags(), isMember);
    }

    public static FlairDto flair(CommunityFlair f) {
        return f == null ? null : new FlairDto(f.getId(), f.getText(), f.getColor());
    }
}
