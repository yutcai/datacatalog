import { defineConfig, devices } from '@playwright/test'

// E2E targets the served UI (nginx serves the SPA and proxies the API at one origin).
// Bring the stack up with `docker compose up` first, then `npm test`.
const baseURL = process.env.BASE_URL ?? 'http://localhost:3000'
const authFile = 'playwright/.auth/user.json'

export default defineConfig({
  testDir: '.',
  fullyParallel: true,
  forbidOnly: !!process.env.CI,
  retries: process.env.CI ? 1 : 0,
  reporter: process.env.CI ? [['html', { open: 'never' }], ['list']] : 'html',
  use: {
    baseURL,
    trace: 'on-first-retry',
  },
  projects: [
    // Logs in once via the UI and saves the authenticated storage state.
    { name: 'setup', testMatch: /.*\.setup\.ts/ },
    {
      name: 'chromium',
      testMatch: /tests\/.*\.spec\.ts/,
      use: { ...devices['Desktop Chrome'], storageState: authFile },
      dependencies: ['setup'],
    },
  ],
})
