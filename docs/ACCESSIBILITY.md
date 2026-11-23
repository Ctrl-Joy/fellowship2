# Accessibility

Target: WCAG 2.2 AA. Checked automatically by `frontend/e2e/08-accessibility.spec.ts` (axe-core) and
`frontend/e2e/09-keyboard.spec.ts` (keyboard behaviour axe can't see). Both run in CI with the rest of the
end-to-end suite (see `docs/FRONTEND.md`, "End-to-end tests").

## How the audit runs
- **Pages:** landing (signed out), home feed, post page (seeded Rust vs Go thread with comments), community page
  (`c/programming`), messages, files, leaderboard, settings, admin dashboard (as `admin_demo`), moderator funding.
- **Matrix:** each page in the dark and light themes, at 1280 px and 375 px wide (40 scans).
- **Rules:** axe tags `wcag2a`, `wcag2aa`, `wcag21a`, `wcag21aa`, `wcag22aa` and `best-practice`.
  Any **serious** or **critical** violation fails the test; every scan's full results (including minor and moderate)
  are attached to the test report as JSON.
- **Keyboard:** the first Tab reaches "Skip to content", which moves focus to `<main>`; the next six tab stops all show a
  focus indicator; a dialog takes focus, keeps Tab inside, closes on Escape and returns focus to its opener.

Result on 2026-11-23: **0 serious or critical violations** on all 40 scans; the keyboard checks pass.

## What the audit found and how it was fixed
| Finding (axe rule) | Where | Fix |
|---|---|---|
| Text contrast below 4.5:1 (`color-contrast`) | `--text-faint` in both themes (4.4:1 dark, 3.5:1 light), light `--accent` (3.4:1), light `--warning`, `--info`, `--danger` on tinted badges, white on the dark-theme brand button (3.4:1) | Tokens in `design-system/tokens.css`: faint `138 148 153` dark / `99 108 113` light, light accent `0 110 76`, warning `133 92 0`, info `24 94 200`, danger `176 45 38`, dark `--brand-fg` near-black (5.7:1 on the indigo). Brand and dark-theme colours are otherwise unchanged. |
| Flair and stance chips drew their label in an arbitrary community colour (2.3:1 in light) | `Badge color=…`, Context Mode `StanceChip` | The custom colour now tints the chip and a dot/icon only; the label uses the text colour, so any flair colour is readable in both themes. |
| Faded text in the landing illustration (`opacity` on the layer rows) | `HeroIllustration` (since replaced by the Howlers landing, `routes/landing/`) | Opacity removed; the layers still read as decreasing through their bar counts. |
| Buttons with no accessible name at 375 px (`button-name`, critical) | Save/Share pills on feed cards hid their text with `hidden sm:inline` | Text is `sr-only sm:not-sr-only`: hidden visually on phones, still announced. |
| Visible text not part of the accessible name (`label-content-name-mismatch`) | Account menu (avatar initials), leaderboard score button | Avatar initials are decorative generated content (`data-initials` + `::before`, `aria-hidden`), the menu is "Account menu, {username}". The score button is named by its own content plus a screen-reader prefix. |
| Interactive controls nested in another control | Leaderboard rows: profile and community links inside `<summary>` | Rows are plain: links stay links, and a separate disclosure button (`aria-expanded`, `aria-controls`) opens the breakdown. |
| `<dl>` with non-`dt`/`dd` children (`definition-list`) | Community sidebar ("Created", visibility) | Icons moved inside the `<dd>`. |
| Unlabelled form fields (`label`, critical) | Hidden file inputs behind the avatar/banner "Upload" buttons in Settings | The input is `aria-hidden` (the visible button is the control) and carries the button's label. |
| Touch targets under 24 px (`target-size`, WCAG 2.2) | Community and author links in the post header; comment "⋯" menus squeezed to 16 px at 375 px | Links are `min-h-6` flex items; the menu button doesn't shrink and the comment action row wraps. |
| Focus not returned after closing a dialog | Every `Dialog` (opened from buttons and menu items, not a Radix trigger) | `components/ui/Dialog.tsx` remembers the element focused at open time and refocuses it on close. |
| Skip link target not focusable | `<main id="main">` in both layouts | `tabIndex={-1}` (no outline on the container), so "Skip to content" really moves focus. |

Other accessibility fixes made while writing the end-to-end suite (each found because an element had no usable
accessible name or role): the post page's comment-count button was named "7" (now "7 comments"); mod-queue items are
articles named by their title; the DM thread is a `role="log"` region named "Conversation with …"; each Community AI
answer is an article named "Community AI answer" with its notice as a `note`; the library page no longer nests a second
`<main>` inside the app's `<main>`.

## Known limits (not failures, but worth knowing)
- axe can't judge everything: reading order of complex widgets, the quality of alt text people write, and live-region
  verbosity still need a manual screen-reader pass (not done in this phase: no screen reader was available in the
  automated environment).
- The audit covers the ten pages above. Other pages share the same components and tokens but weren't scanned
  (podcast studio, community settings, mod tools, library, AI chat, notifications, onboarding, share page).
- Charts (Recharts in the admin overview and Pulse results) expose a text summary through `role="img"` + `aria-label`;
  individual bars aren't separately navigable.
- Moderate and minor axe findings are attached to the test report but don't fail the build, and they weren't triaged
  in this pass. Triage them next; anything kept should be listed here with a reason.
