// Types mirror docs/API.md (v1). Keep in sync with the backend DTOs.
export type ID = number;

export interface CursorPage<T> {
  items: T[];
  nextCursor: string | null;
}
export interface OffsetPage<T> {
  items: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export interface ApiErrorBody {
  status: number;
  code: string;
  message: string;
  fieldErrors?: Record<string, string> | null;
  traceId?: string;
  timestamp?: string;
}

export interface UserSummary {
  id: ID;
  username: string;
  displayName: string | null;
  avatarUrl: string | null;
}
export interface CommunityRef {
  id: ID;
  slug: string;
  name: string;
  iconUrl: string | null;
}
export interface Topic {
  id: ID;
  slug: string;
  name: string;
  description: string | null;
}
export type Visibility = 'PUBLIC' | 'RESTRICTED' | 'PRIVATE';
export interface CommunitySummary {
  id: ID;
  slug: string;
  name: string;
  description: string | null;
  iconUrl: string | null;
  bannerUrl: string | null;
  topic: Topic | null;
  visibility: Visibility;
  memberCount: number;
  tags: string[];
  isMember: boolean;
}
export interface Flair {
  id: ID;
  text: string;
  color: string;
}
export type CommunityRole = 'OWNER' | 'ADMIN' | 'MODERATOR' | 'CONTRIBUTOR' | 'MEMBER';
export interface CommunityRule {
  id: ID;
  title: string;
  description: string | null;
  position: number;
}
export interface CommunityDetail extends CommunitySummary {
  createdAt: string;
  rules: CommunityRule[];
  flairs: Flair[];
  moderators: { user: UserSummary; role: 'OWNER' | 'ADMIN' | 'MODERATOR' }[];
  viewer: {
    isMember: boolean;
    role: CommunityRole | null;
    canPost: boolean;
    canModerate: boolean;
    canView: boolean;
    banned?: boolean;
    bannedUntil?: string | null;
  };
}
export interface CommunityMember {
  user: UserSummary;
  role: CommunityRole;
  joinedAt: string;
}

export type PostType = 'TEXT' | 'LINK' | 'IMAGE' | 'POLL';
export type VoteValue = -1 | 0 | 1;
export interface PollOption {
  id: ID;
  label: string;
  votes: number;
}
export interface Poll {
  multiple: boolean;
  closesAt: string | null;
  closed: boolean;
  totalVotes: number;
  myOptionIds: ID[];
  options: PollOption[];
}
export interface MediaRef {
  id: ID;
  url: string;
  width: number | null;
  height: number | null;
}
export interface PostSummary {
  id: ID;
  type: PostType;
  title: string;
  bodyPreview: string;
  url: string | null;
  linkDomain: string | null;
  media: MediaRef[];
  poll: Poll | null;
  author: UserSummary | null;
  community: CommunityRef;
  flair: Flair | null;
  tags: string[];
  score: number;
  upvotes: number;
  downvotes: number;
  commentCount: number;
  myVote: VoteValue;
  saved: boolean;
  pinned: boolean;
  locked: boolean;
  removed: boolean;
  createdAt: string;
  editedAt: string | null;
}
export interface PostDetail extends PostSummary {
  bodyHtml: string;
  viewer: { canEdit: boolean; canDelete: boolean; canModerate: boolean };
}
export interface VoteResult {
  score: number;
  upvotes?: number;
  downvotes?: number;
  myVote: VoteValue;
}

export interface CommentNode {
  id: ID;
  postId: ID;
  parentId: ID | null;
  depth: number;
  body: string;
  author: UserSummary | null;
  score: number;
  myVote: VoteValue;
  deleted: boolean;
  removed: boolean;
  createdAt: string;
  editedAt: string | null;
  replyCount: number;
  replies: CommentNode[];
  moreReplies: number;
  /** Author is blocked by the viewer; body is hidden server-side. */
  blocked?: boolean;
}
export interface UserComment {
  comment: CommentNode;
  post: { id: ID; title: string; community: CommunityRef };
}

export interface UserSettings {
  theme: 'dark' | 'light' | 'system';
  showNsfw: boolean;
  profilePublic: boolean;
  showCommunities: boolean;
  emailNotifications: boolean;
}
export interface Me {
  id: ID;
  username: string;
  email: string;
  displayName: string | null;
  avatarUrl: string | null;
  bannerUrl: string | null;
  bio: string | null;
  emailVerified: boolean;
  onboardingCompleted: boolean;
  role: 'USER' | 'ADMIN';
  postKarma: number;
  commentKarma: number;
  createdAt: string;
  settings: UserSettings;
}
export interface PublicProfile {
  id: ID;
  username: string;
  displayName: string | null;
  avatarUrl: string | null;
  bannerUrl: string | null;
  bio: string | null;
  createdAt: string;
  postKarma: number;
  commentKarma: number;
  followerCount: number;
  followingCount: number;
  isFollowing: boolean;
  isSelf: boolean;
  isBlocked?: boolean;
  communities: CommunityRef[];
  moderatorOf: CommunityRef[];
}
export interface AuthResponse {
  accessToken: string;
  expiresIn: number;
  user: Me;
}
export interface SessionInfo {
  id: ID;
  userAgent: string | null;
  ip: string | null;
  createdAt: string;
  lastUsedAt: string | null;
  current: boolean;
}

export type NotificationType =
  | 'POST_REPLY'
  | 'COMMENT_REPLY'
  | 'MENTION'
  | 'NEW_FOLLOWER'
  | 'POST_MILESTONE'
  | 'COMMUNITY_ROLE'
  | 'CONTENT_REMOVED'
  | 'BANNED'
  | 'APPEAL_RESOLVED'
  | 'PULSE_RESULT';
export interface AppNotification {
  id: ID;
  type: NotificationType;
  actor: UserSummary | null;
  message: string;
  link: string;
  read: boolean;
  createdAt: string;
  count: number;
}

export interface SearchSuggest {
  communities: CommunityRef[];
  users: UserSummary[];
  posts: { id: ID; title: string; community: CommunityRef }[];
}

export type FeedTab = 'foryou' | 'following' | 'latest' | 'popular' | 'rising' | 'top';
export type TimeRange = 'day' | 'week' | 'month' | 'year' | 'all';
export type CommunitySort = 'hot' | 'new' | 'top' | 'rising';
export type CommentSort = 'best' | 'top' | 'new' | 'controversial' | 'old';
export type SearchType = 'posts' | 'communities' | 'users';
