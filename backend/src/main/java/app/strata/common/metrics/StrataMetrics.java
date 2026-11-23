package app.strata.common.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.stereotype.Component;

/**
 * Product metrics for Prometheus (D-026). Names follow Micrometer conventions and appear in Prometheus with a
 * {@code strata_} prefix and {@code _total} / {@code _seconds} suffixes (e.g. {@code strata_votes_total}).
 * Tags are low-cardinality enums only: never user ids, community slugs or free text.
 */
@Component
public class StrataMetrics {
    private final MeterRegistry registry;
    private final AtomicInteger websocketSessions = new AtomicInteger();

    public StrataMetrics(MeterRegistry registry) {
        this.registry = registry;
        Gauge.builder("strata.websocket.sessions", websocketSessions, AtomicInteger::get)
                .description("Open STOMP/WebSocket sessions on this node").register(registry);
    }

    /** target = post|comment, direction = up|down|clear. */
    public void vote(String target, int value) {
        String direction = value > 0 ? "up" : value < 0 ? "down" : "clear";
        Counter.builder("strata.votes").description("Votes cast").tag("target", target).tag("direction", direction)
                .register(registry).increment();
    }

    /** outcome = published|held. */
    public void postCreated(String type, boolean held) {
        Counter.builder("strata.posts").description("Posts created").tag("type", type == null ? "unknown" : type)
                .tag("outcome", held ? "held" : "published").register(registry).increment();
    }

    public void commentCreated(boolean held) {
        Counter.builder("strata.comments").description("Comments created").tag("outcome", held ? "held" : "published")
                .register(registry).increment();
    }

    public void messageSent(boolean withMedia) {
        Counter.builder("strata.messages.sent").description("Direct messages sent").tag("media", Boolean.toString(withMedia))
                .register(registry).increment();
    }

    public void websocketOpened() {
        websocketSessions.incrementAndGet();
    }

    public void websocketClosed() {
        websocketSessions.updateAndGet(n -> Math.max(0, n - 1));
    }

    /** kind = post|comment, action = hold|reject, signal = the heuristic that fired. */
    public void spam(String kind, String action, String signal) {
        Counter.builder("strata.spam.flags").description("Spam heuristic holds and rejections").tag("kind", kind)
                .tag("action", action).tag("signal", signal == null ? "unknown" : signal).register(registry).increment();
    }

    /** operation = answer|long, outcome = ok|refused|error. */
    public void aiRequest(String operation, String outcome, Duration latency, long inputTokens, long outputTokens) {
        Timer.builder("strata.ai.request").description("Calls to the AI model provider").tag("operation", operation)
                .tag("outcome", outcome).publishPercentileHistogram().register(registry).record(latency);
        if (inputTokens > 0) {
            DistributionSummary.builder("strata.ai.tokens").description("Tokens per AI request").baseUnit("tokens")
                    .tag("operation", operation).tag("direction", "input").register(registry).record(inputTokens);
        }
        if (outputTokens > 0) {
            DistributionSummary.builder("strata.ai.tokens").description("Tokens per AI request").baseUnit("tokens")
                    .tag("operation", operation).tag("direction", "output").register(registry).record(outputTokens);
        }
    }

    public void feedCache(boolean hit) {
        Counter.builder("strata.feed.cache").description("First-page feed id cache lookups").tag("result", hit ? "hit" : "miss")
                .register(registry).increment();
    }

    /** outcome = clean|infected|unavailable (D-029). */
    public void antivirusScan(String outcome) {
        Counter.builder("strata.antivirus.scans").description("Antivirus scans of uploads").tag("engine", "clamav")
                .tag("outcome", outcome).register(registry).increment();
    }
}
