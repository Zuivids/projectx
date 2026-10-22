import { Navigate, Outlet, useLocation } from 'react-router'
import { isAdmin } from '../api/auth'
import { useAuth } from './AuthContext'

/**
 * These guards only decide what to show. The real protection is on the server: every API
 * call is checked there, so hiding a page here is a convenience, not security.
 */

export function RequireAuth() {
  const { user, loading } = useAuth()
  const location = useLocation()
  if (loading) return <p className="muted">Ielādē…</p>
  if (!user) return <Navigate to="/pieslegties" replace state={{ from: location.pathname }} />
  return <Outlet />
}

export function RequireAdmin() {
  const { user, loading } = useAuth()
  if (loading) return <p className="muted">Ielādē…</p>
  if (!user) return <Navigate to="/pieslegties" replace />
  if (!isAdmin(user)) return <Navigate to="/sakums" replace />
  // Admins must have passed MFA in this session (the server enforces the same rule)
  if (!user.mfaVerified) return <Navigate to="/konts/drosiba" replace state={{ adminNeedsMfa: true }} />
  return <Outlet />
}

/**
 * Login and registration pages redirect away when logged in. After a successful login this
 * sends the user back to the page they originally wanted (stored by RequireAuth).
 */
export function RedirectIfLoggedIn() {
  const { user, loading } = useAuth()
  const location = useLocation()
  if (loading) return <p className="muted">Ielādē…</p>
  if (user) {
    const from = (location.state as { from?: string } | null)?.from
    return <Navigate to={from ?? '/sakums'} replace />
  }
  return <Outlet />
}
