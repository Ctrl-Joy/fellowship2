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
AI Assistant, Collections, Podcasts, Messages, Moderator funding and Leaderboard appear under “Coming next” in the nav and
all route to one `/soon/:feature` page that collects no data. Reporting/moderation actions, Context Mode and Digest have no UI yet.

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
