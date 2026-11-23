import { BookOpenText, GitBranch, HandCoins, Headphones, Sparkles, type LucideIcon } from 'lucide-react';

interface Feature {
  icon: LucideIcon;
  kicker: string;
  title: string;
  body: string;
  points: string[];
}

const FEATURES: Feature[] = [
  {
    icon: GitBranch,
    kicker: 'Context Mode',
    title: 'See the shape of an argument, not just its length.',
    body: 'Long or heated threads can be viewed as a map: the central claim, the strongest supporting and opposing points, evidence people cited, and the questions still open.',
    points: ['Links back to every original comment', 'Minority viewpoints preserved', 'Labels you can correct'],
  },
  {
    icon: Sparkles,
    kicker: 'Community AI',
    title: 'Ask the community, get answers with sources.',
    body: 'A retrieval-grounded assistant that searches posts and comments you are allowed to see, separates opinion from fact, and cites exactly where each claim came from.',
    points: ['Citations to real posts', 'Says when sources are thin', 'Respects private communities'],
  },
  {
    icon: BookOpenText,
    kicker: 'Thread Digest & Collections',
    title: 'Read a thousand comments in two minutes. Keep the good ones.',
    body: 'Digests summarise conclusions, disagreements and useful links. Collections turn saved posts into a personal library with notes and tags.',
    points: ['Never claims false consensus', 'Shows when it was generated', 'Private by default'],
  },
  {
    icon: Headphones,
    kicker: 'Community podcasts',
    title: 'Long-form episodes from the best discussions.',
    body: 'Deep dives, debates and monthly roundups assembled from community threads — with transcripts, chapters and every source linked.',
    points: ['Human review before publishing', 'Transcript search', 'Source discussions attached'],
  },
  {
    icon: HandCoins,
    kicker: 'Moderator support',
    title: 'The people who keep communities healthy deserve recognition.',
    body: 'Voluntary, transparent contributions to a community’s moderator pool, plus a leaderboard that rewards consistency and care — never raw ban counts.',
    points: ['Public allocation rules', 'Quality-weighted recognition', 'Disabled until payments are compliant'],
  },
];

export function FeatureSections() {
  return (
    <section aria-labelledby="roadmap" className="border-b border-border">
      <div className="mx-auto max-w-6xl px-4 py-16 md:px-6">
        <p className="text-xs font-semibold uppercase tracking-wider text-accent">On the roadmap</p>
        <h2 id="roadmap" className="mt-2 max-w-2xl font-serif text-3xl font-semibold tracking-tight">
          Built to make discussions more useful after they happen.
        </h2>
        <p className="mt-3 max-w-2xl text-muted">
          The core community platform is live today. These capabilities are being built in phases and are clearly marked
          in the app until they ship.
        </p>
        <div className="mt-12 divide-y divide-border border-y border-border">
          {FEATURES.map(({ icon: Icon, kicker, title, body, points }) => (
            <article key={kicker} className="grid gap-4 py-8 md:grid-cols-[220px_1fr_240px] md:gap-8">
              <div className="flex items-center gap-2.5 text-sm font-medium text-muted md:items-start">
                <Icon size={18} className="text-accent" /> {kicker}
              </div>
              <div>
                <h3 className="font-serif text-xl font-semibold leading-snug">{title}</h3>
                <p className="mt-2 text-[15px] leading-relaxed text-muted">{body}</p>
              </div>
              <ul className="space-y-1.5 text-sm text-muted">
                {points.map((p) => (
                  <li key={p} className="flex gap-2"><span className="mt-2 h-1 w-3 shrink-0 rounded-full bg-accent/60" aria-hidden="true" />{p}</li>
                ))}
              </ul>
            </article>
          ))}
        </div>
      </div>
    </section>
  );
}
