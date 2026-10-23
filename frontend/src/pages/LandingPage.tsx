import { Link } from 'react-router'
import { useAuth } from '../auth/AuthContext'
import { APP_NAME } from '../config'

/** Full-width page: each band holds its own centred .container (see Layout). */
export function LandingPage() {
  const { user } = useAuth()

  return (
    <>
      <section className="hero">
        <div className="container hero-inner">
          <h1>
            Mācies savā tempā ar <span className="highlight">{APP_NAME}</span>
          </h1>
          <p className="lead">
            Tiešsaistes kursi latviešu valodā. Pirmais kurss ar 50% atlaidi, pēc tam maksā par katru kursu vai
            izvēlies abonementu.
          </p>
          <div className="actions">
            {user ? (
              <Link to="/sakums" className="button button-accent">
                Uz maniem kursiem
              </Link>
            ) : (
              <>
                <Link to="/registreties" className="button button-accent">
                  Sākt mācīties
                </Link>
                <Link to="/pieslegties" className="button button-secondary">
                  Pieslēgties
                </Link>
              </>
            )}
            <Link to="/kursi" className="button button-secondary">
              Apskatīt kursus
            </Link>
          </div>
        </div>
      </section>

      <section className="container features landing-features">
        <div className="card">
          <span className="feature-icon" aria-hidden="true">🎧</span>
          <h2>Audio kursi</h2>
          <p>Klausies nodarbības jebkur un turpini no vietas, kur pārtrauci.</p>
        </div>
        <div className="card">
          <span className="feature-icon" aria-hidden="true">📈</span>
          <h2>Tavs progress</h2>
          <p>Seko savam progresam katrā kursā.</p>
        </div>
        <div className="card">
          <span className="feature-icon" aria-hidden="true">🔒</span>
          <h2>Drošs konts</h2>
          <p>Pēc izvēles divpakāpju pieslēgšanās ar autentifikācijas lietotni.</p>
        </div>
      </section>
    </>
  )
}
