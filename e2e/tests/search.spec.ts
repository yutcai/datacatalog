import { test, expect } from '@playwright/test'
import { registerApiUser, createDataset } from './helpers'

// The catalog is shared (parallel tests and local demo rows live in the same list),
// so every test seeds rows carrying its own unique marker and only asserts through
// that filter — never "the list holds exactly N rows".

test('finds a dataset by name', async ({ page, request }) => {
  const user = await registerApiUser(request)
  const name = `e2e-search-${Date.now()}`
  await createDataset(request, user, { name, description: 'search probe' })

  await page.goto('/')
  const search = page.getByRole('form', { name: 'Search' })
  await search.getByLabel('Search').fill(name)
  await search.getByRole('button', { name: 'Search' }).click()

  await expect(page.getByTestId('result-count')).toHaveText('1 result')
  await expect(page.getByRole('link', { name })).toBeVisible()
})

test('filters by tag and pages through the results', async ({ page, request }) => {
  const user = await registerApiUser(request)
  const tag = `e2e-page-${Date.now()}`
  // 11 rows against a page size of 10 — the smallest set that forces a second page.
  for (let i = 1; i <= 11; i++) {
    await createDataset(request, user, { name: `${tag} dataset ${i}`, tags: [tag] })
  }

  await page.goto('/')
  const search = page.getByRole('form', { name: 'Search' })
  await search.getByLabel('Tag').fill(tag)
  await search.getByRole('button', { name: 'Search' }).click()

  await expect(page.getByTestId('result-count')).toHaveText('11 results')
  await expect(page.getByTestId('page-info')).toHaveText('Page 1 of 2')
  await expect(page.getByRole('link', { name: tag })).toHaveCount(10)

  await page.getByRole('button', { name: 'Next' }).click()
  await expect(page.getByTestId('page-info')).toHaveText('Page 2 of 2')
  await expect(page.getByRole('link', { name: tag })).toHaveCount(1)
  await expect(page.getByRole('button', { name: 'Next' })).toBeDisabled()
})

test('shows the empty state when nothing matches', async ({ page }) => {
  await page.goto('/')
  const search = page.getByRole('form', { name: 'Search' })
  await search.getByLabel('Search').fill(`e2e-no-such-dataset-${Date.now()}`)
  await search.getByRole('button', { name: 'Search' }).click()

  await expect(page.getByTestId('empty')).toBeVisible()
  await expect(page.getByTestId('result-count')).toHaveText('0 results')
})
