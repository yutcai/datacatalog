import type { APIRequestContext } from '@playwright/test'
import { expect } from '@playwright/test'

// Seed state through the API, not the UI: registering users and creating datasets
// here is setup, not the behavior under test, and the API is orders of magnitude
// faster than driving forms. Each caller registers its own throwaway user, so tests
// stay isolated even though the catalog list is shared across the whole stack.
//
// The `request` fixture carries no auth (the UI keeps its JWT in localStorage,
// which never reaches an APIRequestContext), so the token is passed explicitly.

export type ApiUser = { username: string; token: string }

export async function registerApiUser(request: APIRequestContext): Promise<ApiUser> {
  const username = `e2e-api-${Date.now()}-${Math.floor(Math.random() * 1e6)}`
  const password = 'pw-12345'

  const registered = await request.post('/v1/auth/register', { data: { username, password } })
  expect(registered.ok()).toBeTruthy()

  const token = await request.post('/v1/auth/token', { data: { username, password } })
  expect(token.ok()).toBeTruthy()
  const { accessToken } = await token.json()
  return { username, token: accessToken }
}

export async function createDataset(
  request: APIRequestContext,
  user: ApiUser,
  body: {
    name: string
    team?: string
    description?: string
    tags?: string[]
    metadata?: Record<string, unknown>
  },
): Promise<{ id: string; name: string }> {
  const res = await request.post('/v1/datasets', {
    headers: { Authorization: `Bearer ${user.token}` },
    data: body,
  })
  expect(res.ok()).toBeTruthy()
  return res.json()
}
