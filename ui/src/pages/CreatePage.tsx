import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { api, ApiError } from '../api'

export default function CreatePage() {
  const navigate = useNavigate()
  const [name, setName] = useState('')
  const [team, setTeam] = useState('')
  const [description, setDescription] = useState('')
  const [tags, setTags] = useState('')
  const [error, setError] = useState('')
  const [busy, setBusy] = useState(false)

  async function onSubmit(e: React.FormEvent) {
    e.preventDefault()
    setError('')
    setBusy(true)
    try {
      const created = await api.create({
        name: name.trim(),
        team: team.trim() || undefined,
        description: description.trim() || undefined,
        tags: tags.split(',').map((t) => t.trim()).filter(Boolean),
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
        {error && <p className="error" role="alert">{error}</p>}
        <p style={{ marginTop: '1rem' }}>
          <button type="submit" disabled={busy}>Create</button>
        </p>
      </form>
    </main>
  )
}
