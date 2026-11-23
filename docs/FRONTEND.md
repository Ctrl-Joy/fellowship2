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
Nothing in the nav is a placeholder any more: every item routes to a real feature. The `/soon/:feature` page and the
`upcomingNav` list remain for future work and render nothing while the list is empty.

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

### Funding with Stripe (D-034)
- `components/Demo.tsx` became `components/Mode.tsx`: `ModeBanner`, `ModeBadge` and `MoneyAmount` take their text from the API
  (`FundingStatus.label`/`notice`/`payouts`, `Money.label` per row): "DEMO, no real money", "TEST MODE: no real money", or no
  badge for live money. Buttons say "(demo)" or "(test mode)" (`contributeSuffix`).
- **Contribute dialog**: when the response has `checkout`, the browser goes to `checkout.url` (Stripe Checkout), and the button
  shows "Opening Stripe Checkout…". Test mode says to use a Stripe test card. The site never sees card data or any Stripe key.
- **Return**: `/funding/pools/:id?checkout=success&session_id=…` shows `CheckoutReturn`, which polls
  `POST /funding/checkouts/confirm` (about a minute, slowing down) until CONFIRMED, FAILED or EXPIRED, then refreshes funding
  queries. If Stripe hasn't confirmed by then it shows "Payment pending" (it counts once confirmed).
  `?checkout=cancelled&pending=…` calls the cancel endpoint and says nothing was charged. URL parsing and the poll schedule
  are tested in `checkout.test.ts`.
- My contributions shows "billed monthly by Stripe", a failed-renewal warning, and refunded amounts. The transparency report
  labels allocations "Allocated, not paid out" and marks refund ledger rows.
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
## Reputation and moderator recognition
Code in `features/reputation/` (`api.ts`, pure `logic.ts` + `logic.test.ts`, components); types in `types/recognition.ts`.
- **Routes.** `/leaderboard` (tabs: Moderators with month and community selectors, Topics with each topic's top 3) and
  `/topics/:slug/leaders`. "Leaderboard" moved from "Coming next" into the main nav; `/soon/leaderboard` redirects.
- **Moderator rows** expand (a disclosure button with `aria-expanded`; the name links stay outside it) into `ScoreBreakdown`: each component's value, its renormalised weight, the
  points it adds and the raw rows behind it in words. `explainScore` re-derives the server's score from the components
  (tested), and the rail shows `FormulaPanel` built from the server's formula constants, so the page can't drift.
- **Thread chips.** `ExpertiseChip` sits next to the author name in `CommentItem`; all chips on a thread read one cached
  `GET /posts/{id}/expertise` query.
- **Profile.** `TopicStandings` (topic badges with rank) and `ModeratorRecord` (monthly scores and badges) in the About
  rail; both hide when the server says `hidden`.
- **Mod tools → Team** tab: `PeerEndorsements` ("Helpful +1" with an optional note, one per co-moderator per month).
- **Settings → Preferences → Moderator recognition**: "Show me on moderator leaderboards" (`/me/moderator-settings`).
- Empty states say why a board is empty (no qualifying activity yet) instead of showing placeholder rankings.
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

## Infrastructure polish (Phase 10)
- **Responsive images** (`utils/images.ts`, tested in `images.test.ts`): `responsiveImage(media)` builds `src` + `srcSet` with
  width descriptors from `thumbnails` (320/960 variants plus the original's real width); `FEED_IMAGE_SIZES` is the `sizes`
  for feed and post images. `sizedUrl(url, 320|960)` appends `?w=` to our own media URLs only (external, `blob:` and
  `data:` URLs are left alone). Used by `PostCard` (card media), `PostRow` (compact thumbnail), the post page gallery,
  `Avatar` (all avatars and community icons ask for 320 px), the right-rail and collection thumbnails. All of them load
  lazily with `decoding="async"`. Banners keep the original (960 px would look soft on wide screens).
- **Private DM images** (`features/messages`): the composer uploads through `dmApi.uploadMedia` (`POST /me/dm-media`).
  `DmImage` renders a signed image with `srcset`; signed URLs expire, so a failed load invalidates the thread query once
  to fetch fresh URLs. `DmMessage.media` is `DmMedia[]` (`types/messages.ts`).
- **Demo data notice** (`features/demo`): `DemoNotice` sits under the top bar when `GET /demo/status` says the database
  holds the fictional demo data. It is a `role="note"` strip with a labelled dismiss button; dismissal is remembered in
  localStorage (wrapped in try/catch, tested in `api.test.ts`), so it shows again in a private window.
## End-to-end tests (Playwright) and accessibility audit (D-027)
`frontend/e2e/`, Chromium only, run with `npm run e2e` (`playwright.config.ts`). The suite needs a running backend with
the demo seed and a frontend server that proxies `/api` and `/ws`. Playwright does **not** start anything by default.

| Spec | Flows |
|---|---|
| `01-account` | 1 register (a fresh `e2e_*` user, one per run), 3 onboarding (topic, community, theme), 2 log out and log in, 4 create a community |
| `02-posts` | 5 join (leaves first if already a member), 6 create a text post, 7 vote, 8 comment, 9 reply, 10 save, 11 new collection from the post menu, 12 search |
| `03-knowledge` | 13 Context Mode, 14 Digest (+ refresh), 15 Community AI with one question: passes with a cited answer (live Claude or the offline engine) or the honest notice |
| `04-pulse` | 16 create a Pulse (UI, 1 hour), 17 vote (results hidden until you vote) |
| `05-files` | 18 upload, share link, download signed out (content compared); 18b E2EE: encrypt in the browser, check the upload never contained the name or plaintext, open the `#k=` link in a fresh context, decrypt, read the decrypted Blob inside the page |
| `06-podcast-funding-leaderboard` | 19 play an episode (the player starts and the position advances; on a fresh database a clearly labelled 20 s WAV test tone is published through the studio API and deleted afterwards), 20 demo contribution (DEMO label asserted), 21 leaderboard (rows and breakdown, or the honest empty state) |
| `07-moderation-messages` | 22 report → remove from the queue → the author's notification, appeal page and "removed" notice (the moderator isn't named); 23 DM between two contexts, arriving live with no reload |
| `08-accessibility` | axe-core on 10 pages × dark/light × 1280 px/375 px; serious and critical violations fail (see `docs/ACCESSIBILITY.md`) |
| `09-keyboard` | skip link, visible focus ring, dialog focus trap, Escape and focus return |

**Repeatable on one database.** Every test creates uniquely named data (`uid()`), arranges what isn't the flow under
test through the API (`sessions.api(user)`), and uses accessible locators only (`getByRole`, `getByLabel`). Tests run
serially in one worker. Each demo user gets one long-lived browser context, because the refresh cookie rotates on every
page boot and reusing an old one revokes the session; `gotoAuthed` waits for the boot refresh. Locally the latest cookie
per user is kept in `e2e/.auth/` (git-ignored) to stay under the login rate limit (10 per account per 10 min); a stale
cookie falls back to a fresh login. Registration is limited to 5 per IP per hour, so the suite registers once per run.
Nothing reads email: no flow requires a verified address.

**Running it locally.**
```bash
cd frontend
npx playwright install chromium            # once
# Backend on :8080 with the seed. If its CORS_ALLOWED_ORIGINS doesn't list your test server's origin, let the dev proxy
# present an allowed one (it only rewrites requests whose Origin is the dev server's own localhost origin):
STRATA_DEV_PROXY_ORIGIN=http://localhost:5173 npx vite --port 5174 --strictPort
npm run e2e                                 # E2E_BASE_URL defaults to http://localhost:5174
E2E_START_SERVER=1 npm run e2e              # or let Playwright start `vite --port 5174` itself
AXE_ONLY="post page,files" npx playwright test 08-accessibility   # audit a subset
npm run e2e:report                          # HTML report (CI); traces are kept for failed tests
```
Vitest ignores `e2e/**`; `tsc -b` type-checks the specs through `tsconfig.e2e.json`.

**CI.** The `e2e` job in `.github/workflows/ci.yml` starts PostgreSQL and Redis service containers, runs the backend
jar with the seed on, `AI_PROVIDER=offline` and an empty `ANTHROPIC_API_KEY`, serves the built frontend with
`vite preview --port 5174` (same proxy) with `CORS_ALLOWED_ORIGINS=http://localhost:5174`, waits for readiness and runs
the suite. It references no repository secrets (every value is a throwaway CI-only string), checks out without persisted
credentials, and uploads the report and traces only when it fails.
