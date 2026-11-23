package app.strata.funding;

import app.strata.common.error.ApiException;
import app.strata.common.security.AuthUser;
import app.strata.funding.FundingDtos.AllocationPreview;
import app.strata.funding.FundingDtos.CloseRequest;
import app.strata.funding.FundingDtos.CommunityFunding;
import app.strata.funding.FundingDtos.ContributeRequest;
import app.strata.funding.FundingDtos.ContributeResult;
import app.strata.funding.FundingDtos.CreatePoolRequest;
import app.strata.funding.FundingDtos.FundingStatus;
import app.strata.funding.FundingDtos.MyFunding;
import app.strata.funding.FundingDtos.Policy;
import app.strata.funding.FundingDtos.PolicyRequest;
import app.strata.funding.FundingDtos.PoolSummary;
import app.strata.funding.FundingDtos.SubscriptionDto;
import app.strata.funding.FundingDtos.TransparencyReport;
import app.strata.funding.FundingDtos.WebhookResult;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Moderator funding (demo mode). Every amount in every response is a {@link Money} with its demo label. */
@Tag(name = "Moderator funding")
@RestController
@RequestMapping("/api/v1")
public class FundingController {
    private final FundingService funding;

    public FundingController(FundingService funding) {
        this.funding = funding;
    }

    private static AuthUser require(AuthUser user) {
        if (user == null) throw ApiException.unauthorized("Authentication required");
        return user;
    }

    @GetMapping("/funding/status")
    public FundingStatus status() {
        return funding.status();
    }

    @GetMapping("/communities/{slug}/funding")
    public CommunityFunding community(@PathVariable String slug, @AuthenticationPrincipal AuthUser user) {
        return funding.community(slug, user);
    }

    @PutMapping("/communities/{slug}/funding/policy")
    public Policy policy(@PathVariable String slug, @AuthenticationPrincipal AuthUser user, @Valid @RequestBody PolicyRequest req) {
        return funding.setPolicy(slug, require(user), req);
    }

    @PostMapping("/communities/{slug}/funding/pools")
    @ResponseStatus(HttpStatus.CREATED)
    public PoolSummary createPool(@PathVariable String slug, @AuthenticationPrincipal AuthUser user, @Valid @RequestBody CreatePoolRequest req) {
        return funding.createPool(slug, require(user), req);
    }

    @GetMapping("/funding/pools/{id}/report")
    public TransparencyReport report(@PathVariable long id, @AuthenticationPrincipal AuthUser user) {
        return funding.report(id, user);
    }

    /** Moderators only, so it requires a principal even though it's a GET. */
    @GetMapping("/funding/pools/{id}/allocation-preview")
    public AllocationPreview preview(@PathVariable long id, @AuthenticationPrincipal AuthUser user) {
        return funding.preview(id, require(user));
    }

    @PostMapping("/funding/pools/{id}/contributions")
    @ResponseStatus(HttpStatus.CREATED)
    public ContributeResult contribute(@PathVariable long id, @AuthenticationPrincipal AuthUser user, @Valid @RequestBody ContributeRequest req) {
        return funding.contribute(id, require(user), req);
    }

    @PostMapping("/funding/pools/{id}/close")
    public TransparencyReport close(@PathVariable long id, @AuthenticationPrincipal AuthUser user,
                                    @Valid @RequestBody(required = false) CloseRequest req) {
        return funding.close(id, require(user), req);
    }

    @GetMapping("/me/funding")
    public MyFunding mine(@AuthenticationPrincipal AuthUser user) {
        return funding.my(require(user));
    }

    @PostMapping("/me/funding/subscriptions/{id}/cancel")
    public SubscriptionDto cancel(@PathVariable long id, @AuthenticationPrincipal AuthUser user) {
        return funding.cancelSubscription(id, require(user));
    }

    /**
     * Provider webhooks. Public (no JWT): authenticity comes from the signature header, which the provider verifies
     * over the raw body, so the body is read as a String and never re-serialised before verification.
     */
    @PostMapping(value = "/funding/webhooks/{provider}", consumes = "application/json")
    public WebhookResult webhook(@PathVariable String provider, @RequestBody String payload,
                                 @RequestHeader(value = "Strata-Demo-Signature", required = false) String signature) {
        return funding.webhook(provider, payload, signature);
    }
}
