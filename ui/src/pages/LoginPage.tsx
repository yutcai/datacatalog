import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { useAuth } from '../auth'
import { ApiError } from '../api'

export default function LoginPage() {
  const { login, register } = useAuth()
  const navigate = useNavigate()
  const [mode, setMode] = useState<'login' | 'register'>('login')
  const [username, setUsername] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState('')
  const [busy, setBusy] = useState(false)

  async function onSubmit(e: React.FormEvent) {
    e.preventDefault()
    setError('')
    setBusy(true)
    try {
      if (mode === 'register') await register(username, password)
      else await login(username, password)
      navigate('/')
    } catch (err) {
      const msg =
        err instanceof ApiError && err.status === 401
          ? 'Invalid username or password'
          : err instanceof ApiError && err.status === 409
            ? 'That username is taken'
            : 'Something went wrong'
      setError(msg)
    } finally {
      setBusy(false)
    }
  }

  return (
    <main>
      <h1>{mode === 'login' ? 'Log in' : 'Create an account'}</h1>
      <form onSubmit={onSubmit} className="card" aria-label="Authentication">
        <label htmlFor="username">Username</label>
        <input
          id="username"
          name="username"
          value={username}
          onChange={(e) => setUsername(e.target.value)}
          autoComplete="username"
          required
        />
        <label htmlFor="password">Password</label>
        <input
          id="password"
          name="password"
          type="password"
          value={password}
          onChange={(e) => setPassword(e.target.value)}
          autoComplete="current-password"
          required
        />
        {error && <p className="error" role="alert">{error}</p>}
        <p style={{ marginTop: '1rem' }}>
          <button type="submit" disabled={busy}>
            {mode === 'login' ? 'Sign in' : 'Sign up'}
          </button>
        </p>
      </form>
      <p className="muted">
        {mode === 'login' ? 'Need an account?' : 'Already have one?'}{' '}
        <button
          type="button"
          className="secondary"
          onClick={() => {
            setMode(mode === 'login' ? 'register' : 'login')
            setError('')
          }}
        >
          {mode === 'login' ? 'Register' : 'Log in'}
        </button>
      </p>
    </main>
  )
}
