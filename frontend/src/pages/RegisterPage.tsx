import { useState, type FormEvent } from 'react'
import { Link } from 'react-router'
import { authApi } from '../api/auth'
import { ApiError } from '../api/client'
import { FormError } from '../components/FormError'

/** Same limits as RegisterRequest on the server (NIST 800-63B: length, no composition rules) */
const MIN_PASSWORD = 12
const MAX_PASSWORD = 128

export function RegisterPage() {
  const [displayName, setDisplayName] = useState('')
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [confirm, setConfirm] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)
  const [done, setDone] = useState(false)

  async function submit(event: FormEvent) {
    event.preventDefault()
    setError(null)
    if (password.length < MIN_PASSWORD) {
      setError(`Parolei jābūt vismaz ${MIN_PASSWORD} simbolus garai`)
      return
    }
    if (password !== confirm) {
      setError('Paroles nesakrīt')
      return
    }
    setBusy(true)
    try {
      await authApi.register(email, password, displayName)
      setDone(true)
    } catch (err) {
      setError(
        err instanceof ApiError && err.status === 400
          ? 'Pārbaudi ievadītos datus'
          : err instanceof ApiError
            ? err.message
            : 'Neizdevās sazināties ar serveri',
      )
    } finally {
      setBusy(false)
    }
  }

  if (done) {
    // The server answers the same way whether or not the email was already registered,
    // so this message must not claim that a new account was created for sure.
    return (
      <div className="narrow">
        <h1>Paldies!</h1>
        <p>Reģistrācija pieņemta. Tagad vari pieslēgties ar savu e-pastu un paroli.</p>
        <Link to="/pieslegties" className="button">
          Pieslēgties
        </Link>
      </div>
    )
  }

  return (
    <div className="narrow">
      <h1>Izveidot kontu</h1>
      <form onSubmit={submit} className="form">
        <label>
          Vārds
          <input
            value={displayName}
            onChange={(e) => setDisplayName(e.target.value)}
            autoComplete="name"
            maxLength={100}
            required
          />
        </label>
        <label>
          E-pasts
          <input
            type="email"
            value={email}
            onChange={(e) => setEmail(e.target.value)}
            autoComplete="email"
            maxLength={320}
            required
          />
        </label>
        <label>
          Parole
          <input
            type="password"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            autoComplete="new-password"
            minLength={MIN_PASSWORD}
            maxLength={MAX_PASSWORD}
            required
          />
          <span className="hint">Vismaz {MIN_PASSWORD} simboli. Garāka frāze ir drošāka par sarežģītu vārdu.</span>
        </label>
        <label>
          Parole vēlreiz
          <input
            type="password"
            value={confirm}
            onChange={(e) => setConfirm(e.target.value)}
            autoComplete="new-password"
            maxLength={MAX_PASSWORD}
            required
          />
        </label>
        <FormError message={error} />
        <button type="submit" className="button" disabled={busy}>
          {busy ? 'Reģistrē…' : 'Reģistrēties'}
        </button>
      </form>
      <p className="muted">
        Jau ir konts? <Link to="/pieslegties">Pieslēdzies</Link>
      </p>
    </div>
  )
}
