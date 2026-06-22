import { useEffect, useState } from 'react'
import { useParams } from 'react-router-dom'
import { api, ApiError, type Dataset, type Version } from '../api'

export default function DetailPage() {
  const { id = '' } = useParams()
  const [dataset, setDataset] = useState<Dataset | null>(null)
  const [notFound, setNotFound] = useState(false)

  const [file, setFile] = useState<File | null>(null)
  const [version, setVersion] = useState<Version | null>(null)
  const [uploadMsg, setUploadMsg] = useState('')
  const [downloadMsg, setDownloadMsg] = useState('')
  const [busy, setBusy] = useState(false)

  const [description, setDescription] = useState('')
  const [editMsg, setEditMsg] = useState('')

  useEffect(() => {
    api
      .get(id)
      .then((d) => {
        setDataset(d)
        setDescription(d.description ?? '')
      })
      .catch((err) => {
        if (err instanceof ApiError && err.status === 404) setNotFound(true)
      })
  }, [id])

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
      setVersion(v)
      setUploadMsg(`Uploaded version ${v.versionNumber} — ${v.state}`)
    } catch {
      setUploadMsg('Upload failed')
    } finally {
      setBusy(false)
    }
  }

  async function onDownload() {
    if (!version) return
    setDownloadMsg('')
    try {
      const { downloadUrl } = await api.download(id, version.versionId)
      // Fetch the bytes and trigger a real download. (window.open after an await loses the
      // click's user-gesture context and gets silently blocked as a popup.)
      const res = await fetch(downloadUrl)
      if (!res.ok) throw new Error(`storage ${res.status}`)
      const blob = await res.blob()
      const url = URL.createObjectURL(blob)
      const a = document.createElement('a')
      a.href = url
      a.download = `${dataset?.name ?? 'dataset'}-v${version.versionNumber}`
      document.body.appendChild(a)
      a.click()
      a.remove()
      // Revoke later: revoking immediately can abort the download in some browsers.
      setTimeout(() => URL.revokeObjectURL(url), 10_000)
      setDownloadMsg('Downloaded')
    } catch (err) {
      setDownloadMsg(`Download failed (${err instanceof Error ? err.message : 'error'})`)
    }
  }

  async function onSaveDescription(e: React.FormEvent) {
    e.preventDefault()
    setEditMsg('')
    try {
      const updated = await api.patch(id, { description })
      setDataset(updated)
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

      <form onSubmit={onUpload} className="card" aria-label="Upload version">
        <h2 style={{ fontSize: '1.1rem' }}>Upload a new version</h2>
        <label htmlFor="file">File</label>
        <input id="file" name="file" type="file"
               onChange={(e) => setFile(e.target.files?.[0] ?? null)} />
        <p style={{ marginTop: '0.8rem' }}>
          <button type="submit" disabled={!file || busy}>Upload</button>
        </p>
        {uploadMsg && <p data-testid="upload-status">{uploadMsg}</p>}
        {version?.state === 'ACTIVE' && (
          <button type="button" className="secondary" onClick={onDownload}>Download</button>
        )}
        {downloadMsg && <p data-testid="download-status">{downloadMsg}</p>}
      </form>

      <form onSubmit={onSaveDescription} className="card" aria-label="Edit description">
        <h2 style={{ fontSize: '1.1rem' }}>Edit description</h2>
        <label htmlFor="edit-description">Description</label>
        <textarea id="edit-description" rows={3} value={description}
                  onChange={(e) => setDescription(e.target.value)} />
        <p style={{ marginTop: '0.8rem' }}>
          <button type="submit">Save</button>
        </p>
        {editMsg && <p data-testid="edit-status">{editMsg}</p>}
      </form>
    </main>
  )
}
