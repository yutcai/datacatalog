import { test, expect } from '@playwright/test'
import { readFileSync } from 'node:fs'

// Sample pattern #2 — assert the OUTCOME (a file actually downloads), not the mechanism.
//
// This is the test that catches the real "Download did nothing" bug: the old code called
// window.open() after an await, which browsers silently block as a popup. A shallow click
// test or a waitForEvent('popup') test would false-green here (Playwright's automated
// Chromium popup policy differs from a real browser). Waiting for the `download` event and
// checking the bytes is the only assertion that reliably catches it — and it's a browser-only
// bug the API/component tests structurally cannot see.
test('uploads a file and downloads it back, verifying the bytes', async ({ page }) => {
  const content = `hello e2e ${Date.now()}`
  const name = `e2e-download-${Date.now()}`

  // Create a dataset through the UI.
  await page.goto('/new')
  await page.getByLabel('Name', { exact: true }).fill(name)
  await page.getByRole('button', { name: 'Create' }).click()
  await expect(page.getByRole('heading', { name })).toBeVisible()

  // Upload a file (browser PUTs straight to S3 via the pre-signed URL).
  await page.getByLabel('File').setInputFiles({
    name: 'sample.txt',
    mimeType: 'text/plain',
    buffer: Buffer.from(content),
  })
  await page.getByRole('button', { name: 'Upload' }).click()
  await expect(page.getByTestId('upload-status')).toContainText('ACTIVE')

  // Download the version and assert a file actually arrives with the right bytes.
  const downloadPromise = page.waitForEvent('download')
  await page.getByTestId('version-row').getByRole('button', { name: 'Download' }).click()
  const download = await downloadPromise

  expect(download.suggestedFilename()).toContain('-v1')
  const path = await download.path()
  expect(readFileSync(path, 'utf8')).toBe(content)
})
