import { createContext, useContext, useEffect, useState } from 'react'
import { api, getToken, setToken } from './api'

type User = { username: string }

type AuthContextValue = {
  user: User | null
  loading: boolean
  login: (username: string, password: string) => Promise<void>
  register: (username: string, password: string) => Promise<void>
  logout: () => void
}

const AuthContext = createContext<AuthContextValue | null>(null)

export function AuthProvider({ children }: { children: React.ReactNode }) {
  const [user, setUser] = useState<User | null>(null)
  // If a token is already stored, verify it via /v1/me before rendering guarded routes.
  const [loading, setLoading] = useState<boolean>(!!getToken())

  useEffect(() => {
    if (!getToken()) return
    api
      .me()
      .then((m) => setUser({ username: m.username }))
      .catch(() => setToken(null))
      .finally(() => setLoading(false))
  }, [])

  async function login(username: string, password: string) {
    const token = await api.login(username, password)
    setToken(token)
    const m = await api.me()
    setUser({ username: m.username })
  }

  async function register(username: string, password: string) {
    await api.register(username, password)
    await login(username, password)
  }

  function logout() {
    setToken(null)
    setUser(null)
  }

  return (
    <AuthContext.Provider value={{ user, loading, login, register, logout }}>
      {children}
    </AuthContext.Provider>
  )
}

export function useAuth(): AuthContextValue {
  const ctx = useContext(AuthContext)
  if (!ctx) throw new Error('useAuth must be used within AuthProvider')
  return ctx
}
