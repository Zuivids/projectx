import { Link, NavLink, Outlet, useNavigate } from 'react-router'
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

  return (
    <>
      <header className="site-header">
        <div className="container header-inner">
          <Link to={user ? '/sakums' : '/'} className="brand">
            {APP_NAME}
          </Link>
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
      <main className="container main">
        <Outlet />
      </main>
      <footer className="site-footer">
        <div className="container muted">© {YEAR} {APP_NAME}</div>
      </footer>
    </>
  )
}
