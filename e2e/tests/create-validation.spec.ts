import { test, expect } from '@playwright/test'

// Both rejections must leave the user on the form with their input intact —
// asserting the URL pins "no navigation happened", not just "a message showed".

test('rejects malformed metadata JSON before anything reaches the API', async ({ page }) => {
  await page.goto('/new')
  await page.getByLabel('Name', { exact: true }).fill(`e2e-validation-${Date.now()}`)
  await page.getByLabel('Metadata (JSON, optional)').fill('{not json')
  await page.getByRole('button', { name: 'Create' }).click()

  await expect(page.getByRole('alert')).toHaveText('Metadata must be valid JSON')
  await expect(page).toHaveURL('/new')
})

test('surfaces the API rejection of a blank name', async ({ page }) => {
  await page.goto('/new')
  // Whitespace slips past the HTML `required` guard — the API's 400 is the real
  // gate, and the UI must translate it into a message instead of dying silently.
  await page.getByLabel('Name', { exact: true }).fill('   ')
  await page.getByRole('button', { name: 'Create' }).click()

  await expect(page.getByRole('alert')).toHaveText('Name is required')
  await expect(page).toHaveURL('/new')
})
