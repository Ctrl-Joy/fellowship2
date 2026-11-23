import { ArrowRight } from 'lucide-react';
import { Link } from 'react-router-dom';
import { Avatar, Skeleton } from '@/components/ui/Display';
import { useCommunities } from '@/features/communities/hooks';
import { pluralize } from '@/utils/format';

/** Real communities from the API — no invented numbers on the landing page. */
export function LiveCommunities() {
  const q = useCommunities({ sort: 'popular', page: 0, size: 6 });
  if (q.isError || (q.data && q.data.items.length === 0)) return null;
  return (
    <section aria-labelledby="live" className="border-b border-border">
      <div className="mx-auto max-w-6xl px-4 py-16 md:px-6">
        <div className="flex flex-wrap items-end justify-between gap-4">
          <div>
            <h2 id="live" className="font-serif text-3xl font-semibold tracking-tight">Find your people</h2>
            <p className="mt-2 text-muted">A few of the communities on this instance right now.</p>
          </div>
          <Link to="/communities" className="inline-flex items-center gap-1.5 text-sm font-medium text-accent hover:underline">
            All communities <ArrowRight size={15} />
          </Link>
        </div>
        <div className="mt-8 grid gap-3 sm:grid-cols-2 lg:grid-cols-3">
          {q.isPending
            ? Array.from({ length: 6 }).map((_, i) => <Skeleton key={i} className="h-28" />)
            : q.data.items.map((c) => (
                <Link key={c.id} to={`/c/${c.slug}`} className="group rounded-lg border border-border bg-surface p-4 transition-colors hover:border-accent/50">
                  <div className="flex items-center gap-3">
                    <Avatar src={c.iconUrl} name={c.slug} square />
                    <div className="min-w-0">
                      <div className="truncate font-semibold group-hover:underline">c/{c.slug}</div>
                      <div className="text-xs text-faint">{pluralize(c.memberCount, 'member')}{c.topic ? ` · ${c.topic.name}` : ''}</div>
                    </div>
                  </div>
                  {c.description && <p className="mt-3 line-clamp-2 text-sm text-muted">{c.description}</p>}
                </Link>
              ))}
        </div>
      </div>
    </section>
  );
}
