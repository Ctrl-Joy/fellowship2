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
