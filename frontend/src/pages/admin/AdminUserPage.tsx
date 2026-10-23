import { useState } from 'react'
import { Link, useParams } from 'react-router'
import { isStaff, userAdminApi, type AdminUserDetail, type Role } from '../../api/users'
import { useAuth } from '../../auth/AuthContext'
import { FormError } from '../../components/FormError'
import { adminErrorText } from '../../lib/adminErrors'
import { AUDIT_EVENT_LABELS, ROLE_LABELS, USER_STATUS_LABELS } from '../../lib/format'
import { useLoad } from '../../lib/useLoad'
import { NotFoundPage } from '../NotFoundPage'

const DATE_TIME = new Intl.DateTimeFormat('lv-LV', { dateStyle: 'short', timeStyle: 'short' })

export function AdminUserPage() {
  const { userId = '' } = useParams()
  const id = Number(userId)
  const { user: me } = useAuth()
  const { data: user, error, status, loading, setData } = useLoad(() => userAdminApi.get(id), userId)
  const [actionError, setActionError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)

  if (status === 404) return <NotFoundPage />
  if (loading) return <p className="muted">Ielādē…</p>
  if (error || !user || !me) return <p className="form-error">{error}</p>

  const self = me.email === user.email
  const superAdmin = me.roles.includes('SUPER_ADMIN')
  // Same rule as the server: admin accounts can only be managed by a super admin
  const canManage = !self && (!isStaff(user.roles) || superAdmin)

  async function run(action: () => Promise<AdminUserDetail>, question?: string) {
    if (question && !window.confirm(question)) return
    setActionError(null)
    setBusy(true)
    try {
      setData(await action())
    } catch (err) {
      setActionError(adminErrorText(err))
    } finally {
      setBusy(false)
    }
  }

  function roleButton(role: Role) {
    const has = user!.roles.includes(role)
    return (
      <button
        type="button"
        className="button button-secondary"
        disabled={busy}
        onClick={() =>
          run(
            () => (has ? userAdminApi.revokeRole(id, role) : userAdminApi.grantRole(id, role)),
            has
              ? `Noņemt lomu “${ROLE_LABELS[role]}”? Lietotāja atvērtās sesijas beigsies.`
              : `Piešķirt lomu “${ROLE_LABELS[role]}”? Lietotājam būs jāpieslēdzas vēlreiz.`,
          )
        }
      >
        {has ? `Noņemt: ${ROLE_LABELS[role]}` : `Piešķirt: ${ROLE_LABELS[role]}`}
      </button>
    )
  }

  return (
    <div className="editor">
      <p>
        <Link to="/admin/lietotaji">← Visi lietotāji</Link>
      </p>
      <div className="page-header">
        <h1>{user.displayName}</h1>
        <span className={user.status === 'ACTIVE' ? 'badge' : 'badge status-disabled'}>
          {USER_STATUS_LABELS[user.status]}
        </span>
      </div>

      <div className="card">
        <dl className="facts">
          <dt>E-pasts</dt>
          <dd>{user.email}</dd>
          <dt>Lomas</dt>
          <dd>{user.roles.map((r) => ROLE_LABELS[r]).join(', ')}</dd>
          <dt>Reģistrācijas datums</dt>
          <dd>{DATE_TIME.format(new Date(user.createdAt))}</dd>
          <dt>Divpakāpju pieslēgšanās</dt>
          <dd>{user.mfaEnabled ? `Ieslēgta · neizmantoti atkopšanas kodi: ${user.recoveryCodesLeft}` : 'Izslēgta'}</dd>
          {user.lockedUntil && (
            <>
              <dt>Bloķēts pēc kļūdām līdz</dt>
              <dd>{DATE_TIME.format(new Date(user.lockedUntil))}</dd>
            </>
          )}
        </dl>
      </div>

      <h2>Darbības</h2>
      {self && <p className="muted">Tas ir tavs konts. Savu MFA vari mainīt sadaļā “Drošība”.</p>}
      {!self && !canManage && (
        <p className="muted">Administratoru kontus var pārvaldīt tikai galvenais administrators.</p>
      )}
      {canManage && (
        <div className="actions">
          {user.status === 'ACTIVE' ? (
            <button
              type="button"
              className="button button-danger"
              disabled={busy}
              onClick={() => run(() => userAdminApi.disable(id), 'Bloķēt kontu? Lietotājs tiks izrakstīts un nevarēs pieslēgties.')}
            >
              Bloķēt kontu
            </button>
          ) : (
            <button type="button" className="button" disabled={busy} onClick={() => run(() => userAdminApi.enable(id))}>
              Atbloķēt kontu
            </button>
          )}
          {user.lockedUntil && (
            <button
              type="button"
              className="button button-secondary"
              disabled={busy}
              onClick={() => run(() => userAdminApi.unlock(id))}
            >
              Atcelt bloķēšanu pēc kļūdām
            </button>
          )}
          {user.mfaEnabled && (
            <button
              type="button"
              className="button button-secondary"
              disabled={busy}
              onClick={() =>
                run(
                  () => userAdminApi.resetMfa(id),
                  'Atiestatīt MFA? Pārliecinies, ka to tiešām lūdz pats lietotājs (piemēram, pazaudēts tālrunis). Lietotājs pieslēgsies tikai ar paroli un MFA varēs iestatīt no jauna.',
                )
              }
            >
              Atiestatīt MFA
            </button>
          )}
        </div>
      )}
      {superAdmin && !self && (
        <>
          <h3>Lomas</h3>
          <div className="actions">
            {roleButton('ADMIN')}
            {roleButton('SUPER_ADMIN')}
          </div>
        </>
      )}
      <FormError message={actionError} />

      <h2>Aktivitāte</h2>
      {user.activity.length === 0 ? (
        <p className="muted">Nav ierakstu.</p>
      ) : (
        <table className="table">
          <thead>
            <tr>
              <th>Laiks</th>
              <th>Notikums</th>
              <th>Veica</th>
              <th>IP adrese</th>
            </tr>
          </thead>
          <tbody>
            {user.activity.map((a, index) => (
              <tr key={index}>
                <td>{DATE_TIME.format(new Date(a.occurredAt))}</td>
                <td>
                  {AUDIT_EVENT_LABELS[a.type] ?? a.type}
                  {(a.type === 'ROLE_GRANTED' || a.type === 'ROLE_REVOKED') && a.details && (
                    <span className="muted"> · {ROLE_LABELS[a.details as Role] ?? a.details}</span>
                  )}
                </td>
                <td>
                  {a.byAdmin ? (
                    'Administrators'
                  ) : a.otherUserId ? (
                    <>
                      Šis lietotājs citam kontam:{' '}
                      <Link to={`/admin/lietotaji/${a.otherUserId}`}>#{a.otherUserId}</Link>
                    </>
                  ) : (
                    'Lietotājs'
                  )}
                </td>
                <td>{a.ipAddress ?? '—'}</td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
    </div>
  )
}
