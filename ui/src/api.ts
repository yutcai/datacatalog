// Thin typed client over the catalog API. One origin: the dev server (Vite) and the
// built image (nginx) both proxy /v1 and /health to the Spring app, so no CORS here.

export type Dataset = {
  id: string
  name: string
  ownerUsername: string | null
  team: string | null
  description: string | null
  tags: string[]
  metadata: Record<string, unknown>
  createdAt: string
  updatedAt: string
}

export type DatasetPage = {
  items: Dataset[]
  page: number
  limit: number
  total: number
}

export type Me = { id: string; username: string }

export type RequestUpload = {
  versionId: string
  versionNumber: number
  s3Key: string
  uploadUrl: string
}

// Matches the backend VersionResponse: the version id field is `id` (note: the
// request-upload response calls it `versionId` — the two responses differ).
export type Version = {
  id: string
  datasetId: string
  versionNumber: number
  state: string
  sizeBytes: number | null
  checksum: string | null
  createdAt: string
}

export class ApiError extends Error {
  status: number
  body: string
  constructor(status: number, body: string) {
    super(`HTTP ${status}`)
    this.status = status
    this.body = body
  }
}

let token: string | null = localStorage.getItem('token')

export function setToken(t: string | null) {
  token = t
  if (t) localStorage.setItem('token', t)
  else localStorage.removeItem('token')
}

export function getToken() {
  return token
}

async function http(path: string, opts: RequestInit = {}): Promise<Response> {
  const headers = new Headers(opts.headers)
  if (token) headers.set('Authorization', `Bearer ${token}`)
  if (opts.body != null && !headers.has('Content-Type')) {
    headers.set('Content-Type', 'application/json')
  }
  return fetch(path, { ...opts, headers })
}

async function unwrap<T>(res: Response): Promise<T> {
  if (!res.ok) throw new ApiError(res.status, await res.text().catch(() => ''))
  if (res.status === 204) return undefined as T
  return res.json() as Promise<T>
}

export const api = {
  async register(username: string, password: string): Promise<void> {
    const res = await http('/v1/auth/register', {
      method: 'POST',
      body: JSON.stringify({ username, password }),
    })
    if (!res.ok) throw new ApiError(res.status, await res.text().catch(() => ''))
  },

  async login(username: string, password: string): Promise<string> {
    const res = await http('/v1/auth/token', {
      method: 'POST',
      body: JSON.stringify({ username, password }),
    })
    const data = await unwrap<{ accessToken: string }>(res)
    return data.accessToken
  },

  me: () => http('/v1/me').then((r) => unwrap<Me>(r)),

  search: (params: URLSearchParams) =>
    http(`/v1/datasets?${params.toString()}`).then((r) => unwrap<DatasetPage>(r)),

  get: (id: string) => http(`/v1/datasets/${id}`).then((r) => unwrap<Dataset>(r)),

  create: (body: {
    name: string
    team?: string
    description?: string
    tags?: string[]
    metadata?: Record<string, unknown>
  }) =>
    http('/v1/datasets', { method: 'POST', body: JSON.stringify(body) }).then((r) =>
      unwrap<Dataset>(r),
    ),

  patch: (id: string, body: Partial<Pick<Dataset, 'name' | 'team' | 'description' | 'tags' | 'metadata'>>) =>
    http(`/v1/datasets/${id}`, { method: 'PATCH', body: JSON.stringify(body) }).then((r) =>
      unwrap<Dataset>(r),
    ),

  listVersions: (id: string) =>
    http(`/v1/datasets/${id}/versions`).then((r) => unwrap<Version[]>(r)),

  requestUpload: (id: string) =>
    http(`/v1/datasets/${id}/versions`, { method: 'POST' }).then((r) =>
      unwrap<RequestUpload>(r),
    ),

  complete: (id: string, versionId: string) =>
    http(`/v1/datasets/${id}/versions/${versionId}/complete`, { method: 'POST' }).then((r) =>
      unwrap<Version>(r),
    ),

  download: (id: string, versionId: string) =>
    http(`/v1/datasets/${id}/versions/${versionId}/download`).then((r) =>
      unwrap<{ downloadUrl: string }>(r),
    ),
}
