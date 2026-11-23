# Strata design system

Working brand: **Strata** — "knowledge in layers". The name, tagline, logo mark and colors live in
`frontend/src/design-system/brand.ts` and `frontend/src/design-system/tokens.css`. Change them there and nowhere else.

## v2 update (layout pass)
- Dark theme is near-black `#0B0E10` with content directly on the background (no boxed feed); hover tints rows.
- **Brand indigo** (`--brand`, `#7A7DFF` dark / `#474AE8` light) is used for primary buttons and links. **Jade** (`--accent`) is kept
  for upvotes and positive state, and **violet** (`--down`) for downvotes.
- Buttons, votes and actions are **pills** (`rounded-full`). Cards and media use `rounded-2xl`. Panels in the right rail use
  `surface-2` at 60% with no border.
- Post titles use the **sans** at 18px semibold (scanability); the serif is reserved for the wordmark and landing headlines.
- Feeds offer **Card** and **Compact** views (remembered per device). Sorting is a dropdown (`Best ▾`), not a tab row.
- Comments: a 28px avatar column with a clickable thread line (collapse), body aligned to the username, pill actions.

## Principles
1. **Editorial, not dashboard.** Content is typography first. Chrome is quiet. Titles use a serif and
   body/UI uses a sans.
2. **Dense but breathable.** Feeds are list-first (compact rows with an optional media thumb), not giant cards.
3. **One accent.** Jade is reserved for primary actions, the active nav item and upvotes. Everything else is neutral.
4. **No decoration that doesn't carry meaning.** No gradients behind content, no glass, no emoji-as-icons.
5. **Motion explains state changes.** It never decorates. Everything honours `prefers-reduced-motion`.

## Color tokens (CSS variables, `rgb` triplets so Tailwind can apply alpha)
| Token | Dark (default) | Light | Use |
|---|---|---|---|
| `--bg` | `#0E0F11` | `#F7F6F3` | app background |
| `--surface` | `#15171A` | `#FFFFFF` | panels, post rows |
| `--surface-2` | `#1C1F23` | `#F1EFEA` | hover, inputs, nested comments |
| `--border` | `#272A30` | `#E3E0D8` | hairlines |
| `--text` | `#E8E6E1` | `#1A1A18` | primary text |
| `--text-muted` | `#9A9890` | `#5E5C55` | meta |
| `--text-faint` | `#6B6A64` | `#8C8A82` | timestamps, placeholders |
| `--accent` | `#3FB68B` | `#13805C` | primary, upvote |
| `--accent-fg` | `#06140E` | `#FFFFFF` | text on accent |
| `--down` | `#8B7CF6` | `#6A5AE0` | downvote |
| `--danger` | `#E5534B` | `#C4372F` | destructive |
| `--warning` | `#D4A72C` | `#9A6B00` | warnings |
| `--info` | `#58A6FF` | `#1F6FEB` | informational |

All text/background pairs meet WCAG AA (4.5:1 for body, 3:1 for large/UI).

## Typography
- UI/body: **Inter** (self-hosted via `@fontsource-variable/inter`). Base 15px / 1.55.
- Titles & editorial: **Source Serif 4** (`@fontsource-variable/source-serif-4`).
- Code: **JetBrains Mono**.
- Scale: 12 · 13 · 15 · 17 · 20 · 24 · 30 (`text-xs … text-3xl`). Page titles never exceed 30px inside the app;
  the landing hero may use 48–56px.

## Spacing, radius, elevation
- 4px base grid (Tailwind default spacing).
- Radius: `sm 4px` (badges, inputs), `md 6px` (buttons, rows), `lg 10px` (dialogs, panels). No `rounded-3xl`.
- Shadows only for floating layers (menus, dialogs, toasts): `0 8px 24px rgb(0 0 0 / .35)` dark, `.12` light.

## Components (`frontend/src/components/ui`)
Button (primary / secondary / ghost / danger; sm / md; loading), IconButton, Input, Textarea, Select, Checkbox,
Switch, Label + FieldError, Dialog (focus-trapped, Esc closes), DropdownMenu (roving focus), Tabs, Tooltip,
Toast (polite live region), Badge, Avatar (initials fallback), Skeleton, EmptyState, ErrorState, Spinner,
Pagination/“Load more” sentinel.

## Layout & breakpoints
`sm 640 · md 768 · lg 1024 · xl 1280 · 2xl 1440`.
- `≥ xl`: left nav (240) · feed (≤ 720) · right rail (320).
- `lg`: left nav collapses to icons (64) · feed · right rail.
- `md`: no rail (its modules move below the feed header); left nav becomes a drawer.
- `< md`: top bar + bottom tab bar (Home, Explore, Create, Inbox, Profile); comment indentation caps at 4 levels
  visually, deeper threads continue in “Continue thread →”.

## States
Every data view implements: skeleton loading, empty state (icon + one sentence + an action), error state
(message + retry), and success toasts for mutations.
