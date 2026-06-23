import { useEffect, useState } from 'react'
import { useParams } from 'react-router-dom'
import { api, ApiError, type Dataset, type Version } from '../api'

export default function DetailPage() {
  const { id = '' } = useParams()
  const [dataset, setDataset] = useState<Dataset | null>(null)
  const [notFound, setNotFound] = useState(false)
  const [versions, setVersions] = useState<Version[]>([])

  const [file, setFile] = useState<File | null>(null)
  const [uploadMsg, setUploadMsg] = useState('')
  const [downloadMsg, setDownloadMsg] = useState('')
  const [busy, setBusy] = useState(false)

  const [description, setDescription] = useState('')
  const [metadataText, setMetadataText] = useState('')
  const [editMsg, setEditMsg] = useState('')

  useEffect(() => {
    api
      .get(id)
      .then((d) => {
        setDataset(d)
        setDescription(d.description ?? '')
        setMetadataText(JSON.stringify(d.metadata ?? {}, null, 2))
      })
      .catch((err) => {
        if (err instanceof ApiError && err.status === 404) setNotFound(true)
      })
    loadVersions()
  }, [id])

  async function loadVersions() {
    try {
      setVersions(await api.listVersions(id))
    } catch {
      /* leave the list as-is */
    }
  }

  async function onUpload(e: React.FormEvent) {
    e.preventDefault()
    if (!file) return
    setUploadMsg('')
    setBusy(true)
    try {
      const req = await api.requestUpload(id)
      // Bytes go straight to S3 via the pre-signed URL — never through the API.
      const put = await fetch(req.uploadUrl, { method: 'PUT', body: file })
      if (!put.ok) throw new Error(`S3 PUT ${put.status}`)
      const v = await api.complete(id, req.versionId)
      setUploadMsg(`Uploaded version ${v.versionNumber} — ${v.state}`)
      setFile(null)
      await loadVersions() // new version now shows in the history and is downloadable
    } catch {
      setUploadMsg('Upload failed')
    } finally {
      setBusy(false)
    }
  }

  async function onDownload(v: Version) {
    setDownloadMsg('')
    try {
      const { downloadUrl } = await api.download(id, v.id)
      // Fetch the bytes and trigger a real download. (window.open after an await loses the
      // click's user-gesture context and gets silently blocked as a popup.)
      const res = await fetch(downloadUrl)
      if (res.status === 404) {
        // The version row is in Postgres but its object isn't in S3 — local LocalStack
        // resets when its container is rebuilt (the DB persists, S3 doesn't).
        setDownloadMsg('File is no longer in local storage — re-upload to restore.')
        return
      }
      if (!res.ok) throw new Error(`storage ${res.status}`)
      const blob = await res.blob()
      const url = URL.createObjectURL(blob)
      const a = document.createElement('a')
      a.href = url
      a.download = `${dataset?.name ?? 'dataset'}-v${v.versionNumber}`
      document.body.appendChild(a)
      a.click()
      a.remove()
      // Revoke later: revoking immediately can abort the download in some browsers.
      setTimeout(() => URL.revokeObjectURL(url), 10_000)
      setDownloadMsg(`Downloaded v${v.versionNumber}`)
    } catch (err) {
      setDownloadMsg(`Download failed (${err instanceof Error ? err.message : 'error'})`)
    }
  }

  async function onSaveEdit(e: React.FormEvent) {
    e.preventDefault()
    setEditMsg('')
    let metadata: Record<string, unknown>
    try {
      metadata = metadataText.trim() ? JSON.parse(metadataText) : {}
    } catch {
      setEditMsg('Metadata must be valid JSON')
      return
    }
    try {
      const updated = await api.patch(id, { description, metadata })
      setDataset(updated)
      setMetadataText(JSON.stringify(updated.metadata ?? {}, null, 2))
      setEditMsg('Saved')
    } catch (err) {
      setEditMsg(
        err instanceof ApiError && err.status === 403
          ? 'Only the owner can edit this dataset'
          : 'Save failed',
      )
    }
  }

  if (notFound) return <main><h1>Not found</h1><p>No dataset with that id.</p></main>
  if (!dataset) return <main><p>Loading…</p></main>

  return (
    <main>
      <h1>{dataset.name}</h1>
      <div className="card">
        <p className="muted">Owner: {dataset.ownerUsername} · Team: {dataset.team ?? '—'}</p>
        <p>{dataset.description || <span className="muted">No description</span>}</p>
        <p><strong>Tags:</strong> {dataset.tags.length ? dataset.tags.join(', ') : '—'}</p>
        <p><strong>Metadata:</strong></p>
        <pre data-testid="metadata">{JSON.stringify(dataset.metadata, null, 2)}</pre>
      </div>

      <div className="card">
        <h2 style={{ fontSize: '1.1rem' }}>Versions</h2>
        {versions.length === 0 ? (
          <p className="muted" data-testid="no-versions">No versions yet — upload one below.</p>
        ) : (
          <table>
            <thead>
              <tr><th>Version</th><th>State</th><th>Size</th><th>Created</th><th></th></tr>
            </thead>
            <tbody>
              {versions.map((v) => (
                <tr key={v.id} data-testid="version-row">
                  <td>v{v.versionNumber}</td>
                  <td>{v.state}</td>
                  <td>{v.sizeBytes ?? '—'} bytes</td>
                  <td>{new Date(v.createdAt).toLocaleString()}</td>
                  <td>
                    <button type="button" className="secondary" onClick={() => onDownload(v)}>
                      Download
                    </button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
        {downloadMsg && <p data-testid="download-status">{downloadMsg}</p>}
      </div>

      <form onSubmit={onUpload} className="card" aria-label="Upload version">
        <h2 style={{ fontSize: '1.1rem' }}>Upload a new version</h2>
        <label htmlFor="file">File</label>
        <input id="file" name="file" type="file"
               onChange={(e) => setFile(e.target.files?.[0] ?? null)} />
        <p style={{ marginTop: '0.8rem' }}>
          <button type="submit" disabled={!file || busy}>Upload</button>
        </p>
        {uploadMsg && <p data-testid="upload-status">{uploadMsg}</p>}
      </form>

      <form onSubmit={onSaveEdit} className="card" aria-label="Edit dataset">
        <h2 style={{ fontSize: '1.1rem' }}>Edit</h2>
        <label htmlFor="edit-description">Description</label>
        <textarea id="edit-description" rows={3} value={description}
                  onChange={(e) => setDescription(e.target.value)} />
        <label htmlFor="edit-metadata">Metadata (JSON — merged by key)</label>
        <textarea id="edit-metadata" rows={5} value={metadataText}
                  onChange={(e) => setMetadataText(e.target.value)}
                  style={{ fontFamily: 'ui-monospace, monospace' }} />
        <p style={{ marginTop: '0.8rem' }}>
          <button type="submit">Save</button>
        </p>
        {editMsg && <p data-testid="edit-status">{editMsg}</p>}
      </form>
    </main>
  )
}
