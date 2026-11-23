package app.strata.ai;

import app.strata.common.config.AppProperties;
import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.core.http.StreamResponse;
import com.anthropic.helpers.BetaMessageAccumulator;
import com.anthropic.models.beta.messages.BetaRawMessageStreamEvent;
import com.anthropic.models.beta.AnthropicBeta;
import com.anthropic.models.beta.messages.BetaCacheControlEphemeral;
import com.anthropic.models.beta.messages.BetaFallbacksParam;
import com.anthropic.models.beta.messages.BetaMessage;
import com.anthropic.models.beta.messages.BetaOutputConfig;
import com.anthropic.models.beta.messages.BetaStopReason;
import com.anthropic.models.beta.messages.BetaTextBlockParam;
import com.anthropic.models.beta.messages.MessageCreateParams;
import jakarta.annotation.PreDestroy;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Claude via the official Anthropic Java SDK. Enabled when ANTHROPIC_API_KEY is configured on the server.
 *
 * <ul>
 *   <li>Model from AI_MODEL (default {@code claude-opus-5-5}); depth controlled with output_config.effort
 *       (thinking is always on for this model, so no thinking parameter is sent).</li>
 *   <li>The static system prompt carries cache_control so repeated questions reuse the cached prefix; the
 *       sources and question vary per request and live in the final user turn.</li>
 *   <li>Server-side refusal fallbacks are enabled ({@code fallbacks: "default"}) so a safety-classifier refusal
 *       is retried on a suitable model instead of failing the request; a remaining refusal is reported honestly.</li>
 * </ul>
 */
@Component
public class ClaudeProvider implements AiProvider {
    private static final Logger log = LoggerFactory.getLogger(ClaudeProvider.class);

    private final AppProperties.Ai cfg;
    private final AnthropicClient client;

    public ClaudeProvider(AppProperties props) {
        this.cfg = props.ai();
        String key = cfg.apiKey();
        this.client = key == null || key.isBlank() ? null : AnthropicOkHttpClient.builder().apiKey(key).build();
    }

    @Override
    public String id() {
        return "claude";
    }

    @Override
    public String label() {
        return "Claude (" + cfg.model() + ")";
    }

    @Override
    public boolean available() {
        return client != null;
    }

    @Override
    public Answer answer(Request r) {
        MessageCreateParams.Builder p = MessageCreateParams.builder()
                .model(cfg.model())
                .maxTokens(cfg.maxTokens())
                .systemOfBetaTextBlockParams(List.of(BetaTextBlockParam.builder()
                        .text(AnswerPrompt.SYSTEM)
                        .cacheControl(BetaCacheControlEphemeral.builder().build())
                        .build()))
                .outputConfig(BetaOutputConfig.builder().effort(BetaOutputConfig.Effort.of(cfg.effort())).build())
                .addBeta(AnthropicBeta.SERVER_SIDE_FALLBACK_2026_07_01)
                .fallbacks(BetaFallbacksParam.ofDefault());
        for (Turn t : r.history()) {
            if ("USER".equals(t.role())) p.addUserMessage(t.text()); else p.addAssistantMessage(t.text());
        }
        p.addUserMessage(AnswerPrompt.userTurn(r));

        BetaMessage msg = client.beta().messages().create(p.build());
        Optional<BetaStopReason> stop = msg.stopReason();
        Integer in = (int) msg.usage().inputTokens();
        Integer out = (int) msg.usage().outputTokens();
        if (stop.isPresent() && stop.get().equals(BetaStopReason.REFUSAL)) {
            log.info("Community AI request declined by safety classifier");
            return new Answer("I can't help with that request.", List.of(), "The model declined to answer this question.", in, out);
        }
        String text = msg.content().stream()
                .flatMap(b -> b.text().stream())
                .map(t -> t.text())
                .collect(Collectors.joining("\n\n"));
        String notice = stop.isPresent() && stop.get().equals(BetaStopReason.MAX_TOKENS)
                ? "The answer was cut off at the length limit." : null;
        String[] parts = AnswerPrompt.split(text);
        return new Answer(parts[0], AnswerPrompt.followUps(parts[1]), notice, in, out);
    }

    /** Result of a long generation: text plus whether it was cut short or declined. */
    public record LongText(String text, boolean truncated, boolean refused, long inputTokens, long outputTokens) {}

    /**
     * Long-form generation (e.g. a 45–90 minute podcast script). Streams the response, as recommended for large
     * max_tokens to avoid HTTP timeouts, and accumulates it into the final message.
     */
    public LongText generateLong(String system, String userTurn, long maxTokens) {
        if (client == null) throw new IllegalStateException("Claude is not configured");
        MessageCreateParams params = MessageCreateParams.builder()
                .model(cfg.model())
                .maxTokens(maxTokens)
                .systemOfBetaTextBlockParams(List.of(BetaTextBlockParam.builder()
                        .text(system)
                        .cacheControl(BetaCacheControlEphemeral.builder().build())
                        .build()))
                .outputConfig(BetaOutputConfig.builder().effort(BetaOutputConfig.Effort.of(cfg.effort())).build())
                .addBeta(AnthropicBeta.SERVER_SIDE_FALLBACK_2026_07_01)
                .fallbacks(BetaFallbacksParam.ofDefault())
                .addUserMessage(userTurn)
                .build();
        BetaMessageAccumulator acc = BetaMessageAccumulator.create();
        try (StreamResponse<BetaRawMessageStreamEvent> stream = client.beta().messages().createStreaming(params)) {
            stream.stream().forEach(acc::accumulate);
        }
        BetaMessage msg = acc.message();
        Optional<BetaStopReason> stop = msg.stopReason();
        String text = msg.content().stream().flatMap(b -> b.text().stream()).map(t -> t.text()).collect(Collectors.joining("\n\n"));
        return new LongText(text, stop.isPresent() && stop.get().equals(BetaStopReason.MAX_TOKENS),
                stop.isPresent() && stop.get().equals(BetaStopReason.REFUSAL), msg.usage().inputTokens(), msg.usage().outputTokens());
    }

    @PreDestroy
    void close() {
        if (client != null) client.close();
    }
}
