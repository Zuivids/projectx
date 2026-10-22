import { Link } from 'react-router'
import { useAuth } from '../auth/AuthContext'
import { APP_NAME } from '../config'

export function LandingPage() {
  const { user } = useAuth()

  return (
    <>
      <section className="hero">
        <h1>Mācies savā tempā ar {APP_NAME}</h1>
        <p className="lead">
          Tiešsaistes kursi latviešu valodā. Pirmais kurss ar 50% atlaidi, pēc tam maksā par katru kursu vai izvēlies
          abonementu.
        </p>
        <div className="actions">
          {user ? (
            <Link to="/sakums" className="button">
              Uz maniem kursiem
            </Link>
          ) : (
            <>
              <Link to="/registreties" className="button">
                Sākt mācīties
              </Link>
              <Link to="/pieslegties" className="button button-secondary">
                Pieslēgties
              </Link>
            </>
          )}
        </div>
      </section>

      <section className="features">
        <div className="card">
          <h2>Audio kursi</h2>
          <p>Klausies nodarbības jebkur un turpini no vietas, kur pārtrauci.</p>
        </div>
        <div className="card">
          <h2>Tavs progress</h2>
          <p>Seko savam progresam katrā kursā.</p>
        </div>
        <div className="card">
          <h2>Drošs konts</h2>
          <p>Pēc izvēles divpakāpju pieslēgšanās ar autentifikācijas lietotni.</p>
        </div>
      </section>
    </>
  )
}
