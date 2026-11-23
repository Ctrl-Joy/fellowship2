package app.strata.funding;

import app.strata.funding.payment.FundingRuntime;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Background work for funding: charges due monthly demo subscriptions (never Stripe ones) and closes pools whose period ended. Each item runs in
 * its own transaction (through the service proxy) so one failure doesn't block the rest; both steps are idempotent, so
 * running them on several nodes at once is safe.
 */
@Component
public class FundingJobs {
    private static final Logger log = LoggerFactory.getLogger(FundingJobs.class);
    private static final int BATCH = 200;

    private final FundingService funding;
    private final FundingRuntime runtime;
    private final FundingProperties props;

    public FundingJobs(FundingService funding, FundingRuntime runtime, FundingProperties props) {
        this.funding = funding;
        this.runtime = runtime;
        this.props = props;
    }

    @Scheduled(fixedDelayString = "${strata.funding.job-interval-ms:60000}", initialDelay = 30_000)
    public void scheduled() {
        if (!runtime.enabled() || Boolean.FALSE.equals(props.jobsEnabled())) return;
        Instant now = Instant.now();
        chargeDue(now);
        closeDue(now);
    }

    /** Charges every subscription due at {@code now}. Returns the number of new charges. */
    public int chargeDue(Instant now) {
        // Hosted providers (Stripe) bill cycles themselves and Howlers records each paid invoice from the webhook.
        if (runtime.enabled() && runtime.provider().hostedCheckout()) return 0;
        int charged = 0;
        for (long id : funding.dueSubscriptions(now, BATCH)) {
            try {
                if (funding.chargeCycle(id, now) != null) charged++;
            } catch (RuntimeException e) {
                log.warn("Recurring charge for subscription {} failed: {}", id, e.toString());
            }
        }
        return charged;
    }

    /** Closes (or moves to AWAITING_ALLOCATION) every pool whose period ended by {@code now}. */
    public int closeDue(Instant now) {
        int n = 0;
        for (long id : funding.duePools(now, BATCH)) {
            try {
                funding.closeIfDue(id, now);
                n++;
            } catch (RuntimeException e) {
                log.warn("Closing funding pool {} failed: {}", id, e.toString());
            }
        }
        return n;
    }
}
