import type { PostSummary, UserSummary } from './api';

/** Mirrors backend app.strata.insight / app.strata.collection DTOs (docs/API.md → Knowledge layer). */
export type Stance = 'SUPPORT' | 'OPPOSE' | 'NEUTRAL' | 'QUESTION' | 'EVIDENCE' | 'EXPERIENCE';

export interface ArgItem {
  commentId: number;
  parentId: number | null;
  excerpt: string;
  author: UserSummary | null;
  score: number;
  stance: Stance;
  suggested: boolean;
  myLabel: Stance | null;
  labelCounts: Partial<Record<Stance, number>>;
}

export interface EvidenceLink {
  url: string;
  domain: string;
  commentId: number;
  excerpt: string;
}

export interface MapNode {
  id: number;
  parentId: number | null;
  stance: Stance;
  suggested: boolean;
  score: number;
  label: string;
}

export interface ContextView {
  postId: number;
  title: string;
  excerpt: string;
  stats: {
    analysed: number;
    total: number;
    labelled: number;
    suggested: number;
    participants: number;
    byStance: Record<Stance, number>;
  };
  groups: Record<Stance, ArgItem[]>;
  evidence: EvidenceLink[];
  openQuestions: ArgItem[];
  agreement: ArgItem[];
  disagreement: ArgItem[];
  map: MapNode[];
  truncated: boolean;
}

export type DigestLength = 'short' | 'standard' | 'detailed';
export type DigestFocus = 'balanced' | 'top' | 'new' | 'beginner' | 'technical';
export type LineKind = 'DATA' | 'ANECDOTE' | 'OPINION' | 'SPECULATION' | 'STATEMENT';

export interface DigestLine {
  text: string;
  commentId: number;
  kind: LineKind;
  author: string | null;
}

export interface DigestView {
  postId: number;
  length: DigestLength;
  focus: DigestFocus;
  engine: string;
  engineNote: string;
  generatedAt: string;
  commentCountAtGeneration: number;
  currentCommentCount: number;
  stale: boolean;
  sections: {
    summary: DigestLine[];
    question: string;
    takeaways: DigestLine[];
    supporting: DigestLine[];
    opposing: DigestLine[];
    mostUseful: ArgItem[];
    minority: ArgItem[];
    unresolved: ArgItem[];
    links: EvidenceLink[];
    consensus: { status: 'CONSENSUS' | 'DIVIDED' | 'INSUFFICIENT'; statement: string };
  };
}

export interface Collection {
  id: number;
  name: string;
  description: string;
  shared: boolean;
  shareToken: string | null;
  itemCount: number;
  previewImages: string[];
  updatedAt: string;
}

export interface CollectionItem {
  id: number;
  collectionId: number;
  type: 'POST' | 'COMMENT';
  post: PostSummary;
  comment: { id: number; excerpt: string; author: UserSummary | null; score: number; deleted: boolean } | null;
  note: string;
  tags: string[];
  position: number;
  createdAt: string;
}

export interface SharedCollection {
  collection: Collection;
  owner: UserSummary | null;
  items: CollectionItem[];
}
