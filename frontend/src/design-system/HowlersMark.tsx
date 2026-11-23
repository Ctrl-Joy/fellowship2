import { useId } from 'react';

/**
 * Howlers brand mark for the public pages: a moonlit pup head on a night tile, with the wordmark in the display font.
 * The app-wide rename (brand.ts, BrandMark) is a separate change; the public header and footer use this already.
 */
export const HOWLERS = {
  name: 'Howlers',
  tagline: 'Find your pack & howl together',
} as const;

export function HowlersMark({ size = 30, withName = false }: { size?: number; withName?: boolean }) {
  const gid = `howlers-mark-${useId().replace(/:/g, '')}`;
  return (
    <span className="inline-flex items-center gap-2.5">
      <svg width={size} height={size} viewBox="0 0 32 32" aria-hidden="true" focusable="false">
        <defs>
          <linearGradient id={gid} x1="0" y1="0" x2="1" y2="1">
            <stop offset="0" stopColor="#2a1b63" />
            <stop offset="1" stopColor="#5a46cc" />
          </linearGradient>
        </defs>
        <rect width="32" height="32" rx="9" fill={`url(#${gid})`} />
        <circle cx="23.5" cy="8.5" r="3.2" fill="#ffe9b0" />
        <path d="M8.2 15.5 L9 7.6 L13.4 11.6 Z M23.8 15.5 L23 7.6 L18.6 11.6 Z" fill="#c9d3f7" />
        <ellipse cx="16" cy="18" rx="8.6" ry="7.6" fill="#c9d3f7" />
        <ellipse cx="16" cy="21" rx="4.4" ry="3.4" fill="#fff" />
        <circle cx="12.6" cy="17" r="1.25" fill="#1d1840" />
        <circle cx="19.4" cy="17" r="1.25" fill="#1d1840" />
        <ellipse cx="16" cy="19.9" rx="1.5" ry="1" fill="#1d1840" />
      </svg>
      {withName ? (
        <span className="font-display text-[17px] font-extrabold uppercase tracking-tight text-text">{HOWLERS.name}</span>
      ) : (
        <span className="sr-only">{HOWLERS.name}</span>
      )}
    </span>
  );
}
