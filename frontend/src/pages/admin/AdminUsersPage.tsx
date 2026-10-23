import { useState, type FormEvent } from 'react'
import { Link } from 'react-router'
import { userAdminApi, type UserStatus } from '../../api/users'
import { ROLE_LABELS, USER_STATUS_LABELS } from '../../lib/format'
import { useLoad } from '../../lib/useLoad'

const FILTERS: { label: string; status?: UserStatus }[] = [
  { label: 'Visi' },
  { label: 'Aktīvi', status: 'ACTIVE' },
  { label: 'Bloķēti', status: 'DISABLED' },
]

export function AdminUsersPage() {
  const [input, setInput] = useState('')
  const [query, setQuery] = useState('')
  const [status, setStatus] = useState<UserStatus | undefined>()
  const { data, error, loading } = useLoad(() => userAdminApi.list(query, status), `${query}|${status ?? ''}`)

  function search(event: FormEvent) {
    event.preventDefault()
    setQuery(input)
  }

  return (
    <>
      <p>
        <Link to="/admin">← Administrēšana</Link>
      </p>
      <h1>Lietotāji</h1>

      <form onSubmit={search} className="inline-form" role="search">
        <input
          value={input}
          onChange={(e) => setInput(e.target.value)}
          placeholder="E-pasts vai vārds"
          aria-label="Meklēt lietotāju"
        />
        <button type="submit" className="button button-secondary">
          Meklēt
        </button>
      </form>

      <div className="tabs" role="tablist">
        {FILTERS.map((f) => (
          <button
            key={f.label}
            type="button"
            role="tab"
            aria-selected={status === f.status}
            className={status === f.status ? 'tab active' : 'tab'}
            onClick={() => setStatus(f.status)}
          >
            {f.label}
          </button>
        ))}
      </div>

      {loading && <p className="muted">Ielādē…</p>}
      {error && <p className="form-error">{error}</p>}
      {data && data.items.length === 0 && <p className="muted">Neviens lietotājs neatbilst meklēšanai.</p>}
      {data && data.items.length > 0 && (
        <>
          <table className="table">
            <thead>
              <tr>
                <th>E-pasts</th>
                <th>Statuss</th>
                <th>Vārds</th>
                <th>Lomas</th>
                <th>MFA</th>
              </tr>
            </thead>
            <tbody>
              {data.items.map((user) => (
                <tr key={user.id}>
                  <td>
                    <Link to={`/admin/lietotaji/${user.id}`}>{user.email}</Link>
                  </td>
                  <td>
                    <span className={user.status === 'ACTIVE' ? 'badge' : 'badge status-disabled'}>
                      {USER_STATUS_LABELS[user.status]}
                    </span>
                    {user.locked && <span className="badge status-draft">Bloķēts pēc kļūdām</span>}
                  </td>
                  <td>{user.displayName}</td>
                  <td>{user.roles.filter((r) => r !== 'LEARNER').map((r) => ROLE_LABELS[r]).join(', ') || '—'}</td>
                  <td>{user.mfaEnabled ? 'Jā' : 'Nē'}</td>
                </tr>
              ))}
            </tbody>
          </table>
          {data.totalItems > data.items.length && (
            <p className="muted">
              Rādīti {data.items.length} no {data.totalItems}. Precizē meklēšanu, lai atrastu pārējos.
            </p>
          )}
        </>
      )}
    </>
  )
}
