import { expect, test } from './fixtures';

/** Keyboard checks axe can't make: skip link, visible focus, dialog focus trap and focus return. */

test('skip link moves focus to the main content', async ({ sessions }) => {
  const page = await sessions.open('paperback_jo', '/home');
  await expect(page.getByRole('heading', { level: 1, name: 'Home feed' })).toBeVisible();
  await page.keyboard.press('Tab');
  const skip = page.getByRole('link', { name: 'Skip to content' });
  await expect(skip).toBeFocused();
  await expect(skip).toBeInViewport();
  await page.keyboard.press('Enter');
  await expect(page.getByRole('main')).toBeFocused();
  // The next Tab lands inside the main content, not back in the header or nav.
  await page.keyboard.press('Tab');
  expect(await page.evaluate(() => !!document.activeElement?.closest('main'))).toBe(true);
});

test('keyboard focus is visible', async ({ sessions }) => {
  const page = await sessions.open('paperback_jo', '/home');
  await expect(page.getByRole('heading', { level: 1, name: 'Home feed' })).toBeVisible();
  for (let i = 0; i < 6; i++) {
    await page.keyboard.press('Tab');
    const ring = await page.evaluate(() => {
      const el = document.activeElement as HTMLElement | null;
      if (!el || el === document.body) return 'none';
      const s = getComputedStyle(el);
      const outline = s.outlineStyle !== 'none' && parseFloat(s.outlineWidth) > 0;
      const shadow = s.boxShadow && s.boxShadow !== 'none';
      return outline || shadow ? 'visible' : `${el.tagName} ${el.getAttribute('aria-label') ?? el.textContent?.trim().slice(0, 30)}`;
    });
    expect(ring, 'focused element shows a focus indicator').toBe('visible');
  }
});

test('dialogs trap focus, close on Escape and return focus', async ({ sessions }) => {
  const page = await sessions.open('paperback_jo', '/messages');
  const opener = page.getByRole('main').getByRole('button', { name: 'New', exact: true });
  await opener.focus();
  await page.keyboard.press('Enter');
  const dialog = page.getByRole('dialog', { name: 'New message' });
  await expect(dialog).toBeVisible();
  await expect(dialog.getByRole('textbox', { name: 'Username' })).toBeFocused();
  for (let i = 0; i < 8; i++) {
    await page.keyboard.press('Tab');
    expect(await dialog.evaluate((d) => d.contains(document.activeElement))).toBe(true);
  }
  await page.keyboard.press('Escape');
  await expect(dialog).toBeHidden();
  await expect(opener).toBeFocused();
});
