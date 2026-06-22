import { Link, Navigate, Route, Routes } from 'react-router-dom'
import { useAuth } from './auth'
import LoginPage from './pages/LoginPage'
import ListPage from './pages/ListPage'
import CreatePage from './pages/CreatePage'
import DetailPage from './pages/DetailPage'

function RequireAuth({ children }: { children: React.ReactNode }) {
  const { user, loading } = useAuth()
  if (loading) return <main><p>Loading…</p></main>
  if (!user) return <Navigate to="/login" replace />
  return <>{children}</>
}

export default function App() {
  const { user, logout } = useAuth()
  return (
    <>
      <header className="topbar">
        <strong>DataCatalog</strong>
        <nav aria-label="Main">
          <Link to="/">Datasets</Link>
          {user && <Link to="/new">New dataset</Link>}
        </nav>
        {user ? (
          <span>
            <span className="muted" data-testid="current-user">{user.username}</span>{' '}
            <button className="secondary" onClick={logout}>Log out</button>
          </span>
        ) : (
          <Link to="/login">Log in</Link>
        )}
      </header>
      <Routes>
        <Route path="/login" element={<LoginPage />} />
        <Route path="/" element={<RequireAuth><ListPage /></RequireAuth>} />
        <Route path="/new" element={<RequireAuth><CreatePage /></RequireAuth>} />
        <Route path="/datasets/:id" element={<RequireAuth><DetailPage /></RequireAuth>} />
      </Routes>
    </>
  )
}
