import type { CommunityRef, UserSummary } from './api';

/** Integer minor units (cents) of an ISO 4217 currency. Never a float. `label` is "DEMO, no real money" for demo money. */
export interface Money {
  amountMinor: number;
  currency: string;
  demo: boolean;
  label: string | null;
}

export type PoolStatus = 'OPEN' | 'AWAITING_ALLOCATION' | 'CLOSED';
export type AllocationPolicy = 'EQUAL' | 'ACTIVITY' | 'MANUAL';

export interface FundingStatus {
  enabled: boolean;
  provider: string | null;
  demo: boolean;
  label: string | null;
  notice: string | null;
  currencies: { code: string; exponent: number }[];
}

export interface FundingPolicy {
  policy: AllocationPolicy;
  activityCap: number;
  updatedBy: UserSummary | null;
  updatedAt: string | null;
}

export interface PoolSummary {
  id: number;
  community: CommunityRef;
  title: string;
  purpose: string;
  goal: Money;
  raised: Money;
  contributorCount: number;
  periodStart: string;
  periodEnd: string;
  status: PoolStatus;
  provider: string;
  demo: boolean;
  createdBy: UserSummary | null;
  closedPolicy: AllocationPolicy | null;
  closedAt: string | null;
  createdAt: string;
}

export interface CommunityFunding {
  status: FundingStatus;
  policy: FundingPolicy;
  pools: PoolSummary[];
  viewer: { canContribute: boolean; canCreatePool: boolean; canSetPolicy: boolean; canClose: boolean };
}

export interface PoolRef {
  id: number;
  title: string;
  community: CommunityRef;
  status: PoolStatus;
}

export interface Contribution {
  id: number;
  pool: PoolRef;
  kind: 'ONE_TIME' | 'RECURRING';
  amount: Money;
  showName: boolean;
  subscriptionId: number | null;
  cycle: number | null;
  chargeId: string;
  createdAt: string;
}

export interface FundingSubscription {
  id: number;
  pool: PoolRef;
  amount: Money;
  showName: boolean;
  status: 'ACTIVE' | 'CANCELLED' | 'ENDED';
  cyclesCharged: number;
  nextChargeAt: string | null;
  createdAt: string;
  cancelledAt: string | null;
  cancelledBy: 'USER' | 'PROVIDER' | 'SYSTEM' | null;
}

export interface ContributeResult {
  contribution: Contribution | null;
  subscription: FundingSubscription | null;
}

export interface MyFunding {
  status: FundingStatus;
  contributions: Contribution[];
  subscriptions: FundingSubscription[];
  openPools: PoolSummary[];
}

export interface FundingAllocation {
  moderator: UserSummary | null;
  policy: AllocationPolicy;
  amount: Money;
  activityScore: number | null;
  reportsResolved: number | null;
  appealsHandled: number | null;
  note: string | null;
  createdAt: string | null;
}

export interface LedgerEntry {
  id: number;
  entryType: 'CONTRIBUTION' | 'ALLOCATION';
  debitAccount: string;
  debitLabel: string;
  creditAccount: string;
  creditLabel: string;
  amount: Money;
  createdAt: string;
}

export interface Reconciliation {
  contributionsTotal: Money;
  ledgerIn: Money;
  ledgerOut: Money;
  allocationsTotal: Money;
  poolBalance: Money;
  reconciles: boolean;
}

export interface TransparencyReport {
  pool: PoolSummary;
  raised: Money;
  contributionCount: number;
  contributorCount: number;
  anonymousContributors: number;
  namedContributors: UserSummary[];
  allocated: Money;
  allocations: FundingAllocation[];
  ledger: LedgerEntry[];
  ledgerTruncated: boolean;
  reconciliation: Reconciliation;
  closedNote: string | null;
}

export interface AllocationPreview {
  policy: AllocationPolicy;
  total: Money;
  allocations: FundingAllocation[];
  note: string | null;
  windowStart: string;
  windowEnd: string;
}

export interface CreatePoolBody {
  title: string;
  purpose: string;
  currency: string;
  goalMinor: number;
  periodEnd: string;
}

export interface ContributeBody {
  amountMinor: number;
  recurring: boolean;
  showName: boolean;
}

export interface CloseBody {
  allocations?: { username: string; amountMinor: number }[];
  note?: string;
}
