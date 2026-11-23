import type { ID, MediaRef, UserSummary } from './api';

/**
 * A DM image. `signed` URLs (images sent since D-024) work only for conversation participants and expire after
 * 30 to 60 minutes; refetch the messages for fresh ones. Older attachments have a public, unsigned URL.
 */
export interface DmMedia extends MediaRef {
  signed: boolean;
  expiresAt: string | null;
}

export type DmPolicy = 'EVERYONE' | 'FOLLOWING' | 'NOBODY';

export interface DmMessage {
  id: ID;
  conversationId: ID;
  senderId: ID;
  /** Plain text, ≤4000 chars; "" when deleted. */
  body: string;
  media: DmMedia[];
  createdAt: string;
  deleted: boolean;
}

export interface ConversationSummary {
  id: ID;
  /** null when the other account was deleted. */
  other: UserSummary | null;
  lastMessage: { id: ID; senderId: ID; excerpt: string; createdAt: string; deleted: boolean } | null;
  unreadCount: number;
  otherLastReadMessageId: ID | null;
  online: boolean;
  canMessage: boolean;
  cannotMessageReason: string | null;
  updatedAt: string;
}

export interface MessagePage {
  /** Oldest → newest. */
  items: DmMessage[];
  hasMore: boolean;
}

/** Frames on `/user/queue/events`. */
export type RealtimeEvent =
  | { type: 'MESSAGE'; payload: DmMessage }
  | { type: 'MESSAGE_DELETED'; payload: { conversationId: ID; messageId: ID } }
  | { type: 'READ'; payload: { conversationId: ID; userId: ID; messageId: ID } }
  | { type: 'TYPING'; payload: { conversationId: ID; userId: ID } }
  | { type: 'PRESENCE'; payload: { userId: ID; online: boolean } }
  | { type: 'NOTIFICATION'; payload: { unreadCount: number } }
  | { type: 'UNREAD_MESSAGES'; payload: { count: number } };
