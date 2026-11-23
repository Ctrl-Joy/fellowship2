# Strata API — v1 contract

Base path: `/api/v1`. JSON everywhere. OpenAPI UI at `/swagger-ui.html` (JSON at `/v3/api-docs`).

## Conventions

**Auth.** `Authorization: Bearer <accessToken>` (JWT, 15 min). The refresh token is an
`HttpOnly; Secure(prod); SameSite=Strict` cookie named `strata_rt`, scoped to `/api/v1/auth`,
rotated on every refresh. Reuse of a rotated refresh token revokes the whole session family.
Frontend keeps the access token **in memory only** and calls `POST /auth/refresh` on boot and on 401.
All requests use `credentials: "include"`.

**Success responses** return the DTO directly (no envelope).

**Errors** (all 4xx/5xx):
```json
{ "status": 400, "code": "VALIDATION_FAILED", "message": "Human readable",
  "fieldErrors": { "username": "must be 3-21 characters" }, "traceId": "c0ffee...", "timestamp": "..." }
```
Codes: `VALIDATION_FAILED, UNAUTHORIZED, FORBIDDEN, NOT_FOUND, CONFLICT, RATE_LIMITED, BAD_REQUEST, INTERNAL_ERROR`.
Every response carries `X-Trace-Id`.

**Pagination.**
- Cursor (feeds, comments, notifications, search posts): `?cursor=<opaque>&limit=25` →
  `{ "items": [...], "nextCursor": "opaque" | null }`
- Offset (directories, admin lists): `?page=0&size=20` →
  `{ "items": [...], "page": 0, "size": 20, "totalElements": 120, "totalPages": 6 }`

**Timestamps** ISO-8601 UTC strings. **IDs** are numbers (bigint).

## Shared DTOs

```ts
UserSummary   { id, username, displayName, avatarUrl|null }
CommunityRef  { id, slug, name, iconUrl|null }
CommunitySummary { id, slug, name, description, iconUrl, bannerUrl, topic: Topic|null,
                   visibility: "PUBLIC"|"RESTRICTED"|"PRIVATE", memberCount, tags: string[], isMember }
Topic { id, slug, name, description }
Flair { id, text, color }      // color = hex like "#3FB68B"
PostSummary {
  id, type: "TEXT"|"LINK"|"IMAGE"|"POLL", title, bodyPreview: string /* plain text ≤300 */,
  url|null, linkDomain|null, media: { id, url, width|null, height|null }[],
  poll: Poll|null, author: UserSummary|null /* null = deleted account */, community: CommunityRef,
  flair: Flair|null, tags: string[], score, upvotes, downvotes, commentCount,
  myVote: -1|0|1, saved: boolean, pinned, locked, removed, createdAt, editedAt|null
}
PostDetail = PostSummary & { bodyHtml: string /* sanitized server-side */, viewer: { canEdit, canDelete, canModerate } }
Poll { multiple: boolean, closesAt|null, closed: boolean, totalVotes, myOptionIds: number[],
       options: { id, label, votes }[] }
CommentNode {
  id, postId, parentId|null, depth, body /* markdown source; render without raw HTML */,
  author: UserSummary|null, score, myVote, deleted: boolean, removed: boolean,
  createdAt, editedAt|null, replyCount, replies: CommentNode[], moreReplies: number /* not yet loaded */
}
```

## Auth `/auth`
| Method | Path | Body | Returns |
|---|---|---|---|
| POST | `/auth/register` | `{username, email, password, displayName?}` | `AuthResponse` + cookie |
| POST | `/auth/login` | `{login /*username or email*/, password}` | `AuthResponse` + cookie |
| POST | `/auth/refresh` | – (cookie) | `AuthResponse` + rotated cookie |
| POST | `/auth/logout` | – | 204, revokes current session |
| GET | `/auth/username-available?username=` | – | `{available, reason|null}` |
| POST | `/auth/verify-email` | `{token}` | 204 |
| POST | `/auth/resend-verification` | – (auth) | 204 |
| POST | `/auth/forgot-password` | `{email}` | 204 (always) |
| POST | `/auth/reset-password` | `{token, newPassword}` | 204, revokes all sessions |
| GET | `/auth/sessions` | – | `{id, userAgent, ip, createdAt, lastUsedAt, current}[]` |
| DELETE | `/auth/sessions/{id}` | – | 204 |

`AuthResponse { accessToken, expiresIn, user: Me }`. Password rules: 8–128 chars, ≥1 letter and ≥1 digit.
Username: `^[A-Za-z0-9_]{3,21}$`, case-insensitive unique.
Dev mode: verification/reset emails are written to the backend log (`MAIL-DEV`) instead of sent.

## Users
```ts
Me { id, username, email, displayName, avatarUrl, bannerUrl, bio, emailVerified, onboardingCompleted,
     role: "USER"|"ADMIN", postKarma, commentKarma, createdAt,
     settings: { theme: "dark"|"light"|"system", showNsfw, profilePublic, showCommunities, emailNotifications } }
PublicProfile { id, username, displayName, avatarUrl, bannerUrl, bio, createdAt, postKarma, commentKarma,
     followerCount, followingCount, isFollowing, isSelf, communities: CommunityRef[] /* if visible */,
     moderatorOf: CommunityRef[] }
```
| Method | Path | Notes |
|---|---|---|
| GET | `/users/me` | `Me` |
| PATCH | `/users/me` | `{displayName?, bio?}` → `Me` |
| PATCH | `/users/me/settings` | partial settings → `Me` |
| POST | `/users/me/avatar` | multipart `file` (png/jpeg/webp/gif ≤ 5 MB) → `Me` |
| POST | `/users/me/banner` | multipart `file` → `Me` |
| POST | `/users/me/password` | `{currentPassword, newPassword}` → 204 |
| POST | `/users/me/deactivate` | `{password}` → 204 (reversible by logging in) |
| DELETE | `/users/me` | `{password}` → 204 (anonymizes; content shows `author: null`) |
| GET | `/users/{username}` | `PublicProfile` (404 if deleted; limited if private) |
| GET | `/users/{username}/posts` | cursor `PostSummary` |
| GET | `/users/{username}/comments` | cursor `{comment: CommentNode, post: {id, title, community: CommunityRef}}` |
| POST / DELETE | `/users/{username}/follow` | 204 |

## Topics & onboarding
| GET | `/topics` | `Topic[]` |
|---|---|---|
| GET | `/onboarding/suggestions?topicIds=1,2` | `{communities: CommunitySummary[], users: UserSummary[]}` |
| POST | `/onboarding/complete` | `{topicIds: number[], communitySlugs: string[], followUsernames: string[], theme?}` → `Me` |

## Communities
```ts
CommunityDetail = CommunitySummary & {
  createdAt, rules: { id, title, description, position }[], flairs: Flair[],
  moderators: { user: UserSummary, role: "OWNER"|"ADMIN"|"MODERATOR" }[],
  viewer: { isMember, role: "OWNER"|"ADMIN"|"MODERATOR"|"CONTRIBUTOR"|"MEMBER"|null,
            canPost, canModerate, canView }
}
```
| Method | Path | Notes |
|---|---|---|
| GET | `/communities?q=&topic=<slug>&sort=popular\|new&page&size` | offset page of `CommunitySummary` |
| GET | `/communities/trending?limit=5` | `CommunitySummary[]` (most new members/posts last 7 days) |
| GET | `/me/communities` | `CommunitySummary[]` joined |
| POST | `/communities` | `{slug, name, description, topicId?, visibility, tags[], rules:[{title, description}]}` → `CommunityDetail` |
| GET | `/communities/{slug}` | `CommunityDetail` (PRIVATE + non-member → limited, `canView:false`) |
| PATCH | `/communities/{slug}` | mods: `{name?, description?, topicId?, visibility?, tags?}` |
| PUT | `/communities/{slug}/rules` | mods: full replacement list |
| PUT | `/communities/{slug}/flairs` | mods: `{id?, text, color}[]` |
| POST | `/communities/{slug}/icon` \| `/banner` | mods, multipart |
| POST / DELETE | `/communities/{slug}/membership` | join / leave (owner cannot leave) |
| GET | `/communities/{slug}/members?page&size` | `{user: UserSummary, role, joinedAt}` page |
| PUT | `/communities/{slug}/members/{username}/role` | owner/admin: `{role}` |

Slug: `^[a-z0-9_]{3,24}$`, unique. Restricted: anyone views, only CONTRIBUTOR+ posts. Private: members only.

## Posts & feeds
| Method | Path | Notes |
|---|---|---|
| GET | `/feed?tab=foryou\|following\|latest\|popular\|rising\|top&t=day\|week\|month\|year\|all&cursor&limit` | cursor `PostSummary`; anonymous users get `popular` for `foryou`/`following` |
| GET | `/communities/{slug}/posts?sort=hot\|new\|top\|rising&t=&cursor` | pinned posts first on first page for `hot` |
| GET | `/trending/posts?limit=5` | `PostSummary[]` |
| POST | `/posts` | `{communitySlug, type, title, bodyHtml?, url?, mediaIds?: number[], flairId?, tags?: string[], poll?: {options: string[], multiple, closesAt?}}` → `PostDetail` |
| GET | `/posts/{id}` | `PostDetail` |
| PATCH | `/posts/{id}` | author: `{title?, bodyHtml?, tags?, flairId?}` |
| DELETE | `/posts/{id}` | author (soft delete) |
| POST | `/posts/{id}/vote` | `{value: -1\|0\|1}` → `{score, upvotes, downvotes, myVote}` |
| PUT / DELETE | `/posts/{id}/save` | 204 |
| PUT / DELETE | `/posts/{id}/hide` | 204 |
| POST | `/posts/{id}/poll/vote` | `{optionIds: number[]}` → `Poll` |
| GET | `/me/saved?cursor` | cursor `PostSummary` |
| POST | `/media` | multipart `file` image ≤ 10 MB → `{id, url, width, height, contentType}` |
| GET | `/files/{key}` | serves stored media (local storage driver) |

Title 1–300 chars. Body HTML ≤ 40 000 chars after sanitization. Tags ≤ 5, `^[a-z0-9-]{1,24}$`. Poll 2–8 options.

## Comments
| Method | Path | Notes |
|---|---|---|
| GET | `/posts/{id}/comments?sort=best\|top\|new\|controversial\|old&cursor&limit=20` | cursor of top-level `CommentNode`, each with up to 2 further levels × 5 replies preloaded |
| GET | `/comments/{id}/replies?sort&cursor&limit=10` | cursor of direct children (each preloaded 1 level × 3) |
| POST | `/posts/{id}/comments` | `{body (markdown, 1–10 000), parentId?}` → `CommentNode`; max depth 10 |
| GET | `/comments/{id}` | single `CommentNode` without preloaded replies (used for live inserts) |
| PATCH | `/comments/{id}` | author `{body}` |
| DELETE | `/comments/{id}` | author; soft delete keeps tree shape |
| POST | `/comments/{id}/vote` | `{value}` → `{score, myVote}` |

`@username` mentions create notifications.

## Search
| GET | `/search?q=&type=posts\|communities\|users&community=<slug>&sort=relevance\|new\|top&t=&cursor` | `{items, nextCursor}` with item type by `type` |
|---|---|---|
| GET | `/search/suggest?q=` | `{communities: CommunityRef[], users: UserSummary[], posts: {id, title, community: CommunityRef}[]}` |

## Notifications
```ts
Notification { id, type: "POST_REPLY"|"COMMENT_REPLY"|"MENTION"|"NEW_FOLLOWER"|"POST_MILESTONE"|"COMMUNITY_ROLE",
  actor: UserSummary|null, message, link /* app route, e.g. /p/12#c-55 */, read, createdAt, count /* grouped */ }
```
| GET | `/notifications?cursor&unreadOnly=false` | cursor |
|---|---|---|
| GET | `/notifications/unread-count` | `{count}` |
| POST | `/notifications/{id}/read` | 204 |
| POST | `/notifications/read-all` | 204 |

## Ops
`GET /actuator/health` (liveness + readiness groups: db, redis, storage).

## Knowledge layer (Phase 4)

### Context Mode
| Method | Path | Notes |
|---|---|---|
| GET | `/posts/{id}/context` | `ContextView`: main claim, `stats` (analysed/total/labelled/suggested/participants/byStance), `groups` per stance (≤12 each, strongest first), `evidence` links with source comment, `openQuestions`, `agreement`, `disagreement`, `map` (≤80 nodes with parentId), `truncated` |
| PUT | `/comments/{id}/stance` | auth, `{stance: SUPPORT\|OPPOSE\|NEUTRAL\|QUESTION\|EVIDENCE\|EXPERIENCE}`; one label per user, the comment author's label counts ×2 |
| DELETE | `/comments/{id}/stance` | remove your label |

`ArgItem { commentId, parentId, excerpt, author, score, stance, suggested, myLabel, labelCounts }`. `suggested=true` means
the stance came from rules (no reader label yet). Stances are relative to what the comment replies to.

### Thread Digest
| GET | `/posts/{id}/digest?length=short\|standard\|detailed&focus=balanced\|top\|new\|beginner\|technical` | cached `DigestView` (generated on first request) |
|---|---|---|
| POST | `/posts/{id}/digest?length&focus` | regenerate (10/hour/user) |

`DigestView { engine, engineNote, generatedAt, commentCountAtGeneration, currentCommentCount, stale, sections }`;
every `Line { text, commentId, kind: DATA|ANECDOTE|OPINION|SPECULATION|STATEMENT, author }` quotes a real comment.
`consensus.status` is `CONSENSUS` only with ≥5 comments, ≥3 stance-weighted votes and ≥75% on one side.

### Smart Collections (all `/me/**` require auth; collections are private by default)
| Method | Path | Notes |
|---|---|---|
| GET / POST | `/me/collections` | list / create `{name, description?}` (409 on duplicate name) |
| PATCH / DELETE | `/me/collections/{id}` | `{name?, description?, shared?}`; `shared:true` mints a share token, `false` revokes it |
| GET | `/me/collections/{id}/items?q&tag&type` | items in manual order |
| POST | `/me/collections/{id}/items` | `{type: POST\|COMMENT, postId, commentId?, note?, tags?}` (409 if already filed) |
| PUT | `/me/collections/{id}/order` | `{itemIds}` |
| PATCH / DELETE | `/me/collection-items/{itemId}` | `{note?, tags?, collectionId? (move)}` |
| GET | `/me/library/items?q&tag&type` · `/me/library/tags` · `/me/library/membership?postId&commentId` | library-wide search, tag counts, where an item is filed |
| GET | `/collections/shared/{token}` | public read-only view; items from private communities the viewer can't see are omitted |

## Community AI (Phase 5)
| Method | Path | Notes |
|---|---|---|
| GET | `/ai/status` | `{provider, model, llmConfigured, note}` (public) |
| POST | `/me/ai/ask` | `{conversationId?, question (≤1000), communitySlug?, postId?, sinceDays?}` → `{conversation, question, answer}`; 30/hour/user |
| GET | `/me/ai/conversations` · `/me/ai/conversations/{id}` | history (owner only; others get 404) |
| DELETE | `/me/ai/conversations/{id}` | delete a conversation |
| POST | `/me/ai/messages/{id}/feedback` | `{value: -1\|0\|1}` |

`MessageDto { id, role: USER|ASSISTANT, content (markdown with [n] citations), engine, intent, sources: Source[], followUps, notice, feedback, createdAt }`.
`Source { n, kind: POST|COMMENT, postId, commentId, title, communitySlug, author, score, createdAt, excerpt, link, cited }`.
Every `[n]` in `content` is guaranteed to satisfy 1 ≤ n ≤ sources.length. Citations the model invents are stripped server-side.
`postId` scopes the question to one thread, and `communitySlug` to one community. Both are permission-checked.

## Moderation (Phase 3)
All `/mod/**` and `/communities/{slug}/mod/**` endpoints require a community MODERATOR/ADMIN/OWNER (or platform admin);
everyone else gets 403. Every action is written to the moderation log.

```ts
QueueItem { targetType: "POST"|"COMMENT", postId, commentId|null, title, excerpt, author: UserSummary|null,
            removed, locked, pinned, createdAt, reportCount, reasons: {reason, count}[], details: string[] /* ≤5 newest */,
            firstReportedAt, lastReportedAt }
ModAction { id, action: "REMOVE_POST"|"APPROVE_POST"|"REMOVE_COMMENT"|"APPROVE_COMMENT"|"LOCK"|"UNLOCK"|"PIN"|"UNPIN"|"BAN"|"UNBAN",
            moderator: UserSummary|null, targetUser: UserSummary|null, postId|null, commentId|null, targetTitle|null,
            reason|null, expiresAt|null, reversed: boolean, createdAt }
Ban     { user: UserSummary, reason, expiresAt|null /* null = permanent */, bannedBy: UserSummary|null, createdAt }
Appeal  { id, action: ModAction, user: UserSummary, message, status: "PENDING"|"ACCEPTED"|"REJECTED", response|null, createdAt, resolvedAt|null }
ModStats { openReports, pendingAppeals, activeBans, actionsLast30Days, removedLast30Days }
MyModAction { action: ModAction, community: CommunityRef, appeal: Appeal|null, canAppeal: boolean }
```
| Method | Path | Notes |
|---|---|---|
| POST | `/reports` | auth. `{targetType, postId, commentId?, reason (≤100), details? (≤1000)}` → 204. 409 if you already have an open report on it. 20/hour |
| GET | `/communities/{slug}/mod/queue` | open reports grouped by target, most reported first (≤100) |
| GET | `/communities/{slug}/mod/stats` | `ModStats` |
| GET | `/communities/{slug}/mod/log?cursor` | cursor page of `ModAction`, newest first |
| GET | `/communities/{slug}/mod/bans` | active `Ban[]` |
| POST | `/communities/{slug}/mod/bans` | `{username, reason (1..300), days?: 1..3650 \| null}` → `Ban`. Can't ban yourself or a moderator of equal/higher rank |
| DELETE | `/communities/{slug}/mod/bans/{username}` | 204 |
| GET | `/communities/{slug}/mod/appeals?status=PENDING\|ALL` | `Appeal[]` |
| POST | `/mod/appeals/{id}/resolve` | `{accept: boolean, response? (≤1000)}` → `Appeal`. Accepting reverses the action (restore content / lift ban) |
| POST | `/mod/posts/{id}/{remove\|approve\|lock\|unlock\|pin\|unpin}` | `{reason?}` → `PostDetail`. Max 3 pinned per community. Remove/approve resolve the post's open reports |
| POST | `/mod/comments/{id}/{remove\|approve}` | `{reason?}` → 204 |
| GET | `/me/mod-actions/{id}` | the affected user only → `MyModAction` |
| POST | `/me/mod-actions/{id}/appeal` | `{message (10..2000)}` → `Appeal`. One appeal per action; only REMOVE_* and BAN, not reversed |
| GET | `/me/blocks` | `UserSummary[]` |
| PUT / DELETE | `/users/{username}/block` | 204. Blocked users' posts disappear from your feeds/search; their comments render as `[blocked user]`; they can't notify you |

Additions to existing DTOs: `CommunityDetail.viewer.banned: boolean`, `CommunityDetail.viewer.bannedUntil: string|null`;
`PublicProfile.isBlocked: boolean`; `CommentNode.blocked: boolean`.
Notification types added: `CONTENT_REMOVED` (link `/mod-actions/{id}`), `BANNED` (link `/mod-actions/{id}`),
`APPEAL_RESOLVED`, `PULSE_RESULT`. Banned users get 403 on post/comment/vote/pulse-vote in that community.

## Community Pulse (Phase 4)
```ts
Pulse { id, community: CommunityRef, type: "POLL"|"PREDICTION"|"OPINION"|"DEBATE", title, description,
        multiple, anonymous, allowChange, closesAt, status: "OPEN"|"CLOSED"|"RESOLVED", createdAt, author: UserSummary|null,
        totalVoters, myOptionIds: number[], resultsVisible: boolean /* true after you vote, or once closed */,
        options: { id, label, votes: number|null /* null while results hidden */, correct: boolean }[],
        resolvedOptionId|null, accuracy: number|null /* share of PREDICTION voters who picked correctly, 0..1 */,
        viewer: { canVote: boolean, canManage: boolean } }
PulseReason { author: UserSummary|null /* null when anonymous */, optionId, text, createdAt }
```
| Method | Path | Notes |
|---|---|---|
| GET | `/communities/{slug}/pulses?status=active` | `Pulse[]` open, soonest closing first |
| GET | `/communities/{slug}/pulses?status=closed&cursor` | cursor page of closed/resolved `Pulse`, newest first (history) |
| GET | `/pulses/active?limit=5` | open pulses from your joined communities (anonymous: popular public ones) |
| POST | `/communities/{slug}/pulses` | mods. `{type, title (≤200), description? (≤1000), options: string[2..8], multiple, anonymous, allowChange, durationHours: 1..720}` → `Pulse` |
| GET | `/pulses/{id}` | `Pulse` |
| POST | `/pulses/{id}/vote` | `{optionIds (1..8), reasoning? (≤500)}` → `Pulse`. 409 if already voted and `allowChange=false`; 400 if closed |
| POST | `/pulses/{id}/close` | mods → `Pulse` |
| POST | `/pulses/{id}/resolve` | mods, PREDICTION only, after close: `{optionId}` → `Pulse`; notifies voters (`PULSE_RESULT`) |
| GET | `/pulses/{id}/reasons` | `PulseReason[]` (≤50 newest; empty while results hidden) |
| DELETE | `/pulses/{id}` | mods → 204 |

Pulses close automatically at `closesAt` (status is computed on read). Live results: the UI refetches open pulses every 15 s.

## Podcasts (Phase 6)
Episodes belong to a community. Moderators of that community manage them in the studio. Only `PUBLISHED`
episodes are listed publicly, and drafts are visible to managers only. Media is **uploaded**, never fabricated: AI can
draft a cited script when `ANTHROPIC_API_KEY` is set, but audio/video generation is not configured
(`capabilities.audioGeneration=false`).

```ts
EpisodeType = "WEEKLY_ROUNDUP"|"DEEP_DIVE"|"DEBATE"|"INTERVIEW"|"BEGINNER_GUIDE"|"MONTHLY_REPORT"|"COMPARISON"|"HIGHLIGHTS"|"MULTI_PERSPECTIVE"
EpisodeStatus = "DRAFT"|"IN_REVIEW"|"PUBLISHED"|"ARCHIVED"
EpisodeSummary { id, community: CommunityRef, title, summary, type: EpisodeType, mediaKind: "AUDIO"|"VIDEO"|null,
                 durationSeconds|null, thumbnailUrl|null, status: EpisodeStatus, publishedAt|null, createdAt, playCount,
                 saved: boolean, progress: { positionSeconds, completed } | null, chapterCount }
EpisodeDetail = EpisodeSummary & {
  mediaUrl|null, mediaContentType|null, allowDownload: boolean,
  chapters: { id, startSeconds, title }[],
  sources: { position, postId, commentId|null, title, communitySlug, excerpt, link }[],
  hasTranscript: boolean, transcriptVttUrl: string|null /* for <track kind="subtitles"> */,
  script: string|null /* markdown; managers only */, scriptStatus: "NONE"|"GENERATING"|"READY"|"FAILED",
  scriptSource: "HUMAN"|"AI_DRAFT"|null, scriptError: string|null,
  createdBy: UserSummary|null, publishedBy: UserSummary|null,
  viewer: { canManage: boolean } }
TranscriptSegment { id, startSeconds: number, endSeconds: number, speaker: string|null, text }
Capabilities { aiScriptDrafting: boolean, audioGeneration: false, videoGeneration: false, maxUploadMb: number,
               audioTypes: string[], videoTypes: string[] }
```
| Method | Path | Notes |
|---|---|---|
| GET | `/podcasts/capabilities` | `Capabilities` |
| GET | `/podcasts?type=&community=<slug>&sort=recent\|popular&cursor` | cursor page of published `EpisodeSummary` |
| GET | `/podcasts/home` | `{ featured: EpisodeSummary[] (≤3, most played last 30 days), continueListening: EpisodeSummary[] (auth, in progress), recent: EpisodeSummary[] (≤12), saved: EpisodeSummary[] (auth, ≤12) }` |
| GET | `/me/podcasts/history?cursor` | episodes you've played, most recent first |
| GET | `/podcasts/episodes/{id}` | `EpisodeDetail` |
| GET | `/podcasts/episodes/{id}/transcript?q=` | `TranscriptSegment[]`; `q` filters case-insensitively |
| GET | `/podcasts/episodes/{id}/transcript.vtt` | `text/vtt` subtitles |
| PUT | `/podcasts/episodes/{id}/progress` | auth `{positionSeconds, completed?}` → 204. The first play by a user counts once in `playCount` |
| PUT / DELETE | `/podcasts/episodes/{id}/save` | 204 |
| GET | `/communities/{slug}/podcasts/studio` | managers: all episodes of the community, any status |
| POST | `/communities/{slug}/podcasts/episodes` | managers `{title (≤200), summary? (≤2000), type}` → `EpisodeDetail` (DRAFT) |
| PATCH | `/podcasts/episodes/{id}` | managers `{title?, summary?, type?, allowDownload?, script? (≤200k), chapters?: {startSeconds, title}[] (≤100), sources?: {postId, commentId?}[] (≤50)}` → `EpisodeDetail`. Editing a PUBLISHED episode's media/transcript is blocked; unpublish first |
| POST | `/podcasts/episodes/{id}/media` | managers, multipart `file` + `durationSeconds` (client-measured) → `EpisodeDetail`. Audio: mp3/m4a/ogg/wav; video: mp4/webm; validated by magic bytes; ≤ `maxUploadMb` |
| POST | `/podcasts/episodes/{id}/thumbnail` | managers, multipart image → `EpisodeDetail` |
| PUT | `/podcasts/episodes/{id}/transcript` | managers `{format: "vtt"\|"srt"\|"timestamped", content}` → `{segments: number}`. `timestamped` = lines like `[00:01:23] Speaker: text` |
| POST | `/podcasts/episodes/{id}/submit` | DRAFT → IN_REVIEW |
| POST | `/podcasts/episodes/{id}/publish` | managers; requires IN_REVIEW, media and transcript → PUBLISHED (records `publishedBy`) |
| POST | `/podcasts/episodes/{id}/unpublish` | PUBLISHED → ARCHIVED (hidden; can be resubmitted) |
| DELETE | `/podcasts/episodes/{id}` | managers; deletes episode and its media |
| POST | `/podcasts/episodes/{id}/draft-script` | managers `{topic (≤300), targetMinutes: 10..90}` → 202 `EpisodeDetail` with `scriptStatus: "GENERATING"`. Runs in the background; poll the episode. 400 when AI isn't configured. 5/day/user |

Media files are served from `/api/v1/files/**` with HTTP Range support (seeking). Their URLs are unguessable, but they are
not access-controlled, so episodes in private communities have the same limit as images (see DECISIONS).

## Direct messages (one-to-one)
All endpoints require sign-in. You can't message someone you've blocked, or who has blocked you, and the recipient's
`dmPolicy` setting applies: `EVERYONE` (default), `FOLLOWING` (only people they follow) or `NOBODY`.

```ts
DmMessage { id, conversationId, senderId, body: string /* plain text, ≤4000; "" when deleted */,
            media: { id, url, width|null, height|null }[], createdAt, deleted: boolean }
ConversationSummary { id, other: UserSummary|null /* null if their account was deleted */,
            lastMessage: { id, senderId, excerpt, createdAt, deleted } | null,
            unreadCount, otherLastReadMessageId: number|null /* for "Seen" receipts */,
            online: boolean, canMessage: boolean, cannotMessageReason: string|null, updatedAt }
```
| Method | Path | Notes |
|---|---|---|
| GET | `/me/conversations?q=` | your conversations, most recent first. `q` matches the other username/display name or message text |
| POST | `/me/conversations` | `{username}` → `ConversationSummary` (get-or-create). 403 when blocked or not allowed by their `dmPolicy`; 400 for yourself |
| GET | `/me/conversations/{id}` | `ConversationSummary` |
| GET | `/me/conversations/{id}/messages?before=<messageId>&limit=30` | `{items: DmMessage[] /* oldest → newest */, hasMore}` |
| POST | `/me/conversations/{id}/messages` | `{body?, mediaIds?: number[] (≤4, your own uploads from POST /media)}` → `DmMessage`. Needs body or media. 60/min |
| POST | `/me/conversations/{id}/read` | `{messageId}` → 204 (marks everything up to it read; sends a READ receipt) |
| DELETE | `/me/messages/{id}` | your own message → 204 (soft delete, shown as "Message deleted") |
| GET | `/me/messages/unread-count` | `{count}` = unread messages across conversations |

`Me.settings` gains `dmPolicy`; `PATCH /users/me/settings` accepts `{dmPolicy}`. `PublicProfile` gains `canMessage: boolean`.

## Real-time (WebSocket + STOMP)
Endpoint: `ws(s)://<host>/ws` (plain WebSocket, STOMP 1.2). Send the access token in the CONNECT frame:
`Authorization: Bearer <accessToken>`. Invalid or expired tokens are rejected, so reconnect with a fresh token after refresh.
REST stays the source of truth. Events tell the client what changed, and the client updates its cache or refetches.

**Personal events**: subscribe to `/user/queue/events`. Every frame is `{ type, payload }`:
| type | payload |
|---|---|
| `MESSAGE` | `DmMessage` (also echoed to your own other tabs) |
| `MESSAGE_DELETED` | `{conversationId, messageId}` |
| `READ` | `{conversationId, userId, messageId}` |
| `TYPING` | `{conversationId, userId}` (the UI shows "typing…" for ~5 s after the last one) |
| `PRESENCE` | `{userId, online}` (only for people you have a conversation with) |
| `NOTIFICATION` | `{unreadCount}` (refetch the inbox) |
| `UNREAD_MESSAGES` | `{count}` |

**Send typing**: `SEND /app/typing` with body `{conversationId}` (rate-limited and ignored unless you're a participant).

**Topics** (each SUBSCRIBE is permission-checked, and unauthorized ones are rejected with an ERROR frame):
| destination | who may subscribe | frame body |
|---|---|---|
| `/topic/pulse.{pulseId}` | anyone who can view the pulse | `{pulseId, totalVoters}` (refetch the pulse; tallies aren't pushed so hidden results stay hidden) |
| `/topic/modqueue.{communityId}` | that community's moderators | `{communityId}` (refetch queue/stats) |

**Live feeds and threads** (signed-in clients only, since the socket needs a token):
| destination | who may subscribe | frame body |
|---|---|---|
| `/topic/posts` | anyone signed in | `NewPost` for posts in PUBLIC and RESTRICTED communities |
| `/topic/community.{communityId}` | anyone who can view that community (members only for PRIVATE) | `NewPost` for that community |
| `/topic/post.{postId}` | anyone who can view the post | `PostEvent` (below) |

```ts
NewPost { type: "POST_CREATED", postId, communityId, communitySlug, authorId, createdAt }
PostEvent =
  | { type: "COMMENT_CREATED", postId, commentId, parentId: number|null, authorId }
  | { type: "POST_STATS", postId, score, commentCount }            // batched, at most every ~2 s per post
  | { type: "COMMENT_SCORES", postId, scores: { [commentId]: number } } // batched, at most every ~2 s per post
```
Use `GET /comments/{id}` → `CommentNode` (no preloaded replies; same visibility rules as the thread, 404 if deleted or not
visible) to fetch a comment announced by COMMENT_CREATED. Events never include post or comment content, only ids and
counts, so a client can't see anything it couldn't already fetch. Block filtering happens on fetch: a blocked author's
comment comes back with `blocked: true`.

Presence and the simple broker are per-instance (single-node deployment). See DECISIONS for the multi-node path.

## Files & share links (Phase 7)
Private file library. Everything under `/me/files` requires auth and only ever returns the caller's own files (other
people's ids answer 404). Every file response is `Content-Disposition: attachment` with `X-Content-Type-Options: nosniff`,
`Content-Security-Policy: default-src 'none'; sandbox` and `Cache-Control: private, no-store`. Bytes live under a private
storage prefix that the public `/files/**` media path refuses to serve.

```ts
FileItem { id, name, contentType /* from magic bytes; E2EE = application/octet-stream */, sizeBytes, e2ee,
           scanStatus: "CLEAN"|"UNSCANNED" /* CLEAN = passed structural checks, not an antivirus verdict */,
           scanDetail|null, sha256, activeLinks, createdAt, updatedAt }
FileUsage { usedBytes, quotaBytes, maxFileBytes, fileCount }
ShareLink { id, fileId, tokenHint /* last 4 chars */, status: "ACTIVE"|"EXPIRED"|"REVOKED"|"EXHAUSTED",
            createdAt, expiresAt|null, hasPassword, maxDownloads|null, downloadCount,
            keyMode: "FRAGMENT"|"PASSPHRASE"|null, revokedAt|null, lastAccessAt|null }
WrappedKey { wrappedKey /* b64url: IV(12)+AES-GCM(key)+tag */, salt /* b64url, 16-64 bytes */, iterations /* >= 600000 */ }
AccessEntry { id, at, event: "VIEWED"|"DOWNLOADED"|"WRONG_PASSWORD"|"LOCKED"|"EXPIRED"|"REVOKED"|"LIMIT_REACHED"|"FILE_DELETED",
              viewer: UserSummary|null /* null = anonymous */, visitor: string|null /* 12-hex daily salted hash, never an IP */ }
ShareMeta { fileName|null, contentType|null /* both null for E2EE */, sizeBytes, e2ee, keyMode|null, expiresAt|null,
            requiresPassword, downloadsRemaining|null, scanStatus }
```

| Method | Path | Notes |
|---|---|---|
| GET | `/me/files?q&cursor&limit` | `CursorPage<FileItem>`, newest first; `q` = case-insensitive name search |
| GET | `/me/files/usage` | `FileUsage` (quota `strata.files.quota-mb`, default 1024; max file `max-file-mb`, default 500) |
| POST | `/me/files` (multipart `file`, `e2ee?`, `label?`) | 201 `FileItem`. Streamed to storage. 400 `fieldErrors.file` for: over size/quota, blocked executable/script/macro type, content that doesn't match the extension, unsupported type, archive with executables or bomb-like expansion. With `e2ee=true` the body must be a Strata container (`STRATAE2` v1); the client's file name is not stored (`label` or "Encrypted file") |
| GET | `/me/files/{id}` | `FileItem` |
| PATCH | `/me/files/{id}` `{name}` | Rename (plain files keep their extension; E2EE renames the label) |
| DELETE | `/me/files/{id}` | 204. Soft-delete, revoke links, remove stored bytes after commit |
| GET | `/me/files/{id}/content` | Owner download (Bearer). Supports `Range` (206) |
| POST | `/me/files/{id}/download-url` | `{url, expiresAt}`: signed `/downloads/{grant}` valid 5 min, for plain browser downloads |
| GET | `/me/files/{id}/links` | `ShareLink[]` |
| POST | `/me/files/{id}/links` `{expiresAt?, password? (4-128), maxDownloads? (1-10000), keyMode?, wrappedKey?}` | 201 `{link: ShareLink, token}`. The token is returned **once** (only its SHA-256 is stored); share page `/s/{token}`. `keyMode` is required for E2EE files (`PASSPHRASE` requires `wrappedKey`) and rejected for plain files. `expiresAt` must be at least 1 min ahead and at most 1 year |
| DELETE | `/me/files/{id}/links/{linkId}` | 204, revoke (idempotent) |
| GET | `/me/files/{id}/links/{linkId}/access?cursor&limit` | `CursorPage<AccessEntry>`, newest first |
| GET | `/share/{token}` | Public `ShareMeta` (logs one VIEWED per visitor per 10 min). 404 unknown; **410 `GONE`** with a message when expired, revoked, exhausted or the file was deleted (logged) |
| POST | `/share/{token}/unlock` `{password?}` | Public. `{downloadUrl, expiresAt, wrappedKey: WrappedKey|null}`. Wrong password → 400 `fieldErrors.password` (logged). 5 wrong passwords per visitor per 15 min (or 50 per link per hour) → 429 (LOCKED logged). A signed-in caller is recorded as the viewer |
| GET | `/downloads/{grant}` | Public, signed grant (HMAC, 5 min). Re-checks the link at use time. Unlimited links support `Range`; a request without Range or from byte 0 counts as a download. Download-limited links ignore `Range` and send the whole file, each request counting. 404 bad/expired grant, 410 link no longer usable |

Error code `GONE` (410) is new with this section.
