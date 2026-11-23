package app.strata.funding;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Moderator funding configuration ({@code strata.funding.*}).
 *
 * @param enabled            false turns the whole feature off (reads still work, every write is refused)
 * @param provider           payment provider id. Only {@code demo} exists; anything else refuses to start (see D-022)
 * @param demoWebhookSecret  HMAC secret the demo provider signs and verifies webhooks with (>= 16 chars)
 * @param webhookToleranceSeconds how old a signed webhook timestamp may be before it's rejected
 * @param jobsEnabled        runs the recurring-charge and period-close jobs on a schedule
 */
@ConfigurationProperties(prefix = "strata.funding")
public record FundingProperties(Boolean enabled, String provider, String demoWebhookSecret, Long webhookToleranceSeconds,
                                Boolean jobsEnabled) {

    public boolean isEnabled() {
        return enabled == null || enabled;
    }

    public String providerId() {
        return provider == null || provider.isBlank() ? "demo" : provider.strip().toLowerCase();
    }

    public long tolerance() {
        return webhookToleranceSeconds == null ? 300 : webhookToleranceSeconds;
    }
}
