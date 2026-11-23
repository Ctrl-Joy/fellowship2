package app.howlers.messaging;

import app.howlers.user.dto.UserDtos.UserSummary;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;

public final class DmDtos {
    private DmDtos() {}

    /**
     * {@code url} is a participant-only signed URL for images uploaded through {@code /me/dm-media} (valid 30 to 60 min;
     * refetch the messages for fresh ones), or the legacy public media URL for older attachments (D-024).
     */
    public record DmMedia(long id, String url, Integer width, Integer height, app.howlers.storage.Thumbnails thumbnails,
                          boolean signed, java.time.Instant expiresAt) {}

    public record DmMessage(long id, long conversationId, Long senderId, String body, List<DmMedia> media,
                            Instant createdAt, boolean deleted) {}

    public record LastMessage(long id, Long senderId, String excerpt, Instant createdAt, boolean deleted) {}

    public record ConversationSummary(long id, UserSummary other, LastMessage lastMessage, long unreadCount,
                                      Long otherLastReadMessageId, boolean online, boolean canMessage,
                                      String cannotMessageReason, Instant updatedAt) {}

    public record MessagePage(List<DmMessage> items, boolean hasMore) {}

    public record StartRequest(@NotBlank @Size(max = 21) String username) {}

    public record SendRequest(@Size(max = 4000) String body, @Size(max = 4) List<Long> mediaIds) {}

    public record ReadRequest(@NotNull Long messageId) {}

    public record TypingRequest(Long conversationId) {}
}
