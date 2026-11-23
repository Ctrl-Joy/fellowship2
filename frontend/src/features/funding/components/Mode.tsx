import { FlaskConical, PowerOff } from 'lucide-react';
import type { FundingStatus, Money } from '@/types/funding';
import { cn } from '@/utils/format';
import { DEMO_LABEL, formatMoney } from '../money';

/** The badge that sits next to every demo amount. `full` spells out "DEMO, no real money". */
export function DemoBadge({ full = false, className }: { full?: boolean; className?: string }) {
  return (
    <span
      title={DEMO_LABEL}
      className={cn(
        'inline-flex h-5 shrink-0 items-center rounded-sm border border-warning/40 bg-warning/15 px-1.5 text-[10px] font-bold uppercase leading-none tracking-wide text-warning',
        className,
      )}
    >
      {full ? DEMO_LABEL : 'Demo'}
      {!full && <span className="sr-only">, no real money</span>}
    </span>
  );
}

/** An amount with its demo label. Use this for every amount on every funding screen. */
export function MoneyAmount({ m, full = false, className }: { m: Money; full?: boolean; className?: string }) {
  return (
    <span className={cn('inline-flex flex-wrap items-center gap-1.5', className)}>
      <span className="font-semibold tabular-nums">{formatMoney(m)}</span>
      {m.demo && <DemoBadge full={full} />}
    </span>
  );
}

/** Page-level banner: impossible to miss, shown above every funding view. */
export function DemoBanner({ status }: { status: FundingStatus | undefined }) {
  if (status && !status.enabled) {
    return (
      <div role="note" className="flex items-start gap-3 rounded-2xl border border-border bg-surface-2/60 px-4 py-3 text-sm">
        <PowerOff size={18} className="mt-0.5 shrink-0 text-muted" aria-hidden="true" />
        <div>
          <p className="font-semibold">Moderator funding is turned off on this server</p>
          <p className="text-muted">{status.notice ?? 'Existing reports stay readable; nothing can be contributed.'}</p>
        </div>
      </div>
    );
  }
  return (
    <div role="note" aria-label={DEMO_LABEL} className="flex items-start gap-3 rounded-2xl border-2 border-warning/50 bg-warning/10 px-4 py-3 text-sm">
      <FlaskConical size={18} className="mt-0.5 shrink-0 text-warning" aria-hidden="true" />
      <div>
        <p className="font-bold uppercase tracking-wide text-warning">{DEMO_LABEL}</p>
        <p className="text-text">
          {status?.notice ??
            'Demo mode: contributions are simulated, always succeed, and no real money moves. Nothing is charged and nobody is paid.'}
        </p>
      </div>
    </div>
  );
}
