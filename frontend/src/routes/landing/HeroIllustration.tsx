import { ArrowBigUp, MessageSquare } from 'lucide-react';

/**
 * Decorative product illustration (aria-hidden). It depicts the layered thread concept;
 * the names and text are illustrative placeholders, not real activity or statistics.
 */
export function HeroIllustration() {
  const layers = [
    { label: 'Thread', tone: 'bg-surface', offset: 'ml-0' },
    { label: 'Arguments', tone: 'bg-surface-2', offset: 'ml-4 sm:ml-8' },
    { label: 'Takeaways', tone: 'bg-surface-2', offset: 'ml-8 sm:ml-16' },
  ];
  return (
    <div aria-hidden="true" className="relative mx-auto w-full max-w-[520px] select-none">
      <div className="rounded-lg border border-border bg-surface p-4 shadow-float">
        <div className="flex items-center gap-2 text-xs text-muted">
          <span className="h-4 w-4 rounded-sm bg-accent/30" />
          <span className="font-medium text-text">c/system_design</span>
          <span>·</span>
          <span>example thread</span>
        </div>
        <p className="mt-2 font-serif text-lg font-semibold leading-snug">
          When does a modular monolith stop being the right call?
        </p>
        <div className="mt-4 space-y-3 border-l border-border pl-3">
          {[
            ['Start with clear module boundaries and extract only what has a different scaling profile.', 'w-24'],
            ['Team ownership is usually the real trigger, not request volume.', 'w-20'],
          ].map(([text, w], i) => (
            <div key={i} className={i ? 'ml-4 border-l border-border pl-3' : ''}>
              <div className="flex items-center gap-2">
                <span className="h-4 w-4 rounded-full bg-surface-2" />
                <span className={`h-2 rounded-full bg-border ${w}`} />
              </div>
              <p className="mt-1.5 text-sm text-muted">{text}</p>
              <div className="mt-1.5 flex items-center gap-3 text-faint">
                <ArrowBigUp size={15} />
                <MessageSquare size={13} />
              </div>
            </div>
          ))}
        </div>
      </div>
      <div className="mt-3 space-y-2">
        {layers.map((l, i) => (
          <div
            key={l.label}
            className={`flex items-center justify-between rounded-md border border-border ${l.tone} px-3 py-2 ${l.offset}`}
            style={{ opacity: 1 - i * 0.18 }}
          >
            <span className="text-xs font-medium text-muted">{l.label}</span>
            <span className="flex gap-1">
              {Array.from({ length: 3 - i }).map((_, j) => (
                <span key={j} className="h-1.5 w-8 rounded-full bg-accent/40" />
              ))}
            </span>
          </div>
        ))}
      </div>
    </div>
  );
}
