package app.strata.common.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

class ProductionSafetyCheckTest {

    private static MockEnvironment safe() {
        return new MockEnvironment()
                .withProperty("strata.jwt.secret", "c2VjdXJlLXJhbmRvbS1zZWNyZXQtZm9yLXRlc3RzLW9ubHktMTIzNDU2Nzg5MA==")
                .withProperty("strata.funding.enabled", "true")
                .withProperty("strata.funding.demo-webhook-secret", "a-long-random-webhook-secret")
                .withProperty("spring.datasource.password", "a-long-db-password-123")
                .withProperty("strata.files.visitor-salt", "salty-salt-salt")
                .withProperty("strata.cookie.secure", "true")
                .withProperty("management.endpoints.web.exposure.include", "health,info,prometheus")
                .withProperty("management.server.port", "8081")
                .withProperty("server.port", "8080");
    }

    @Test
    void aProperlyConfiguredProductionPasses() {
        assertThat(ProductionSafetyCheck.problems(safe())).isEmpty();
        new ProductionSafetyCheck(safe()).afterPropertiesSet();
    }

    @Test
    void devDefaultsAreAllReportedAtOnce() {
        MockEnvironment env = new MockEnvironment()
                .withProperty("strata.jwt.secret", ProductionSafetyCheck.DEV_JWT_SECRET)
                .withProperty("strata.funding.demo-webhook-secret", ProductionSafetyCheck.DEV_WEBHOOK_SECRET)
                .withProperty("spring.datasource.password", "strata")
                .withProperty("strata.files.visitor-salt", "")
                .withProperty("strata.cookie.secure", "false")
                .withProperty("strata.storage.driver", "s3")
                .withProperty("strata.storage.s3.secret-key", "minioadmin")
                .withProperty("management.endpoints.web.exposure.include", "health,prometheus");
        assertThat(ProductionSafetyCheck.problems(env)).hasSize(7)
                .anyMatch(p -> p.startsWith("JWT_SECRET"))
                .anyMatch(p -> p.startsWith("FUNDING_DEMO_WEBHOOK_SECRET"))
                .anyMatch(p -> p.startsWith("DATABASE_PASSWORD"))
                .anyMatch(p -> p.startsWith("FILE_VISITOR_SALT"))
                .anyMatch(p -> p.contains("cookie"))
                .anyMatch(p -> p.startsWith("S3_SECRET_KEY"))
                .anyMatch(p -> p.contains("MANAGEMENT_PORT"));
        assertThatThrownBy(() -> new ProductionSafetyCheck(env).afterPropertiesSet())
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("Refusing to start");
    }

    @Test
    void shortOrNonBase64JwtSecretsAndDisabledFundingAreHandled() {
        assertThat(ProductionSafetyCheck.problems(safe().withProperty("strata.jwt.secret", "c2hvcnQ=")))
                .containsExactly("JWT_SECRET must be base64 of at least 32 random bytes");
        assertThat(ProductionSafetyCheck.problems(safe().withProperty("strata.jwt.secret", "not base64 !!")))
                .containsExactly("JWT_SECRET must be base64 (openssl rand -base64 48)");
        // With funding off, its webhook secret doesn't matter.
        assertThat(ProductionSafetyCheck.problems(safe().withProperty("strata.funding.enabled", "false")
                .withProperty("strata.funding.demo-webhook-secret", ProductionSafetyCheck.DEV_WEBHOOK_SECRET))).isEmpty();
    }
}
