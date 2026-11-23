import { Clock, Flame, Sparkles, TrendingUp, Trophy, Users } from 'lucide-react';
import { Link, useSearchParams } from 'react-router-dom';
import { buttonClasses } from '@/components/ui/Button';
import { useAuth } from '@/features/auth/hooks';
import { FeedControls, SortMenu } from '@/features/posts/components/FeedControls';
import { PostList } from '@/features/posts/components/PostList';
import { useFeed } from '@/features/posts/hooks';
import { PageColumns } from '@/layouts/PageColumns';
import { RightRail } from '@/layouts/RightRail';
import type { FeedTab, TimeRange } from '@/types/api';
import { useDocumentTitle } from '@/hooks/useDocumentTitle';

const TABS: { value: FeedTab; label: string; icon: JSX.Element; auth?: boolean }[] = [
  { value: 'foryou', label: 'Best', icon: <Sparkles size={15} />, auth: true },
  { value: 'popular', label: 'Hot', icon: <Flame size={15} /> },
  { value: 'latest', label: 'New', icon: <Clock size={15} /> },
  { value: 'following', label: 'Following', icon: <Users size={15} />, auth: true },
  { value: 'rising', label: 'Rising', icon: <TrendingUp size={15} /> },
  { value: 'top', label: 'Top', icon: <Trophy size={15} /> },
];
const RANGES: { value: TimeRange; label: string }[] = [
  { value: 'day', label: 'Today' },
  { value: 'week', label: 'This week' },
  { value: 'month', label: 'This month' },
  { value: 'year', label: 'This year' },
  { value: 'all', label: 'All time' },
];

const EMPTY: Record<FeedTab, { title: string; description: string }> = {
  foryou: { title: 'Your feed is quiet', description: 'Join a few communities or follow people and their best posts will show up here.' },
  following: { title: 'Nothing from people you follow', description: 'Follow members whose posts you enjoy — their new posts appear here first.' },
  latest: { title: 'No posts yet', description: 'Be the first to start a discussion.' },
  popular: { title: 'Nothing popular yet', description: 'Posts with strong engagement appear here.' },
  rising: { title: 'Nothing rising right now', description: 'Fresh posts gaining traction appear here.' },
  top: { title: 'No top posts in this range', description: 'Try a longer time range.' },
};

export default function HomePage() {
  const { isAuthed } = useAuth();
  const [params, setParams] = useSearchParams();
  const tabs = TABS.filter((t) => !t.auth || isAuthed);
  const requested = params.get('tab') as FeedTab | null;
  const tab: FeedTab = tabs.some((t) => t.value === requested) ? requested! : isAuthed ? 'foryou' : 'popular';
  const t = (params.get('t') as TimeRange) || 'week';
  const feed = useFeed(tab, t);
  useDocumentTitle(tab === 'popular' ? 'Popular' : 'Home');

  const set = (patch: Record<string, string>) => {
    const next = new URLSearchParams(params);
    Object.entries(patch).forEach(([k, v]) => next.set(k, v));
    setParams(next, { replace: true });
  };

  return (
    <PageColumns rail={<RightRail />}>
      <h1 className="sr-only">{tab === 'popular' ? 'Popular posts' : 'Home feed'}</h1>
      <FeedControls>
        <SortMenu title="Sort by" value={tab} options={tabs} onChange={(v) => set({ tab: v })} />
        {tab === 'top' && <SortMenu title="Top posts from" value={t} options={RANGES} onChange={(v) => set({ t: v })} />}
      </FeedControls>
      <PostList
        query={feed}
        empty={{
          ...EMPTY[tab],
          action:
            tab === 'foryou' || tab === 'following' ? (
              <Link to="/communities" className={buttonClasses('primary', 'sm')}>Find communities</Link>
            ) : isAuthed ? (
              <Link to="/submit" className={buttonClasses('primary', 'sm')}>Create a post</Link>
            ) : undefined,
        }}
      />
    </PageColumns>
  );
}
