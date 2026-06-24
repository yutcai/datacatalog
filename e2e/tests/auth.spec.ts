import { test, expect } from '@playwright/test'

// Sample pattern #1 (cont.) — these tests run with the storage state saved by auth.setup.ts,
// so there is no login step here: the saved session lands us authenticated.
test('reuses the saved login — lands on the authenticated datasets page', async ({ page }) => {
  await page.goto('/')

  await expect(page.getByRole('button', { name: 'Log out' })).toBeVisible()
  await expect(page.getByRole('heading', { name: 'Datasets' })).toBeVisible()
})
