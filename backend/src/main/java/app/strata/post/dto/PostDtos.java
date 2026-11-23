package app.strata.post.dto;

import app.strata.community.dto.CommunityDtos.CommunityRef;
import app.strata.community.dto.CommunityDtos.FlairDto;
import app.strata.user.dto.UserDtos.UserSummary;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import jakarta.validation.Valid;
import java.time.Instant;
import java.util.List;

public final class PostDtos {
    private PostDtos() {}

    public record MediaRef(long id, String url, Integer width, Integer height) {}

    public record PollOptionDto(long id, String label, int votes) {}

    public record PollDto(boolean multiple, Instant closesAt, boolean closed, int totalVotes, List<Long> myOptionIds,
                          List<PollOptionDto> options) {}

    public record PostSummary(long id, String type, String title, String bodyPreview, String url, String linkDomain,
                              List<MediaRef> media, PollDto poll, UserSummary author, CommunityRef community,
                              FlairDto flair, List<String> tags, int score, int upvotes, int downvotes,
                              int commentCount, int myVote, boolean saved, boolean pinned, boolean locked,
                              boolean removed, Instant createdAt, Instant editedAt) {}

    public record PostViewer(boolean canEdit, boolean canDelete, boolean canModerate) {}

    public record PostDetail(long id, String type, String title, String bodyPreview, String url, String linkDomain,
                             List<MediaRef> media, PollDto poll, UserSummary author, CommunityRef community,
                             FlairDto flair, List<String> tags, int score, int upvotes, int downvotes,
                             int commentCount, int myVote, boolean saved, boolean pinned, boolean locked,
                             boolean removed, Instant createdAt, Instant editedAt, String bodyHtml, PostViewer viewer) {
        public static PostDetail of(PostSummary s, String bodyHtml, PostViewer viewer) {
            return new PostDetail(s.id(), s.type(), s.title(), s.bodyPreview(), s.url(), s.linkDomain(), s.media(),
                    s.poll(), s.author(), s.community(), s.flair(), s.tags(), s.score(), s.upvotes(), s.downvotes(),
                    s.commentCount(), s.myVote(), s.saved(), s.pinned(), s.locked(), s.removed(), s.createdAt(),
                    s.editedAt(), bodyHtml, viewer);
        }
    }

    public record PollInput(@NotNull @Size(min = 2, max = 8) List<@NotBlank @Size(max = 120) String> options,
                            boolean multiple, Instant closesAt) {}

    public record CreatePostRequest(
            @NotBlank String communitySlug,
            @NotNull @Pattern(regexp = "TEXT|LINK|IMAGE|POLL") String type,
            @NotBlank @Size(max = 300) String title,
            @Size(max = 60_000) String bodyHtml,
            @Size(max = 2000) String url,
            @Size(max = 20) List<Long> mediaIds,
            Long flairId,
            @Size(max = 5) List<@Pattern(regexp = "^[a-z0-9-]{1,24}$", message = "tags are lowercase words with dashes") String> tags,
            @Valid PollInput poll) {}

    public record UpdatePostRequest(@Size(min = 1, max = 300) String title, @Size(max = 60_000) String bodyHtml,
                                    @Size(max = 5) List<@Pattern(regexp = "^[a-z0-9-]{1,24}$") String> tags,
                                    Long flairId) {}

    public record VoteRequest(@NotNull @Min(-1) @Max(1) Integer value) {}

    public record VoteResult(int score, int upvotes, int downvotes, int myVote) {}

    public record PollVoteRequest(@NotNull @Size(min = 1, max = 8) List<Long> optionIds) {}
}
