import type { CommentNode } from '@/types/api';

/** Pure helpers for immutable updates to a lazily-loaded comment forest. */

export function mapTree(nodes: CommentNode[], id: number, fn: (n: CommentNode) => CommentNode): CommentNode[] {
  let changed = false;
  const out = nodes.map((n) => {
    if (n.id === id) {
      changed = true;
      return fn(n);
    }
    if (n.replies.length) {
      const replies = mapTree(n.replies, id, fn);
      if (replies !== n.replies) {
        changed = true;
        return { ...n, replies };
      }
    }
    return n;
  });
  return changed ? out : nodes;
}

export function findNode(nodes: CommentNode[], id: number): CommentNode | null {
  for (const n of nodes) {
    if (n.id === id) return n;
    const hit = findNode(n.replies, id);
    if (hit) return hit;
  }
  return null;
}

/** Adds a freshly created reply as the first child of its parent (or top-level when parentId is null). */
export function insertReply(nodes: CommentNode[], reply: CommentNode): CommentNode[] {
  if (reply.parentId == null) return [reply, ...nodes];
  return mapTree(nodes, reply.parentId, (p) => ({
    ...p,
    replies: [reply, ...p.replies],
    replyCount: p.replyCount + 1,
  }));
}

/** Appends a page of lazily-loaded replies, skipping ones already present, and shrinks `moreReplies`. */
export function appendReplies(nodes: CommentNode[], parentId: number, page: CommentNode[], hasMore: boolean): CommentNode[] {
  return mapTree(nodes, parentId, (p) => {
    const seen = new Set(p.replies.map((r) => r.id));
    const fresh = page.filter((r) => !seen.has(r.id));
    const replies = [...p.replies, ...fresh];
    return { ...p, replies, moreReplies: hasMore ? Math.max(0, p.replyCount - replies.length) : 0 };
  });
}

export function markDeleted(nodes: CommentNode[], id: number): CommentNode[] {
  return mapTree(nodes, id, (n) => ({ ...n, deleted: true, body: '', author: null }));
}

/** Number of comments loaded in this forest (including nested). */
export function countLoaded(nodes: CommentNode[]): number {
  return nodes.reduce((acc, n) => acc + 1 + countLoaded(n.replies), 0);
}
