import { createContext, useCallback, useContext, useEffect, useMemo, useRef, useState, type ReactNode } from 'react'
import { authApi, type Me } from '../api/auth'
import { ApiError, onSessionLost } from '../api/client'

interface AuthState {
  /** Logged-in user, or null when logged out */
  user: Me | null
  /** True until the first check with the server has finished */
  loading: boolean
  /** The server ended the session (timeout or account disabled); shown on the login page */
  sessionExpired: boolean
  /** Reloads the user from the server, e.g. after login or MFA setup */
  refresh: () => Promise<Me | null>
  logout: () => Promise<void>
}

const AuthContext = createContext<AuthState | null>(null)

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<Me | null>(null)
  const [loading, setLoading] = useState(true)
  const [sessionExpired, setSessionExpired] = useState(false)
  // Read inside listeners, which would otherwise see a stale user
  const userRef = useRef<Me | null>(null)
  useEffect(() => {
    userRef.current = user
  }, [user])

  const refresh = useCallback(async () => {
    try {
      const me = await authApi.me()
      setUser(me)
      setSessionExpired(false)
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

  // Any 401 while logged in means the server ended the session: log out in the UI too
  useEffect(
    () =>
      onSessionLost(() => {
        if (userRef.current) {
          setUser(null)
          setSessionExpired(true)
        }
      }),
    [],
  )

  // When the user comes back to the tab, check that the session is still alive, so an
  // expired session is noticed before they start filling in a form
  useEffect(() => {
    function onVisible() {
      if (document.visibilityState === 'visible' && userRef.current) {
        void refresh()
      }
    }
    document.addEventListener('visibilitychange', onVisible)
    return () => document.removeEventListener('visibilitychange', onVisible)
  }, [refresh])

  const value = useMemo(
    () => ({ user, loading, sessionExpired, refresh, logout }),
    [user, loading, sessionExpired, refresh, logout],
  )
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
