package app.strata.funding.payment;

import java.util.Map;

/**
 * The only way funding code talks to a payment processor. Strata ships with {@link DemoPaymentProvider} alone; see
 * docs/DECISIONS.md D-022 for how a real provider would plug in and the compliance review it needs first.
 */
public interface PaymentProvider {

    /** Stable id stored on every pool, charge and subscription (e.g. "demo"). */
    String id();

    /** True when charges move no real money. Every amount produced through a demo provider is labelled as such. */
    boolean demo();

    /** Charges once. {@code idempotencyKey} must make retries of the same logical charge return the same result. */
    Charge createCharge(ChargeRequest request);

    /** Sets up a monthly subscription. The first and later cycles are charged through {@link #createCharge}. */
    Subscription createSubscription(SubscriptionRequest request);

    /** Cancels a subscription at the provider. Must be safe to call twice. */
    void cancel(String subscriptionRef);

    /**
     * Verifies a webhook's signature and freshness and parses it.
     *
     * @throws WebhookVerificationException when the signature is missing, malformed, wrong or too old
     */
    WebhookEvent verifyWebhook(String payload, String signatureHeader);

    record ChargeRequest(long amountMinor, String currency, String description, String idempotencyKey) {}

    record Charge(String chargeId, boolean succeeded, boolean demo) {}

    record SubscriptionRequest(long amountMinor, String currency, String description, String idempotencyKey) {}

    record Subscription(String subscriptionRef, boolean demo) {}

    record WebhookEvent(String id, String type, Map<String, Object> data) {}

    class WebhookVerificationException extends RuntimeException {
        public WebhookVerificationException(String message) {
            super(message);
        }
    }
}
