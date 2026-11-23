package app.strata.insight;

import app.strata.user.dto.UserDtos.UserSummary;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.List;
import java.util.Map;

public final class InsightDtos {
    private InsightDtos() {}

    /** A comment as an argument. {@code suggested=true} means the stance came from rules, not people. */
    public record ArgItem(long commentId, Long parentId, String excerpt, UserSummary author, int score, Stance stance,
                          boolean suggested, Stance myLabel, Map<Stance, Integer> labelCounts) {}

    public record EvidenceLink(String url, String domain, long commentId, String excerpt) {}

    public record MapNode(long id, Long parentId, Stance stance, boolean suggested, int score, String label) {}

    public record ContextStats(int analysed, int total, int labelled, int suggested, int participants,
                               Map<Stance, Integer> byStance) {}

    public record ContextView(long postId, String title, String excerpt, ContextStats stats,
                              Map<Stance, List<ArgItem>> groups, List<EvidenceLink> evidence, List<ArgItem> openQuestions,
                              List<ArgItem> agreement, List<ArgItem> disagreement, List<MapNode> map, boolean truncated) {}

    public record StanceRequest(@NotNull Stance stance) {}

    // ------------------------------------------------------------------ digest

    /** One extracted line, always attributed to the comment it came from. kind = DATA/ANECDOTE/OPINION/SPECULATION/STATEMENT. */
    public record Line(String text, long commentId, String kind, String author) {}

    public record Consensus(String status, String statement) {}

    public record DigestSections(List<Line> summary, String question, List<Line> takeaways, List<Line> supporting,
                                 List<Line> opposing, List<ArgItem> mostUseful, List<ArgItem> minority,
                                 List<ArgItem> unresolved, List<EvidenceLink> links, Consensus consensus) {}

    public record DigestView(long postId, String length, String focus, String engine, String engineNote,
                             Instant generatedAt, int commentCountAtGeneration, int currentCommentCount, boolean stale,
                             DigestSections sections) {}
}
