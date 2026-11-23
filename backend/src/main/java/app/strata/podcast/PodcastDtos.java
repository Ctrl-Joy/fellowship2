package app.strata.podcast;

import app.strata.community.dto.CommunityDtos.CommunityRef;
import app.strata.user.dto.UserDtos.UserSummary;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;

public final class PodcastDtos {
    private PodcastDtos() {}

    public static final String TYPES = "WEEKLY_ROUNDUP|DEEP_DIVE|DEBATE|INTERVIEW|BEGINNER_GUIDE|MONTHLY_REPORT|COMPARISON|HIGHLIGHTS|MULTI_PERSPECTIVE";

    public record Progress(int positionSeconds, boolean completed) {}

    public record EpisodeSummary(long id, CommunityRef community, String title, String summary, String type, String mediaKind,
                                 Integer durationSeconds, String thumbnailUrl, String status, Instant publishedAt, Instant createdAt,
                                 int playCount, boolean saved, Progress progress, int chapterCount) {}

    public record Chapter(long id, int startSeconds, String title) {}

    public record SourceRef(int position, long postId, Long commentId, String title, String communitySlug, String excerpt, String link) {}

    public record EpisodeViewer(boolean canManage) {}

    public record EpisodeDetail(long id, CommunityRef community, String title, String summary, String type, String mediaKind,
                                Integer durationSeconds, String thumbnailUrl, String status, Instant publishedAt, Instant createdAt,
                                int playCount, boolean saved, Progress progress, int chapterCount,
                                String mediaUrl, String mediaContentType, boolean allowDownload, List<Chapter> chapters,
                                List<SourceRef> sources, boolean hasTranscript, String transcriptVttUrl, String script,
                                String scriptStatus, String scriptSource, String scriptError, UserSummary createdBy,
                                UserSummary publishedBy, EpisodeViewer viewer) {}

    public record Segment(long id, double startSeconds, double endSeconds, String speaker, String text) {}

    public record Home(List<EpisodeSummary> featured, List<EpisodeSummary> continueListening, List<EpisodeSummary> recent,
                       List<EpisodeSummary> saved) {}

    public record Capabilities(boolean aiScriptDrafting, boolean audioGeneration, boolean videoGeneration, long maxUploadMb,
                               List<String> audioTypes, List<String> videoTypes) {}

    public record CreateEpisode(@NotBlank @Size(max = 200) String title, @Size(max = 2000) String summary,
                                @NotNull @Pattern(regexp = TYPES) String type) {}

    public record ChapterInput(@NotNull @Min(0) @Max(86_400) Integer startSeconds, @NotBlank @Size(max = 200) String title) {}

    public record SourceInput(@NotNull Long postId, Long commentId) {}

    public record UpdateEpisode(@Size(min = 1, max = 200) String title, @Size(max = 2000) String summary,
                                @Pattern(regexp = TYPES) String type, Boolean allowDownload, @Size(max = 200_000) String script,
                                @Size(max = 100) List<@Valid ChapterInput> chapters, @Size(max = 50) List<@Valid SourceInput> sources) {}

    public record TranscriptInput(@NotNull @Pattern(regexp = "vtt|srt|timestamped") String format, @NotBlank String content) {}

    public record ProgressInput(@NotNull @Min(0) @Max(86_400) Integer positionSeconds, Boolean completed) {}

    public record DraftRequest(@NotBlank @Size(max = 300) String topic, @NotNull @Min(10) @Max(90) Integer targetMinutes) {}
}
