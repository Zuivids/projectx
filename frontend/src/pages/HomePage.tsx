import { Link } from 'react-router'
import { useAuth } from '../auth/AuthContext'

export function HomePage() {
  const { user } = useAuth()
  if (!user) return null

  return (
    <>
      <h1>Labdien, {user.displayName}!</h1>

      {user.mfaSetupRequired ? (
        <div className="notice notice-warning">
          Tavam kontam ir administratora tiesības, tāpēc jāieslēdz divpakāpju pieslēgšanās.{' '}
          <Link to="/konts/drosiba">Ieslēgt tagad</Link>
        </div>
      ) : (
        !user.mfaEnabled && (
          <div className="notice">
            Aizsargā savu kontu ar divpakāpju pieslēgšanos. <Link to="/konts/drosiba">Uzzināt vairāk</Link>
          </div>
        )
      )}

      <section>
        <h2>Mani kursi</h2>
        <div className="empty-state">
          <p>Tev vēl nav neviena kursa.</p>
          <p className="muted">Kursu katalogs būs pieejams drīzumā.</p>
        </div>
      </section>
    </>
  )
}
