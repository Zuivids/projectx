import { useState, type FormEvent } from 'react'
import { Link } from 'react-router'
import { authApi } from '../api/auth'
import { ApiError } from '../api/client'
import { useAuth } from '../auth/AuthContext'
import { FormError } from '../components/FormError'

/** Server message when the 5-minute window for the MFA step has passed */
const LOGIN_EXPIRED = 'Login expired, please log in again'

export function LoginPage() {
  const { refresh, sessionExpired } = useAuth()
  const [step, setStep] = useState<'password' | 'mfa'>('password')
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [code, setCode] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)

  async function submitPassword(event: FormEvent) {
    event.preventDefault()
    setError(null)
    setBusy(true)
    try {
      const result = await authApi.login(email, password)
      setPassword('')
      if (result.status === 'MFA_REQUIRED') {
        setStep('mfa')
      } else {
        // Loading the user makes the route guard redirect to the right page
        await refresh()
      }
    } catch (err) {
      setError(err instanceof ApiError && err.status === 401 ? 'Nepareizs e-pasts vai parole' : errorText(err))
    } finally {
      setBusy(false)
    }
  }

  async function submitCode(event: FormEvent) {
    event.preventDefault()
    setError(null)
    setBusy(true)
    try {
      await authApi.completeMfa(code)
      await refresh()
    } catch (err) {
      setCode('')
      if (err instanceof ApiError && err.message === LOGIN_EXPIRED) {
        setStep('password')
        setError('Pieslēgšanās laiks beidzās. Ievadi paroli vēlreiz.')
      } else {
        setError(err instanceof ApiError && err.status === 401 ? 'Nepareizs kods' : errorText(err))
      }
    } finally {
      setBusy(false)
    }
  }

  if (step === 'mfa') {
    return (
      <div className="narrow">
        <h1>Divpakāpju pārbaude</h1>
        <p className="muted">
          Ievadi 6 ciparu kodu no autentifikācijas lietotnes. Ja tālrunis nav pieejams, ievadi vienu no saviem
          atkopšanas kodiem.
        </p>
        <form onSubmit={submitCode} className="form">
          <label>
            Kods
            <input
              value={code}
              onChange={(e) => setCode(e.target.value)}
              autoComplete="one-time-code"
              inputMode="text"
              maxLength={32}
              required
              autoFocus
            />
          </label>
          <FormError message={error} />
          <button type="submit" className="button" disabled={busy}>
            {busy ? 'Pārbauda…' : 'Apstiprināt'}
          </button>
        </form>
      </div>
    )
  }

  return (
    <div className="narrow">
      <h1>Pieslēgties</h1>
      {sessionExpired && (
        <div className="notice notice-warning">Sesija beidzās. Lūdzu, pieslēdzies vēlreiz.</div>
      )}
      <form onSubmit={submitPassword} className="form">
        <label>
          E-pasts
          <input type="email" value={email} onChange={(e) => setEmail(e.target.value)} autoComplete="email" required />
        </label>
        <label>
          Parole
          <input
            type="password"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            autoComplete="current-password"
            required
          />
        </label>
        <FormError message={error} />
        <button type="submit" className="button" disabled={busy}>
          {busy ? 'Pieslēdzas…' : 'Pieslēgties'}
        </button>
      </form>
      <p className="muted">
        Nav konta? <Link to="/registreties">Reģistrējies</Link>
      </p>
    </div>
  )
}

function errorText(err: unknown): string {
  return err instanceof ApiError ? err.message : 'Neizdevās sazināties ar serveri'
}
