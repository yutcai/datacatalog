import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { api, ApiError } from '../api'

export default function CreatePage() {
  const navigate = useNavigate()
  const [name, setName] = useState('')
  const [team, setTeam] = useState('')
  const [description, setDescription] = useState('')
  const [tags, setTags] = useState('')
  const [metadataText, setMetadataText] = useState('')
  const [error, setError] = useState('')
  const [busy, setBusy] = useState(false)

  async function onSubmit(e: React.FormEvent) {
    e.preventDefault()
    setError('')
    let metadata: Record<string, unknown> | undefined
    if (metadataText.trim()) {
      try {
        metadata = JSON.parse(metadataText)
      } catch {
        setError('Metadata must be valid JSON')
        return
      }
    }
    setBusy(true)
    try {
      const created = await api.create({
        name: name.trim(),
        team: team.trim() || undefined,
        description: description.trim() || undefined,
        tags: tags.split(',').map((t) => t.trim()).filter(Boolean),
        metadata,
      })
      navigate(`/datasets/${created.id}`)
    } catch (err) {
      setError(err instanceof ApiError && err.status === 400 ? 'Name is required' : 'Could not create dataset')
    } finally {
      setBusy(false)
    }
  }

  return (
    <main>
      <h1>New dataset</h1>
      <form onSubmit={onSubmit} className="card" aria-label="Create dataset">
        <label htmlFor="name">Name</label>
        <input id="name" name="name" value={name} onChange={(e) => setName(e.target.value)} required />
        <label htmlFor="team">Team</label>
        <input id="team" name="team" value={team} onChange={(e) => setTeam(e.target.value)} />
        <label htmlFor="description">Description</label>
        <textarea id="description" name="description" rows={3} value={description}
                  onChange={(e) => setDescription(e.target.value)} />
        <label htmlFor="tags">Tags (comma-separated)</label>
        <input id="tags" name="tags" value={tags} onChange={(e) => setTags(e.target.value)}
               placeholder="sales, emea" />
        <label htmlFor="metadata">Metadata (JSON, optional)</label>
        <textarea id="metadata" name="metadata" rows={4} value={metadataText}
                  onChange={(e) => setMetadataText(e.target.value)}
                  placeholder={'{"region": "emea", "rows": 1000}'}
                  style={{ fontFamily: 'ui-monospace, monospace' }} />
        {error && <p className="error" role="alert">{error}</p>}
        <p style={{ marginTop: '1rem' }}>
          <button type="submit" disabled={busy}>Create</button>
        </p>
      </form>
    </main>
  )
}
