package app.strata.common.config;

import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** All Strata-specific configuration, bound from {@code strata.*} (environment variables in production). */
@ConfigurationProperties(prefix = "strata")
public record AppProperties(Jwt jwt, Cookie cookie, Cors cors, App app, Mail mail, Storage storage,
                            RateLimit rateLimit, Seed seed, Ai ai) {

    public record Jwt(String secret, Duration accessTtl, Duration refreshTtl, String issuer) {}

    public record Cookie(boolean secure) {}

    public record Cors(List<String> allowedOrigins) {}

    /** publicUrl = where the frontend lives; used to build links in emails. */
    public record App(String publicUrl, String name) {}

    /** mode: "log" (dev: prints mail to the log) or "smtp". */
    public record Mail(String mode, String from) {}

    /** driver: "local" (filesystem). Object-storage driver is planned, see docs/DECISIONS.md. */
    public record Storage(String driver, String localDir, long maxImageBytes, long maxMediaMb) {}

    public record RateLimit(boolean enabled) {}

    public record Seed(boolean enabled) {}

    /**
     * provider: "auto" (Claude when an API key is present, otherwise offline), "claude", or "offline".
     * The key is read from the server environment only and never sent to the browser.
     */
    public record Ai(String provider, String apiKey, String model, String effort, long maxTokens) {}
}
