import type { CommunityRef, UserSummary } from './api';

/** Mirrors docs/API.md → "Podcasts (Phase 6)". */
export type EpisodeType =
  | 'WEEKLY_ROUNDUP'
  | 'DEEP_DIVE'
  | 'DEBATE'
  | 'INTERVIEW'
  | 'BEGINNER_GUIDE'
  | 'MONTHLY_REPORT'
  | 'COMPARISON'
  | 'HIGHLIGHTS'
  | 'MULTI_PERSPECTIVE';

export type EpisodeStatus = 'DRAFT' | 'IN_REVIEW' | 'PUBLISHED' | 'ARCHIVED';
export type MediaKind = 'AUDIO' | 'VIDEO';
export type ScriptStatus = 'NONE' | 'GENERATING' | 'READY' | 'FAILED';

export interface EpisodeProgress {
  positionSeconds: number;
  completed: boolean;
}

export interface EpisodeSummary {
  id: number;
  community: CommunityRef;
  title: string;
  summary: string;
  type: EpisodeType;
  mediaKind: MediaKind | null;
  durationSeconds: number | null;
  thumbnailUrl: string | null;
  status: EpisodeStatus;
  publishedAt: string | null;
  createdAt: string;
  playCount: number;
  saved: boolean;
  progress: EpisodeProgress | null;
  chapterCount: number;
}

export interface Chapter {
  id: number;
  startSeconds: number;
  title: string;
}

export interface EpisodeSource {
  position: number;
  postId: number;
  commentId: number | null;
  title: string;
  communitySlug: string;
  excerpt: string;
  link: string;
}

export interface EpisodeDetail extends EpisodeSummary {
  mediaUrl: string | null;
  mediaContentType: string | null;
  allowDownload: boolean;
  chapters: Chapter[];
  sources: EpisodeSource[];
  hasTranscript: boolean;
  transcriptVttUrl: string | null;
  script: string | null;
  scriptStatus: ScriptStatus;
  scriptSource: 'HUMAN' | 'AI_DRAFT' | null;
  scriptError: string | null;
  createdBy: UserSummary | null;
  publishedBy: UserSummary | null;
  viewer: { canManage: boolean };
}

export interface TranscriptSegment {
  id: number;
  startSeconds: number;
  endSeconds: number;
  speaker: string | null;
  text: string;
}

export interface PodcastCapabilities {
  aiScriptDrafting: boolean;
  audioGeneration: false;
  videoGeneration: false;
  maxUploadMb: number;
  audioTypes: string[];
  videoTypes: string[];
}

export interface PodcastHome {
  featured: EpisodeSummary[];
  continueListening: EpisodeSummary[];
  recent: EpisodeSummary[];
  saved: EpisodeSummary[];
}

export interface SourceRef {
  postId: number;
  commentId?: number;
}

export interface EpisodePatch {
  title?: string;
  summary?: string;
  type?: EpisodeType;
  allowDownload?: boolean;
  script?: string;
  chapters?: { startSeconds: number; title: string }[];
  sources?: SourceRef[];
}

export type TranscriptFormat = 'vtt' | 'srt' | 'timestamped';
