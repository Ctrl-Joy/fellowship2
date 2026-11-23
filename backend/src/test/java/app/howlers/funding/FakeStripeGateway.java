package app.howlers.funding;

import app.howlers.funding.payment.PaymentProvider.CheckoutRequest;
import app.howlers.funding.payment.PaymentProvider.CheckoutSession;
import app.howlers.funding.payment.StripeGateway;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/** In-memory stand-in for Stripe: no network. Tests move sessions to "paid" the way Checkout would. */
public class FakeStripeGateway implements StripeGateway {
    private static final AtomicLong SEQ = new AtomicLong(System.nanoTime() % 1_000_000);

    public final Map<String, CheckoutSession> sessions = new ConcurrentHashMap<>();
    public final Map<String, CheckoutRequest> requests = new ConcurrentHashMap<>();
    public final Map<String, String> invoiceByPaymentIntent = new ConcurrentHashMap<>();
    public final List<String> cancelled = new ArrayList<>();
    public final List<String> idempotencyKeys = new ArrayList<>();
    public volatile boolean failNext;

    @Override
    public CheckoutSession createCheckoutSession(CheckoutRequest r) {
        if (failNext) {
            failNext = false;
            throw new StripeGatewayException("Stripe couldn't create a Checkout Session: simulated outage", null);
        }
        String id = "cs_test_fake" + SEQ.incrementAndGet();
        CheckoutSession s = new CheckoutSession(id, "https://checkout.stripe.com/c/pay/" + id, "open", "unpaid", r.recurring(),
                r.clientReferenceId(), Map.copyOf(r.metadata()), r.amountMinor(), r.currency().toLowerCase(), null, null, null, false);
        sessions.put(id, s);
        requests.put(id, r);
        idempotencyKeys.add(r.idempotencyKey());
        return s;
    }

    @Override
    public CheckoutSession retrieveCheckoutSession(String sessionId) {
        CheckoutSession s = sessions.get(sessionId);
        if (s == null) throw new StripeGatewayException("No such checkout session", null);
        return s;
    }

    @Override
    public CheckoutSession expireCheckoutSession(String sessionId) {
        CheckoutSession s = retrieveCheckoutSession(sessionId);
        if (!"open".equals(s.status())) return s;
        CheckoutSession expired = with(s, "expired", "unpaid", null, null, null);
        sessions.put(sessionId, expired);
        return expired;
    }

    @Override
    public synchronized void cancelSubscription(String subscriptionId) {
        cancelled.add(subscriptionId);
    }

    @Override
    public String invoiceForPaymentIntent(String paymentIntentId) {
        return invoiceByPaymentIntent.get(paymentIntentId);
    }

    /** What Checkout does when the contributor pays a one-time session. */
    public CheckoutSession payOnce(String sessionId, String paymentIntent) {
        CheckoutSession s = with(retrieveCheckoutSession(sessionId), "complete", "paid", paymentIntent, null, null);
        sessions.put(sessionId, s);
        return s;
    }

    /** What Checkout does when the contributor starts a subscription: a subscription and its first, paid invoice. */
    public CheckoutSession paySubscription(String sessionId, String subscription, String invoice) {
        CheckoutSession s = with(retrieveCheckoutSession(sessionId), "complete", "paid", null, subscription, invoice);
        sessions.put(sessionId, s);
        return s;
    }

    public String sessionFor(long pendingId) {
        return requests.entrySet().stream().filter(e -> e.getValue().clientReferenceId().equals(Long.toString(pendingId)))
                .map(Map.Entry::getKey).findFirst().orElseThrow();
    }

    private static CheckoutSession with(CheckoutSession s, String status, String paymentStatus, String pi, String sub, String invoice) {
        return new CheckoutSession(s.id(), s.url(), status, paymentStatus, s.recurring(), s.clientReferenceId(), s.metadata(), s.amountTotal(),
                s.currency(), pi, sub, invoice, s.livemode());
    }
}
