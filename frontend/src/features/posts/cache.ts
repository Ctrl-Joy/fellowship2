import type { InfiniteData, QueryClient, QueryKey } from '@tanstack/react-query';
import { qk } from '@/services/queryKeys';
import type { CursorPage, PostDetail, PostSummary } from '@/types/api';

type PostPatch = (p: PostSummary) => PostSummary | null;

/** Every cache root that may hold PostSummary lists. */
const LIST_ROOTS: QueryKey[] = [qk.posts, ['users'], ['search']];

function isInfinite(d: unknown): d is InfiniteData<CursorPage<PostSummary>> {
  return !!d && typeof d === 'object' && Array.isArray((d as InfiniteData<unknown>).pages);
}

function isPost(x: unknown): x is PostSummary {
  return !!x && typeof x === 'object' && 'title' in x && 'community' in x && 'myVote' in x;
}

function patchList(items: unknown[], id: number, patch: PostPatch): unknown[] {
  return items.flatMap((p) => (isPost(p) && p.id === id ? (patch(p) ?? []) : [p]));
}

/**
 * Applies `patch` to a post wherever it is cached (feeds, community lists, trending, saved,
 * profile, search, detail). Returning null removes the post from lists (hide/delete).
 */
export function patchPostEverywhere(qc: QueryClient, id: number, patch: PostPatch) {
  for (const root of LIST_ROOTS) {
    for (const [key, data] of qc.getQueriesData<unknown>({ queryKey: root })) {
      if (isInfinite(data)) {
        qc.setQueryData(key, {
          ...data,
          pages: data.pages.map((pg) => ({ ...pg, items: patchList(pg.items, id, patch) })),
        });
      } else if (Array.isArray(data)) {
        qc.setQueryData(key, patchList(data, id, patch));
      }
    }
  }
  const detail = qc.getQueryData<PostDetail>(qk.post(id));
  if (detail) {
    const next = patch(detail);
    if (next) qc.setQueryData<PostDetail>(qk.post(id), { ...detail, ...next });
  }
}

export function snapshotPosts(qc: QueryClient) {
  return [...LIST_ROOTS, ['post']].flatMap((root) => qc.getQueriesData<unknown>({ queryKey: root }));
}

export function restoreSnapshot(qc: QueryClient, snap: ReturnType<typeof snapshotPosts>) {
  for (const [key, data] of snap) qc.setQueryData(key, data);
}
