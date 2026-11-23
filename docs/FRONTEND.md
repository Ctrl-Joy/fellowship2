# Strata frontend

React 18 + TypeScript (strict) + Vite 6 + Tailwind 3. Lives in `frontend/`.

## Run
```bash
cd frontend
npm install
npm run dev        # http://localhost:5173, proxies /api -> http://localhost:8080 (override with VITE_BACKEND_URL)
npm run build      # tsc -b && vite build
npm run lint       # type-check only (tsc -b --noEmit)
npm test           # vitest run
```

## Structure
```
src/
  app/            Providers (QueryClient, theme sync, session boot), router (lazy routes), route guards
  routes/         Page components, one folder per area (feed, posts, communities, profile, settings, auth, ...)
  layouts/        AppLayout (top bar, left nav, mobile tab bar, drawer), PublicLayout, RightRail, PageColumns
  features/<f>/   api.ts (endpoint calls) · hooks.ts (TanStack Query) · components/ · pure helpers (+ tests)
  components/ui/  Design-system primitives (Button, Field, Dialog, Menu, Tabs, Tooltip, Switch, Toast, States, ...)
  services/       apiClient (fetch, auth header, single-flight refresh), ApiError, query-key factory
  store/          Zustand auth store (access token in memory only)
  design-system/  brand.ts, tokens.css, BrandMark — the only place to rename/recolour the product
  hooks/ utils/ types/
```

## Auth model
- Access token kept **in memory** (Zustand). Refresh token is the backend's HttpOnly cookie.
- On boot `POST /auth/refresh` restores the session. Any 401 triggers one shared refresh, then the request is retried once.
- `RequireAuth` waits for boot, then redirects to `/login?next=…`; `RedirectIfAuthed` honours only same-origin `next` paths
  and routes users with `onboardingCompleted=false` to `/onboarding`.

## Pages → endpoints
| Page | Endpoints |
|---|---|
| Landing | `GET /communities?sort=popular` |
| Login / Register | `POST /auth/login`, `POST /auth/register`, `GET /auth/username-available` |
| Forgot / Reset / Verify | `POST /auth/forgot-password`, `/auth/reset-password`, `/auth/verify-email` |
| Onboarding | `GET /topics`, `GET /onboarding/suggestions`, `POST /onboarding/complete` |
| App shell | `GET /me/communities`, `GET /search/suggest`, `GET /notifications/unread-count` (30 s poll), `PATCH /users/me/settings` (theme) |
| Right rail | `GET /trending/posts`, `GET /communities/trending` |
| Home | `GET /feed?tab&t&cursor` |
| Post rows (everywhere) | `POST /posts/{id}/vote`, `PUT/DELETE /posts/{id}/save`, `PUT /posts/{id}/hide`, `DELETE /posts/{id}`, `POST /posts/{id}/poll/vote` |
| Post detail | `GET /posts/{id}`, `PATCH /posts/{id}`, `GET /communities/{slug}`, `GET /posts/{id}/comments`, `GET /comments/{id}/replies`, `POST /posts/{id}/comments`, `PATCH/DELETE /comments/{id}`, `POST /comments/{id}/vote` |
| Submit | `GET /me/communities`, `GET /communities/{slug}` (flairs, canPost), `POST /media`, `POST /posts` |
| Communities directory | `GET /topics`, `GET /communities?q&topic&sort&page` , `POST/DELETE /communities/{slug}/membership` |
| Create community | `GET /topics`, `POST /communities` |
| Community page | `GET /communities/{slug}`, `GET /communities/{slug}/posts`, `GET /communities/{slug}/members` |
| Community settings | `PATCH /communities/{slug}`, `PUT …/rules`, `PUT …/flairs`, `POST …/icon`, `POST …/banner`, `PUT …/members/{u}/role` |
| Explore | `GET /topics`, `GET /communities?sort=popular`, `GET /feed?tab=rising` |
| Search | `GET /search?q&type&community&sort&t&cursor` |
| Profile | `GET /users/{u}`, `GET /users/{u}/posts`, `GET /users/{u}/comments`, `POST/DELETE /users/{u}/follow` |
| Settings | `PATCH /users/me`, `POST /users/me/avatar|banner`, `POST /users/me/password`, `GET/DELETE /auth/sessions`, `POST /auth/resend-verification`, `POST /users/me/deactivate`, `DELETE /users/me`, `PATCH /users/me/settings` |
| Saved | `GET /me/saved` |
| Notifications | `GET /notifications`, `POST /notifications/{id}/read`, `POST /notifications/read-all` |

## Not built yet (shown honestly)
Moderator funding and Leaderboard appear under “Coming next” in the nav and route to one `/soon/:feature` page that collects
no data. Everything else listed in the nav (AI, Collections, Podcasts, Messages, moderation, Context Mode, Digest) is built.

## Notes
- Post bodies are authored as HTML in TipTap and rendered from the server's sanitized `bodyHtml`. Comments are markdown rendered
  with `react-markdown` + `skipHtml` (no raw HTML); `@username` becomes an internal link.
- Optimistic updates: votes (posts + comments), saves, follows, joins, mark-read. All roll back on error.
- Comment threads: top level via cursor, replies merged lazily per parent; visual nesting caps at 4 (mobile) / 6 (desktop),
  deeper replies open in a focused “Continue this thread” view.

## Moderation & Pulse
| Area | Where | Endpoints |
|---|---|---|
| Report post/comment | PostMenu "Report", comment ⋯ "Report" → `ReportDialog` (rule titles + generic reasons) | `POST /reports` (409 → "already reported") |
| In-context mod tools | Post page PostMenu "Moderation" section (`canModerate`), comment ⋯ for mods via ThreadContext | `/mod/posts/{id}/{verb}`, `/mod/comments/{id}/{verb}`, ban dialog → `POST …/mod/bans` |
| Mod dashboard | `/c/:slug/mod` (Queue · Appeals · Bans · Log + stats tiles); "Mod tools" pill with open-report badge on community header | `…/mod/{queue,stats,log,bans,appeals}`, `/mod/appeals/{id}/resolve` |
| Affected user | `/mod-actions/:id` (decision + one-time appeal) | `GET/POST /me/mod-actions/{id}[/appeal]` |
| Ban banner | Community header when `viewer.banned`; Join hidden | — |
| Blocking | Profile Block/Unblock (confirm), Settings → Account → Blocked users; comments with `blocked` render a muted placeholder | `PUT/DELETE /users/{u}/block`, `GET /me/blocks` |
| Community Pulse | Community "Pulse" view (`?view=pulse`: active + history, mods "New Pulse"), right-rail card on community page, "Active pulses" on home rail | `/communities/{slug}/pulses`, `/pulses/{id}[/vote\|close\|resolve\|reasons]`, `/pulses/active` |

Open pulses refetch every 15 s. Pure helpers (`features/moderation/helpers.ts`, `features/pulse/helpers.ts`) are unit-tested.

## Podcasts

| Route | What it does | Endpoints |
|---|---|---|
| `/podcasts` | Discovery: featured, continue listening, saved, recent shelves, plus a cursor list filtered by category chips, community and sort; History tab for signed-in users | `/podcasts/home`, `/podcasts`, `/me/podcasts/history` |
| `/podcasts/:id` | Episode: header actions (play/resume, queue, save, share, download, edit in studio), inline video with subtitles, chapters, searchable synced transcript, sources | `/podcasts/episodes/{id}`, `…/transcript?q=`, `…/transcript.vtt`, `…/save`, `…/progress` |
| `/c/:slug?view=podcasts` | The community's published episodes; Studio link for moderators | `/podcasts?community=` |
| `/c/:slug/podcasts/studio` | Studio list by status, "New episode" | `/communities/{slug}/podcasts/studio`, `POST …/podcasts/episodes` |
| `/c/:slug/podcasts/studio/:id` | Editor: details, cover, media upload (XHR progress, client-measured duration), transcript import, chapters, sources, script with AI drafting (polls while `GENERATING`), and the Draft → In review → Published workflow with a checklist | `PATCH /podcasts/episodes/{id}`, `…/media`, `…/thumbnail`, `PUT …/transcript`, `…/submit`, `…/publish`, `…/unpublish`, `…/draft-script`, `/podcasts/capabilities` |

**Global player.** `store/player.ts` (zustand, persisted: current track, queue, per-episode positions, rate) drives the one
`<audio>` element in `features/podcasts/components/MiniPlayer.tsx`. That element is mounted in `AppLayout`, so audio keeps
playing across navigation. Video plays inline on the episode page only.
- **Progress:** saved through `createProgressSaver` (throttled to 15 s, forced on pause and seek, `fetch` keepalive on
  `pagehide`); an episode is marked complete near the end.
- **Media Session:** metadata and handlers provide lock-screen and headset controls.
- **Resume:** playback starts from the server position, falling back to the position saved on this device; finished
  episodes restart from the beginning.

## Messages & real-time
- **Connection** (`services/realtime.ts`, `features/realtime/RealtimeProvider.tsx`): `@stomp/stompjs` 7.5.0 over a plain
  WebSocket at `ws(s)://<host>/ws` (Vite proxies `/ws` with `ws: true`; nginx upgrades it with a 1 h read timeout). It connects
  only while signed in, sends `Authorization: Bearer` in CONNECT (read fresh in `beforeConnect`), refreshes the token after a
  rejected CONNECT, and backs off exponentially from 1 s to 30 s. It reconnects when the signed-in user changes and disconnects on
  logout. A same-user token refresh keeps the open socket, because the server authenticates at CONNECT.
- **Events** on `/user/queue/events` update React Query caches: MESSAGE / MESSAGE_DELETED patch the thread pages; READ moves
  the "Seen" marker (or clears my unread count from another tab); TYPING feeds a zustand store with a 5 s expiry; PRESENCE flips
  online dots; NOTIFICATION / UNREAD_MESSAGES set the badge counts. Polling (notifications 60 s, DM unread 60 s, threads 15 s,
  pulses 15 s) runs **only while the socket is down**.
- **Hooks**: `useRealtimeStatus()` and `useTopic(destination | null, cb)` (subscriptions survive reconnects). PulseCard subscribes
  to `/topic/pulse.{id}` while the pulse is open; the mod dashboard subscribes to `/topic/modqueue.{communityId}` only once the
  community confirms that you're a moderator, because an unauthorized SUBSCRIBE is rejected.
- **UI**: `/messages` and `/messages/:id` show two panes on desktop and one at a time on mobile. The list has online dots, an
  excerpt, time, an unread badge, debounced `?q=` search and a New message dialog with username autocomplete. The thread groups
  messages by day and by sender in 5-minute runs, puts my messages on the right in the brand colour, shows "Seen" under the newest
  message they've read, a typing indicator, and "Message deleted". Older messages load when you scroll to the top, and the scroll
  position is kept. Sends are optimistic, with Retry/Discard on failure; images upload through `POST /media` (≤4). The composer
  grows as you type, Enter sends and Shift+Enter adds a line. TYPING goes out at most once per 2 s. When `canMessage` is false,
  the composer is replaced by `cannotMessageReason`. The thread is marked read while it's visible.
- **Entry points**: a top-bar icon with the unread count; Messages in the left nav (`/soon/messages` redirects); a Message
  button on profiles when `canMessage`; Settings → Preferences → "Who can message you" (`dmPolicy`).
- Pure logic lives in `features/messages/helpers.ts` (grouping, appendMessage/applyDelete/applyRead, page helpers,
  `seenMessageId`, throttle) and `features/realtime/typingStore.ts`, all covered by `helpers.test.ts`.

## Live feeds and threads
Signed-in only (anonymous users have no socket). Code in `features/live/`; event types in `types/live.ts`.
- **"N new posts" pill** (`useNewPosts` + `NewPostsPill`): the home feed listens on `/topic/posts`, a community feed on
  `/topic/community.{id}` (subscribed only when the community detail says `viewer.canView`, since a rejected SUBSCRIBE closes
  the socket). Frames are counted, never inserted: own posts are ignored and ids are deduped. New / Hot / Rising / Top count
  everything; Best (for you) counts posts in joined communities (`useMyCommunities`; everything until that list loads);
  Following is people-based, so it shows no pill. The pill is a zero-height sticky row under the top bar, so the list never
  shifts; clicking it scrolls to the top, trims the infinite query to its first page and refetches it
  (`refetchFromFirstPage` in `posts/cache.ts`), then clears the count. The count resets when the tab, sort or range changes. A
  polite `role="status"` region announces the count.
- **Live post page** (`usePostLive`, subscribed to `/topic/post.{id}` once the post detail loaded):
  - `POST_STATS` patches `score` and `commentCount` everywhere the post is cached (detail, feeds, profile, search) and never
    touches `myVote`; it is skipped while my own vote on the post is in flight (mutation key `mk.postVote`).
  - `COMMENT_SCORES` patches scores in every cached sort order of the thread, skipping comments I'm voting on (`mk.commentVote`).
  - `COMMENT_CREATED` from someone else (mine are inserted by the create mutation) fetches `GET /comments/{id}` unless the
    comment is already cached. Top-level comments go first on page one; replies are appended to a cached parent (its
    `replyCount` grows, so "N more replies" for unloaded children stays right). Replies to a parent that isn't loaded are
    skipped (POST_STATS still updates the total); a 404 is ignored. Arrivals are highlighted for 4 s from a small zustand
    store (`newComments.ts`) with a brand tint and left rule that fades out.
- `patchPostEverywhere` and `patchForests` now keep untouched lists and pages by reference, so a 2 s stats batch only
  re-renders what changed. Pure logic is tested in `features/live/logic.test.ts` and `features/comments/tree.test.ts`.

## Admin, mutes and spam holds (D-021)
| Route | What it shows | API |
|---|---|---|
| `/admin` | Admin dashboard (ADMIN only; others see an "Admins only" page and the server refuses every call). Tabs: **Overview** (real counts, 14-day sign-up bar chart in Recharts with an accessible text label), **Users** (debounced prefix search, status filter, detail dialog with counts, moderated communities, admin history and suspend/unsuspend/promote/demote with a reason), **Communities** (search, filter, quarantine/ban/restore with a required reason), **Flags** (spam holds and rejections across communities; open reports grouped per target with remove/dismiss), **Audit log** | `/admin/**` |
| `/c/:slug/mod?tab=held` | "Held" tab in mod tools: content held by the spam checks, with Approve and publish / Remove | `/communities/{slug}/mod/held`, `/mod/flags/{id}/…` |
| `/settings?tab=muted` | Muted people and communities with Unmute | `/me/mutes`, `/users/{u}/mute`, `/communities/{slug}/mute` |
- **Entry points:** "Admin dashboard" in the account menu for admins. Mute button on profiles (next to Block) and an icon
  button in the community header. Mute state comes only from `/me/mutes` (`features/mutes/api.ts`), never from public payloads.
- **Held content:** feed cards and the post page show "Awaiting review"; the post page explains who can see it. A held
  comment shows an "Awaiting review" tag, and the author gets a toast instead of a comment-count bump. Moderators see
  "Approve and publish" in the post and comment menus.
- **Muted comments** start collapsed with a "muted" hint and expand on click.
- **Quarantined/banned communities** show a banner with the admins' reason; a banned community shows a closed state.
- **Feed mod tools:** `PostCard`/`PostRow` pass `post.viewer?.canModerate` to `PostMenu`, so moderators get remove,
  approve, lock, pin and ban in feeds. `patchPostEverywhere` keeps the detail's wider `viewer` when patching from a feed row.
- **Onboarding:** "Skip for now" calls `POST /onboarding/skip` and stores the returned `Me`.
- Pure logic: `features/admin/helpers.ts` (chart series, offered account actions, audit sentences, rule labels) and
  `features/mutes/api.ts` lookups, tested in `helpers.test.ts` and `mutes.test.ts`.
