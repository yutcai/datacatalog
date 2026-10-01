import { test, expect } from '@playwright/test'
import { registerApiUser, createDataset } from './helpers'

test('the owner edits the description and metadata merges by key', async ({ page }) => {
  const name = `e2e-edit-${Date.now()}`
  await page.goto('/new')
  await page.getByLabel('Name', { exact: true }).fill(name)
  await page.getByLabel('Metadata (JSON, optional)').fill('{"format": "csv"}')
  await page.getByRole('button', { name: 'Create' }).click()
  await expect(page.getByRole('heading', { name })).toBeVisible()

  const edit = page.getByRole('form', { name: 'Edit dataset' })
  await edit.getByLabel('Description').fill('updated by the e2e suite')
  await edit.getByLabel('Metadata (JSON — merged by key)').fill('{"reviewed": true}')
  await edit.getByRole('button', { name: 'Save' }).click()

  await expect(page.getByTestId('edit-status')).toHaveText('Saved')
  // Not plain getByText: the same string sits in the edit textarea, and strict
  // mode rightly flags that ambiguity — narrow to the rendered paragraph.
  await expect(
    page.getByRole('paragraph').filter({ hasText: 'updated by the e2e suite' }),
  ).toBeVisible()
  // Merged by key, not replaced: the key from create must survive the edit.
  await expect(page.getByTestId('metadata')).toContainText('"format": "csv"')
  await expect(page.getByTestId('metadata')).toContainText('"reviewed": true')
})

test('a non-owner sees the 403 surfaced as a message', async ({ page, request }) => {
  // The dataset belongs to a fresh API-created user; the browser session (from
  // auth.setup) is somebody else, so the PATCH comes back 403.
  const owner = await registerApiUser(request)
  const created = await createDataset(request, owner, {
    name: `e2e-forbidden-${Date.now()}`,
    description: 'owned by someone else',
  })

  await page.goto(`/datasets/${created.id}`)
  const edit = page.getByRole('form', { name: 'Edit dataset' })
  await edit.getByLabel('Description').fill('should not stick')
  await edit.getByRole('button', { name: 'Save' }).click()

  await expect(page.getByTestId('edit-status')).toHaveText('Only the owner can edit this dataset')
})
