import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from 'react'
import { authApi, type Me } from '../api/auth'
import { ApiError } from '../api/client'

interface AuthState {
  /** Logged-in user, or null when logged out */
  user: Me | null
  /** True until the first check with the server has finished */
  loading: boolean
  /** Reloads the user from the server, e.g. after login or MFA setup */
  refresh: () => Promise<Me | null>
  logout: () => Promise<void>
}

const AuthContext = createContext<AuthState | null>(null)

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<Me | null>(null)
  const [loading, setLoading] = useState(true)

  const refresh = useCallback(async () => {
    try {
      const me = await authApi.me()
      setUser(me)
      return me
    } catch (error) {
      // 401 means logged out; any other failure (server down) is treated the same way
      if (!(error instanceof ApiError && error.status === 401)) {
        console.error('Could not load the current user', error)
      }
      setUser(null)
      return null
    } finally {
      setLoading(false)
    }
  }, [])

  const logout = useCallback(async () => {
    try {
      await authApi.logout()
    } finally {
      setUser(null)
    }
  }, [])

  // Ask the server once on startup whether there is a logged-in session.
  // State is set after the network call, not synchronously, so the lint rule does not apply.
  useEffect(() => {
    // eslint-disable-next-line react/set-state-in-effect
    void refresh()
  }, [refresh])

  const value = useMemo(() => ({ user, loading, refresh, logout }), [user, loading, refresh, logout])
  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}

// eslint-disable-next-line react/only-export-components
export function useAuth(): AuthState {
  const context = useContext(AuthContext)
  if (!context) {
    throw new Error('useAuth must be used inside AuthProvider')
  }
  return context
}
