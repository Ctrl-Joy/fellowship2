package app.strata.funding.payment;

import app.strata.funding.payment.PaymentProvider.CheckoutRequest;
import app.strata.funding.payment.PaymentProvider.CheckoutSession;
import com.stripe.StripeClient;
import com.stripe.exception.StripeException;
import com.stripe.model.InvoicePayment;
import com.stripe.model.Subscription;
import com.stripe.model.checkout.Session;
import com.stripe.net.RequestOptions;
import com.stripe.param.InvoicePaymentListParams;
import com.stripe.param.checkout.SessionCreateParams;
import com.stripe.param.checkout.SessionCreateParams.LineItem;
import com.stripe.param.checkout.SessionCreateParams.LineItem.PriceData;
import com.stripe.param.checkout.SessionCreateParams.LineItem.PriceData.ProductData;
import com.stripe.param.checkout.SessionCreateParams.LineItem.PriceData.Recurring;
import java.util.Locale;
import java.util.Map;

/** {@link StripeGateway} over the official SDK. The secret key lives only inside the {@link StripeClient}. */
public class SdkStripeGateway implements StripeGateway {
    private final StripeClient client;

    public SdkStripeGateway(String secretKey) {
        this.client = StripeClient.builder().setApiKey(secretKey).setConnectTimeout(10_000).setReadTimeout(20_000)
                .setMaxNetworkRetries(2).build();
    }

    @Override
    public CheckoutSession createCheckoutSession(CheckoutRequest r) {
        PriceData.Builder price = PriceData.builder()
                .setCurrency(r.currency().toLowerCase(Locale.ROOT))
                .setUnitAmount(r.amountMinor())
                .setProductData(ProductData.builder().setName(r.productName()).setDescription(r.description()).build());
        if (r.recurring()) price.setRecurring(Recurring.builder().setInterval(Recurring.Interval.MONTH).build());
        SessionCreateParams.Builder b = SessionCreateParams.builder()
                .setMode(r.recurring() ? SessionCreateParams.Mode.SUBSCRIPTION : SessionCreateParams.Mode.PAYMENT)
                .setSuccessUrl(r.successUrl())
                .setCancelUrl(r.cancelUrl())
                .setClientReferenceId(r.clientReferenceId())
                .setExpiresAt(r.expiresAtEpochSeconds())
                .addLineItem(LineItem.builder().setQuantity(1L).setPriceData(price.build()).build());
        r.metadata().forEach(b::putMetadata);
        if (r.recurring()) {
            SessionCreateParams.SubscriptionData.Builder sub = SessionCreateParams.SubscriptionData.builder().setDescription(r.description());
            r.metadata().forEach(sub::putMetadata);
            b.setSubscriptionData(sub.build());
        } else {
            SessionCreateParams.PaymentIntentData.Builder pi = SessionCreateParams.PaymentIntentData.builder().setDescription(r.description());
            r.metadata().forEach(pi::putMetadata);
            b.setPaymentIntentData(pi.build());
        }
        try {
            return map(client.v1().checkout().sessions().create(b.build(),
                    RequestOptions.builder().setIdempotencyKey(r.idempotencyKey()).build()));
        } catch (StripeException e) {
            throw wrap("create a Checkout Session", e);
        }
    }

    @Override
    public CheckoutSession retrieveCheckoutSession(String sessionId) {
        try {
            return map(client.v1().checkout().sessions().retrieve(sessionId));
        } catch (StripeException e) {
            throw wrap("retrieve the Checkout Session", e);
        }
    }

    @Override
    public CheckoutSession expireCheckoutSession(String sessionId) {
        try {
            Session s = client.v1().checkout().sessions().retrieve(sessionId);
            if (!"open".equals(s.getStatus())) return map(s);
            return map(client.v1().checkout().sessions().expire(sessionId));
        } catch (StripeException e) {
            throw wrap("expire the Checkout Session", e);
        }
    }

    @Override
    public void cancelSubscription(String subscriptionId) {
        try {
            Subscription s = client.v1().subscriptions().retrieve(subscriptionId);
            if ("canceled".equals(s.getStatus()) || "incomplete_expired".equals(s.getStatus())) return;
            client.v1().subscriptions().cancel(subscriptionId);
        } catch (StripeException e) {
            throw wrap("cancel the subscription", e);
        }
    }

    @Override
    public String invoiceForPaymentIntent(String paymentIntentId) {
        try {
            var page = client.v1().invoicePayments().list(InvoicePaymentListParams.builder()
                    .setPayment(InvoicePaymentListParams.Payment.builder()
                            .setType(InvoicePaymentListParams.Payment.Type.PAYMENT_INTENT)
                            .setPaymentIntent(paymentIntentId).build())
                    .setLimit(1L).build());
            return page.getData().stream().map(InvoicePayment::getInvoice).findFirst().orElse(null);
        } catch (StripeException e) {
            throw wrap("look up the invoice for a payment", e);
        }
    }

    static CheckoutSession map(Session s) {
        Map<String, String> meta = s.getMetadata() == null ? Map.of() : Map.copyOf(s.getMetadata());
        return new CheckoutSession(s.getId(), s.getUrl(), s.getStatus(), s.getPaymentStatus(), "subscription".equals(s.getMode()),
                s.getClientReferenceId(), meta, s.getAmountTotal(), s.getCurrency(), s.getPaymentIntent(), s.getSubscription(),
                s.getInvoice(), s.getLivemode());
    }

    private static StripeGatewayException wrap(String what, StripeException e) {
        String id = e.getRequestId() == null ? "" : " (request " + e.getRequestId() + ")";
        return new StripeGatewayException("Stripe couldn't " + what + ": " + e.getMessage() + id, e);
    }
}
