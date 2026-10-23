import { Link, NavLink, Outlet, useLocation, useNavigate } from 'react-router'
import { isAdmin } from '../api/auth'
import { useAuth } from '../auth/AuthContext'
import { APP_NAME } from '../config'

const YEAR = new Date().getFullYear()

export function Layout() {
  const { user, logout } = useAuth()
  const navigate = useNavigate()

  async function handleLogout() {
    // Leave the private page first; otherwise its guard redirects to the login page
    // as soon as the user is cleared.
    navigate('/')
    await logout()
  }

  const admin = isAdmin(user)
  const fullWidth = useLocation().pathname === '/'

  return (
    <>
      {/* Red bar for admins: a constant reminder that this account can change things */}
      <header className={admin ? 'site-header site-header-admin' : 'site-header'}>
        <div className="container header-inner">
          <span className="brand-row">
            <Link to={user ? '/sakums' : '/'} className="brand">
              <span className="brand-mark" aria-hidden="true">
                {APP_NAME.charAt(0)}
              </span>
              {APP_NAME}
            </Link>
            {admin && <span className="role-pill">Admin</span>}
          </span>
          <nav className="nav">
            {user ? (
              <>
                <NavLink to="/kursi">Kursi</NavLink>
                <NavLink to="/sakums">Mani kursi</NavLink>
                <NavLink to="/konts/drosiba">Drošība</NavLink>
                {isAdmin(user) && <NavLink to="/admin">Administrēšana</NavLink>}
                <button type="button" className="link-button" onClick={handleLogout}>
                  Iziet
                </button>
              </>
            ) : (
              <>
                <NavLink to="/kursi">Kursi</NavLink>
                <NavLink to="/pieslegties">Pieslēgties</NavLink>
                <Link to="/registreties" className="button button-small">
                  Reģistrēties
                </Link>
              </>
            )}
          </nav>
        </div>
      </header>
      {/* The landing page lays out its own full-width bands; other pages use the centred column */}
      {fullWidth ? (
        <main className="main main-full">
          <Outlet />
        </main>
      ) : (
        <main className="container main">
          <Outlet />
        </main>
      )}
      <footer className="site-footer">
        <div className="container muted">© {YEAR} {APP_NAME}</div>
      </footer>
    </>
  )
}
