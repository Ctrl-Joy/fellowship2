package app.strata.onboarding;

import app.strata.common.security.AuthUser;
import app.strata.community.CommunityRepository;
import app.strata.community.CommunityService;
import app.strata.community.dto.CommunityDtos.CommunitySummary;
import app.strata.topic.TopicRepository;
import app.strata.user.User;
import app.strata.user.UserMapper;
import app.strata.user.UserRepository;
import app.strata.user.UserService;
import app.strata.user.dto.UserDtos.Me;
import app.strata.user.dto.UserDtos.UserSummary;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Guided first-run: interests → communities → people → preferences. Every step is optional. */
@Tag(name = "Onboarding")
@RestController
@RequestMapping("/api/v1/onboarding")
public class OnboardingController {
    private final JdbcClient jdbc;
    private final CommunityService communities;
    private final CommunityRepository communityRepo;
    private final TopicRepository topics;
    private final UserRepository users;
    private final UserService userService;

    public OnboardingController(JdbcClient jdbc, CommunityService communities, CommunityRepository communityRepo,
                                TopicRepository topics, UserRepository users, UserService userService) {
        this.jdbc = jdbc;
        this.communities = communities;
        this.communityRepo = communityRepo;
        this.topics = topics;
        this.users = users;
        this.userService = userService;
    }

    public record Suggestions(List<CommunitySummary> communities, List<UserSummary> users) {}

    public record CompleteRequest(@Size(max = 30) List<Long> topicIds, @Size(max = 50) List<String> communitySlugs,
                                  @Size(max = 50) List<String> followUsernames,
                                  @Pattern(regexp = "dark|light|system") String theme) {}

    @GetMapping("/suggestions")
    @Transactional(readOnly = true)
    public Suggestions suggestions(@AuthenticationPrincipal AuthUser user,
                                   @RequestParam(required = false) List<Long> topicIds) {
        List<Long> topicFilter = topicIds == null ? List.of() : topicIds.stream().limit(30).toList();
        List<Long> ids = jdbc.sql("""
                        SELECT c.id FROM communities c
                        WHERE c.visibility <> 'PRIVATE'
                          AND (:anyTopic = FALSE OR c.topic_id IN (:topics))
                          AND NOT EXISTS (SELECT 1 FROM community_members m WHERE m.community_id = c.id AND m.user_id = :u)
                        ORDER BY c.member_count DESC, c.id LIMIT 12""")
                .param("anyTopic", !topicFilter.isEmpty()).param("topics", topicFilter.isEmpty() ? List.of(-1L) : topicFilter)
                .param("u", user.id()).query(Long.class).list();
        List<UserSummary> people = users.suggestedToFollow(user.id(), 8).stream().map(UserMapper::toSummary).toList();
        return new Suggestions(communities.byIds(ids, user), people);
    }

    @PostMapping("/complete")
    @Transactional
    public Me complete(@AuthenticationPrincipal AuthUser user, @Valid @RequestBody CompleteRequest req) {
        User u = userService.requireActive(user.id());
        if (req.topicIds() != null) {
            topics.clearUserTopics(u.getId());
            for (Long id : req.topicIds()) {
                if (topics.existsById(id)) topics.addUserTopic(u.getId(), id);
            }
        }
        if (req.communitySlugs() != null) {
            for (String slug : req.communitySlugs()) {
                communityRepo.findBySlug(slug)
                        .filter(c -> c.visibility() != app.strata.community.Community.Visibility.PRIVATE)
                        .ifPresent(c -> communities.join(c.getSlug(), user));
            }
        }
        if (req.followUsernames() != null) {
            // Pre-filter instead of catching: an exception escaping a @Transactional call would mark this
            // whole transaction rollback-only. Unknown or self entries are simply skipped.
            for (String name : req.followUsernames()) {
                boolean followable = users.findByUsernameIgnoreCase(name)
                        .filter(User::isActive).filter(t -> t.getId() != user.id()).isPresent();
                if (followable) userService.follow(user, name);
            }
        }
        if (req.theme() != null) u.setTheme(req.theme());
        u.setOnboardingCompleted(true);
        return UserMapper.toMe(u);
    }
}
