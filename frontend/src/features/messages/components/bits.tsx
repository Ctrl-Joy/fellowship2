import { useRealtimeStatus } from '@/features/realtime/RealtimeProvider';
import { cn } from '@/utils/format';

export function OnlineDot({ className }: { className?: string }) {
  return (
    <span className={cn('block h-3 w-3 rounded-full border-2 border-surface bg-accent', className)} role="img" aria-label="Online" title="Online" />
  );
}

/** Small, unobtrusive hint when the live connection isn't up. Hidden when connected. */
export function ConnectionHint({ className }: { className?: string }) {
  const status = useRealtimeStatus();
  if (status === 'connected' || status === 'idle') return null;
  return (
    <span role="status" className={cn('inline-flex items-center gap-1.5 text-xs text-muted', className)}>
      <span className="h-1.5 w-1.5 animate-pulse rounded-full bg-warning" aria-hidden="true" />
      {status === 'connecting' ? 'Connecting…' : 'Reconnecting…'}
    </span>
  );
}
