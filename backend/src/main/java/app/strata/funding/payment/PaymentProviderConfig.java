package app.strata.funding.payment;

import app.strata.funding.FundingProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Chooses the payment provider at startup. There is deliberately no fallback: asking for a provider that doesn't
 * exist (e.g. {@code FUNDING_PROVIDER=stripe}) stops the application instead of silently faking payments. To run
 * without payments at all, set {@code FUNDING_ENABLED=false}.
 */
@Configuration
public class PaymentProviderConfig {
    private static final Logger log = LoggerFactory.getLogger(PaymentProviderConfig.class);

    @Bean
    FundingRuntime fundingRuntime(FundingProperties props, ObjectMapper mapper) {
        FundingRuntime runtime = select(props, mapper, Clock.systemUTC());
        if (runtime.enabled()) {
            log.info("Moderator funding enabled with provider '{}' (demo={})", runtime.provider().id(), runtime.provider().demo());
        } else {
            log.info("Moderator funding disabled by configuration");
        }
        return runtime;
    }

    /** Pure selection logic, unit-tested without a Spring context. */
    public static FundingRuntime select(FundingProperties props, ObjectMapper mapper, Clock clock) {
        if (!props.isEnabled()) return FundingRuntime.disabled("Moderator funding is turned off on this server.");
        String id = props.providerId();
        if (DemoPaymentProvider.ID.equals(id)) {
            return FundingRuntime.of(new DemoPaymentProvider(props.demoWebhookSecret(), props.tolerance(), mapper, clock));
        }
        throw new IllegalStateException("strata.funding.provider='" + id + "' is not implemented. Only 'demo' exists; real payments need "
                + "a provider integration and a compliance review first (docs/DECISIONS.md D-022). Set FUNDING_PROVIDER=demo, "
                + "or FUNDING_ENABLED=false to turn funding off.");
    }
}
