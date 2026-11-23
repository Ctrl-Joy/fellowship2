package app.strata.funding;

import app.strata.community.dto.CommunityDtos.CommunityRef;
import app.strata.user.dto.UserDtos.UserSummary;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;

public final class FundingDtos {
    private FundingDtos() {}

    public record CurrencyInfo(String code, int exponent) {}

    /** What the server can do. {@code demo} is true for every provider that exists today. */
    public record FundingStatus(boolean enabled, String provider, boolean demo, String label, String notice,
                                List<CurrencyInfo> currencies) {}

    public record Policy(String policy, int activityCap, UserSummary updatedBy, Instant updatedAt) {}

    public record PoolSummary(long id, CommunityRef community, String title, String purpose, Money goal, Money raised,
                              long contributorCount, Instant periodStart, Instant periodEnd, String status, String provider,
                              boolean demo, UserSummary createdBy, String closedPolicy, Instant closedAt, Instant createdAt) {}

    public record CommunityViewer(boolean canContribute, boolean canCreatePool, boolean canSetPolicy, boolean canClose) {}

    public record CommunityFunding(FundingStatus status, Policy policy, List<PoolSummary> pools, CommunityViewer viewer) {}

    public record Contribution(long id, PoolRef pool, String kind, Money amount, boolean showName, Long subscriptionId,
                               Integer cycle, String chargeId, Instant createdAt) {}

    public record PoolRef(long id, String title, CommunityRef community, String status) {}

    public record SubscriptionDto(long id, PoolRef pool, Money amount, boolean showName, String status, int cyclesCharged,
                                  Instant nextChargeAt, Instant createdAt, Instant cancelledAt, String cancelledBy) {}

    public record ContributeResult(Contribution contribution, SubscriptionDto subscription) {}

    public record MyFunding(FundingStatus status, List<Contribution> contributions, List<SubscriptionDto> subscriptions,
                            List<PoolSummary> openPools) {}

    public record Allocation(UserSummary moderator, String policy, Money amount, Integer activityScore, Integer reportsResolved,
                             Integer appealsHandled, String note, Instant createdAt) {}

    public record LedgerEntry(long id, String entryType, String debitAccount, String debitLabel, String creditAccount,
                              String creditLabel, Money amount, Instant createdAt) {}

    public record Reconciliation(Money contributionsTotal, Money ledgerIn, Money ledgerOut, Money allocationsTotal,
                                 Money poolBalance, boolean reconciles) {}

    public record TransparencyReport(PoolSummary pool, Money raised, long contributionCount, long contributorCount,
                                     long anonymousContributors, List<UserSummary> namedContributors, Money allocated,
                                     List<Allocation> allocations, List<LedgerEntry> ledger, boolean ledgerTruncated,
                                     Reconciliation reconciliation, String closedNote) {}

    /** What closing the pool now would allocate. Nothing is written. */
    public record AllocationPreview(String policy, Money total, List<Allocation> allocations, String note,
                                    Instant windowStart, Instant windowEnd) {}

    // ---------------------------------------------------------------- requests

    public record CreatePoolRequest(@NotBlank @Size(max = 120) String title, @NotBlank @Size(min = 10, max = 2000) String purpose,
                                    @NotNull @Pattern(regexp = "[A-Z]{3}") String currency, @NotNull @Positive Long goalMinor,
                                    Instant periodStart, @NotNull Instant periodEnd) {}

    public record ContributeRequest(@NotNull @Positive Long amountMinor, boolean recurring, boolean showName) {}

    public record PolicyRequest(@NotNull @Pattern(regexp = "EQUAL|ACTIVITY|MANUAL") String policy, @Min(1) @Max(1000) Integer activityCap) {}

    public record ManualAllocation(@NotBlank @Size(max = 21) String username, @NotNull @PositiveOrZero Long amountMinor) {}

    /** Closing a pool. {@code allocations} and {@code note} are required when the community's policy is MANUAL. */
    public record CloseRequest(@Valid @Size(max = 100) List<ManualAllocation> allocations, @Size(max = 1000) String note) {}

    public record WebhookResult(String status, String eventId) {}
}
