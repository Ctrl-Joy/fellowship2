package app.howlers.realtime;

import app.howlers.common.security.AuthUser;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionConnectedEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

/**
 * Who is online = who has at least one live STOMP session (several tabs count once). On a transition, the change is
 * pushed only to people who share a conversation with that user, never broadcast. Per instance, see D-017.
 */
@Component
public class PresenceTracker {
    private final Map<Long, AtomicInteger> sessions = new ConcurrentHashMap<>();
    private final RealtimePublisher publisher;
    private final JdbcClient jdbc;

    public PresenceTracker(RealtimePublisher publisher, JdbcClient jdbc) {
        this.publisher = publisher;
        this.jdbc = jdbc;
    }

    public boolean isOnline(long userId) {
        AtomicInteger n = sessions.get(userId);
        return n != null && n.get() > 0;
    }

    @EventListener
    public void onConnected(SessionConnectedEvent e) {
        AuthUser u = StompSecurityInterceptor.userOf(e.getUser());
        if (u == null) return;
        int n = sessions.computeIfAbsent(u.id(), k -> new AtomicInteger()).incrementAndGet();
        if (n == 1) announce(u.id(), true);
    }

    @EventListener
    public void onDisconnected(SessionDisconnectEvent e) {
        AuthUser u = StompSecurityInterceptor.userOf(e.getUser());
        if (u == null) return;
        AtomicInteger counter = sessions.get(u.id());
        if (counter == null) return;
        if (counter.decrementAndGet() <= 0) {
            sessions.remove(u.id(), counter);
            announce(u.id(), false);
        }
    }

    private void announce(long userId, boolean online) {
        List<Long> partners = jdbc.sql("""
                        SELECT DISTINCT other.user_id FROM dm_participants me
                        JOIN dm_participants other ON other.conversation_id = me.conversation_id AND other.user_id <> me.user_id
                        WHERE me.user_id = ?""")
                .param(userId).query(Long.class).list();
        publisher.toUsers(partners, "PRESENCE", Map.of("userId", userId, "online", online));
    }
}
