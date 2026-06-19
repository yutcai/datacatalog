import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { api, type DatasetPage } from '../api'

const LIMIT = 10

export default function ListPage() {
  const [q, setQ] = useState('')
  const [tag, setTag] = useState('')
  const [page, setPage] = useState(0)
  const [data, setData] = useState<DatasetPage | null>(null)
  const [error, setError] = useState('')

  async function load(nextPage = page) {
    setError('')
    try {
      const params = new URLSearchParams({ page: String(nextPage), limit: String(LIMIT) })
      if (q.trim()) params.set('q', q.trim())
      if (tag.trim()) params.set('tag', tag.trim())
      setData(await api.search(params))
      setPage(nextPage)
    } catch {
      setError('Could not load datasets')
    }
  }

  useEffect(() => {
    load(0)
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  function onSearch(e: React.FormEvent) {
    e.preventDefault()
    load(0)
  }

  const total = data?.total ?? 0
  const lastPage = Math.max(0, Math.ceil(total / LIMIT) - 1)

  return (
    <main>
      <h1>Datasets</h1>
      <form onSubmit={onSearch} className="row" aria-label="Search">
        <span style={{ flex: 2 }}>
          <label htmlFor="q">Search</label>
          <input id="q" name="q" value={q} onChange={(e) => setQ(e.target.value)}
                 placeholder="name or description" />
        </span>
        <span style={{ flex: 1 }}>
          <label htmlFor="tag">Tag</label>
          <input id="tag" name="tag" value={tag} onChange={(e) => setTag(e.target.value)} />
        </span>
        <button type="submit">Search</button>
      </form>

      {error && <p className="error" role="alert">{error}</p>}

      {data && (
        <>
          <p className="muted" data-testid="result-count">{total} result{total === 1 ? '' : 's'}</p>
          {data.items.length === 0 ? (
            <p data-testid="empty">No datasets match.</p>
          ) : (
            <table>
              <thead>
                <tr><th>Name</th><th>Owner</th><th>Tags</th></tr>
              </thead>
              <tbody>
                {data.items.map((d) => (
                  <tr key={d.id}>
                    <td><Link to={`/datasets/${d.id}`}>{d.name}</Link></td>
                    <td>{d.ownerUsername}</td>
                    <td>{d.tags.join(', ')}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          )}
          <div className="row" style={{ marginTop: '1rem' }}>
            <button className="secondary" onClick={() => load(page - 1)} disabled={page <= 0}>
              Previous
            </button>
            <span className="muted" data-testid="page-info">Page {page + 1} of {lastPage + 1}</span>
            <button className="secondary" onClick={() => load(page + 1)} disabled={page >= lastPage}>
              Next
            </button>
          </div>
        </>
      )}
    </main>
  )
}
