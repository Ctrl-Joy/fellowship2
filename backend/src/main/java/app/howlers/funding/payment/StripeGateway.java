package app.howlers.funding.payment;

import app.howlers.funding.payment.PaymentProvider.CheckoutRequest;
import app.howlers.funding.payment.PaymentProvider.CheckoutSession;

/**
 * The few Stripe API calls Howlers makes. {@link SdkStripeGateway} implements it with the official SDK; tests use a fake,
 * so the suite never calls Stripe.
 */
public interface StripeGateway {

    /** Creates a hosted Checkout Session (payment or subscription mode, the price inline via {@code price_data}). */
    CheckoutSession createCheckoutSession(CheckoutRequest request);

    CheckoutSession retrieveCheckoutSession(String sessionId);

    /** Expires the session if it's still open; otherwise returns it unchanged. */
    CheckoutSession expireCheckoutSession(String sessionId);

    /** Cancels the subscription now. A subscription that is already cancelled is not an error. */
    void cancelSubscription(String subscriptionId);

    /** The invoice paid by this PaymentIntent, or null when it isn't an invoice payment. */
    String invoiceForPaymentIntent(String paymentIntentId);

    /** A Stripe call failed. The message is Stripe's (never contains the key) plus the request id. */
    class StripeGatewayException extends RuntimeException {
        public StripeGatewayException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
