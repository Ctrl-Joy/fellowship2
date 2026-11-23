package app.strata.realtime;

import java.util.Collection;
import java.util.Map;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Pushes small "something changed" events to connected clients. When called inside a transaction, the send is
 * deferred until after commit, so a client never refetches before the data it was told about is visible, and a
 * rolled-back change is never announced.
 */
@Component
public class RealtimePublisher {
    public static final String USER_QUEUE = "/queue/events";

    private final SimpMessagingTemplate template;

    public RealtimePublisher(SimpMessagingTemplate template) {
        this.template = template;
    }

    public record Event(String type, Object payload) {}

    public void toUser(long userId, String type, Object payload) {
        afterCommit(() -> template.convertAndSendToUser(Long.toString(userId), USER_QUEUE, new Event(type, payload)));
    }

    public void toUsers(Collection<Long> userIds, String type, Object payload) {
        for (Long id : userIds) toUser(id, type, payload);
    }

    public void pulseChanged(long pulseId, int totalVoters) {
        afterCommit(() -> template.convertAndSend("/topic/pulse." + pulseId, Map.of("pulseId", pulseId, "totalVoters", totalVoters)));
    }

    public void modQueueChanged(long communityId) {
        afterCommit(() -> template.convertAndSend("/topic/modqueue." + communityId, Map.of("communityId", communityId)));
    }

    private static void afterCommit(Runnable r) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    r.run();
                }
            });
        } else {
            r.run();
        }
    }
}
