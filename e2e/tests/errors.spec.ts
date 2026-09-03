import { test, expect } from '@playwright/test'

test('shows Not found for an unknown dataset id', async ({ page }) => {
  // Well-formed UUID that cannot exist — exercises the 404 path, not input parsing.
  await page.goto('/datasets/00000000-0000-0000-0000-000000000000')

  await expect(page.getByRole('heading', { name: 'Not found' })).toBeVisible()
  await expect(page.getByText('No dataset with that id.')).toBeVisible()
})

test.describe('unauthenticated', () => {
  // Override the suite-wide saved login with an empty state: this test must
  // start logged out, while every other test in the project starts logged in.
  test.use({ storageState: { cookies: [], origins: [] } })

  test('visiting the catalog without a session lands on the login page', async ({ page }) => {
    await page.goto('/')

    await expect(page).toHaveURL('/login')
    await expect(page.getByRole('heading', { name: 'Log in' })).toBeVisible()
  })
})
