import { brand } from './brand';

/** Logo mark: three stacked strata lines of decreasing length. */
export function BrandMark({ size = 24, withName = false }: { size?: number; withName?: boolean }) {
  return (
    <span className="inline-flex items-center gap-2">
      <svg width={size} height={size} viewBox="0 0 32 32" aria-hidden="true">
        <rect width="32" height="32" rx="7" className="fill-surface-2" />
        <path d="M7 11h18M7 16h13M7 21h8" className="stroke-accent" strokeWidth="3" strokeLinecap="round" />
      </svg>
      {withName && <span className="font-serif text-lg font-semibold tracking-tight text-text">{brand.name}</span>}
      {!withName && <span className="sr-only">{brand.name}</span>}
    </span>
  );
}
