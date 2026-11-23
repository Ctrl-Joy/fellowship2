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
Leaderboard appears under “Coming next” in the nav and routes to one `/soon/:feature` page that collects
no data. (Moderator funding now has real routes, in demo mode; see below.) Everything else listed in the nav (AI, Collections, Podcasts, Messages, moderation, Context Mode, Digest) is built.

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

## Moderator funding (demo mode)
Code in `features/funding/`; types in `types/funding.ts`. Every funding view starts with `DemoBanner` ("DEMO, no real money", warning
colours, `role="note"`), and every amount is rendered through `MoneyAmount`, which puts a DEMO badge next to it (screen readers hear
"Demo, no real money").

| Route | What it does | Endpoints |
|---|---|---|
| `/c/:slug?view=funding` | "Funding" tab: current pools with progress bars, Contribute (members), New pool (moderators), Close now / Allocate (owner), allocation policy panel (owner edits, others read), past periods | `GET /communities/{slug}/funding`, `POST …/funding/pools`, `PUT …/funding/policy`, `POST /funding/pools/{id}/contributions`, `GET …/allocation-preview`, `POST …/close` |
| `/funding` (nav: Moderator funding) | Open pools in my communities, my monthly contributions with Cancel (confirm dialog), contribution history | `GET /me/funding`, `POST /me/funding/subscriptions/{id}/cancel` |
| `/funding/pools/:id` | Public transparency report: totals, reconciliation check, anonymous/named contributors, allocations with policy and activity, append-only ledger | `GET /funding/pools/{id}/report`, `GET /funding/status` |

- **Contribute dialog**: one time or monthly, preset amounts or a typed amount, "show my name" off by default. The submit button
  repeats the amount and "(demo)".
- **Close dialog**: for EQUAL and ACTIVITY it shows the server's preview of each moderator's share. For MANUAL it asks for an
  amount per moderator and shows a live "left to allocate" figure. It also requires a public note, and stays disabled until the
  amounts add up exactly.
- `/soon/funding` redirects to `/funding`, and the "Coming next" nav entry has been replaced by the real route.
- Pure logic lives in `features/funding/money.ts` (format/parse minor units with integer maths only, bounds, presets, progress) and is
  tested in `money.test.ts`.
## Files & share links
| Route | What it does | Endpoints |
|---|---|---|
| `/files` (nav "Files", signed in) | Usage meter against the quota. A drag-and-drop or picker upload zone with per-file XHR progress and cancel; uploads run one after another. An "Encrypt in browser" switch with an explanation, an optional label visible to the server, and "Remember the key in this browser". The library has name search, infinite scroll, a type, size and date per row, a "Checked" badge (tooltip says it isn't antivirus) or "E2EE · not scanned", active link count, Download, Share, Links and access log, Rename and Delete (confirmed) | `/me/files`, `/me/files/usage`, `POST /me/files` (XHR), `PATCH/DELETE /me/files/{id}`, `POST …/download-url`, `GET …/content` |
| Share dialog | Expiry presets (1 h to 30 d, or never), access password, download limit. For E2EE files it also asks where the key goes: in the link (`#k=`) or a passphrase (with confirmation, at least 10 characters). It first checks the key against the file's encrypted metadata, fetched with a Range request. The full link appears once with Copy. After an encrypted upload it also shows the recovery key | `POST /me/files/{id}/links`, `GET …/content` (Range) |
| Links dialog | Each link with status (active, expired, turned off, limit reached), password and key-mode badges, downloads, last activity and "Turn off". Each link's access log is expandable and paged: signed-in viewers by name, anonymous visitors as a short daily hash | `GET /me/files/{id}/links`, `DELETE …/links/{linkId}`, `GET …/links/{linkId}/access` |
| `/s/:token` (public layout, works signed out) | Name, size, type, expiry, downloads left, and an honest scan note. Asks for the password and, for E2EE, the passphrase, or reads `#k=` from the fragment; a link without its key gets a clear message. Plain files download through the signed URL. E2EE files are fetched as ciphertext with progress, decrypted in the browser and saved under their original name. A wrong passphrase is caught before any counted download. Expired, revoked or exhausted links show the server's reason | `GET /share/{token}`, `POST /share/{token}/unlock`, `GET /downloads/{grant}` |
| `/security/e2ee` | The threat model in plain language (mirrors `docs/SECURITY_E2EE.md`), linked from every E2EE explanation | — |

- **Crypto** (`features/files/crypto.ts`): WebCrypto AES-256-GCM, a chunked container (`STRATAE2` v1, 1 MiB chunks,
  per-chunk IV and tag, header, index and last flag in the AAD, encrypted name and type), raw key export as base64url for
  the fragment, PBKDF2-SHA256 at 600k iterations to wrap keys. Tested in `crypto.test.ts` under Vitest's Node environment
  (round trip, wrong key, tampering, truncation, reordering, wrong passphrase).
- **Key memory** (`features/files/keyStore.ts`): optional per-device localStorage map from file id to key. Every access
  is wrapped in try/catch and never uploaded.
- Pure helpers (`features/files/helpers.ts`: sizes, expiry presets, share URLs, labels, passphrase rules) are covered by `helpers.test.ts`.
