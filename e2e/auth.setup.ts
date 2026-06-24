import { test as setup, expect } from '@playwright/test'

// Sample pattern #1 — log in once, reuse the authenticated state across tests.
// Registering a fresh user (per run) through the UI auto-logs-in; we then snapshot
// the browser storage (the JWT lives in localStorage) so other tests skip login.
const authFile = 'playwright/.auth/user.json'

setup('register and log in, then save storage state', async ({ page }) => {
  const username = `e2e-${Date.now()}-${Math.floor(Math.random() * 1e6)}`

  await page.goto('/login')
  await page.getByRole('button', { name: 'Register' }).click() // switch to register mode
  await page.getByLabel('Username').fill(username)
  await page.getByLabel('Password').fill('pw-12345')
  await page.getByRole('button', { name: 'Sign up' }).click()

  // Registering logs in and redirects home; the username appears in the top bar.
  await expect(page.getByTestId('current-user')).toHaveText(username)

  await page.context().storageState({ path: authFile })
})
