import { QRCodeSVG } from 'qrcode.react'
import { useState, type FormEvent } from 'react'
import { useLocation } from 'react-router'
import { authApi, type TotpSetup } from '../api/auth'
import { ApiError } from '../api/client'
import { useAuth } from '../auth/AuthContext'
import { FormError } from '../components/FormError'
import { APP_NAME } from '../config'

type Step = { name: 'overview' } | { name: 'scan'; setup: TotpSetup } | { name: 'codes'; codes: string[] }

export function SecurityPage() {
  const { user, refresh } = useAuth()
  const location = useLocation()
  const adminNeedsMfa = (location.state as { adminNeedsMfa?: boolean } | null)?.adminNeedsMfa
  const [step, setStep] = useState<Step>({ name: 'overview' })
  const [code, setCode] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)

  if (!user) return null

  async function start() {
    setError(null)
    setBusy(true)
    try {
      setStep({ name: 'scan', setup: await authApi.startTotpSetup() })
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Neizdevās sazināties ar serveri')
    } finally {
      setBusy(false)
    }
  }

  async function confirm(event: FormEvent) {
    event.preventDefault()
    setError(null)
    setBusy(true)
    try {
      const result = await authApi.confirmTotpSetup(code)
      setCode('')
      setStep({ name: 'codes', codes: result.recoveryCodes })
    } catch (err) {
      setError(err instanceof ApiError && err.status === 400 ? 'Nepareizs kods, mēģini vēlreiz' : 'Neizdevās apstiprināt')
    } finally {
      setBusy(false)
    }
  }

  if (step.name === 'scan') {
    return (
      <div className="narrow">
        <h1>Ieslēgt divpakāpju pieslēgšanos</h1>
        <ol className="steps">
          <li>
            Atver autentifikācijas lietotni (piemēram, Google Authenticator vai Microsoft Authenticator) un noskenē šo
            QR kodu.
          </li>
        </ol>
        <div className="qr">
          <QRCodeSVG value={step.setup.otpauthUri} size={200} marginSize={2} title={`${APP_NAME} QR kods`} />
        </div>
        <p className="muted">
          Nevari noskenēt? Ievadi šo atslēgu manuāli: <code className="secret">{groupSecret(step.setup.secret)}</code>
        </p>
        <ol className="steps" start={2}>
          <li>Ievadi 6 ciparu kodu, ko rāda lietotne.</li>
        </ol>
        <form onSubmit={confirm} className="form">
          <label>
            Kods
            <input
              value={code}
              onChange={(e) => setCode(e.target.value.replace(/\D/g, ''))}
              inputMode="numeric"
              autoComplete="one-time-code"
              maxLength={6}
              pattern="\d{6}"
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

  if (step.name === 'codes') {
    return <RecoveryCodes codes={step.codes} onDone={() => refresh().then(() => setStep({ name: 'overview' }))} />
  }

  return (
    <div className="narrow">
      <h1>Drošība</h1>
      {adminNeedsMfa && !user.mfaVerified && (
        <div className="notice notice-warning">
          Lai atvērtu administrēšanu, nepieciešama divpakāpju pieslēgšanās šajā sesijā.
          {user.mfaEnabled && ' Izej no konta un pieslēdzies vēlreiz ar kodu.'}
        </div>
      )}
      <div className="card">
        <h2>Divpakāpju pieslēgšanās</h2>
        {user.mfaEnabled ? (
          <p>
            <span className="badge badge-success">Ieslēgta</span> Pieslēdzoties, papildus parolei tiks prasīts kods no
            autentifikācijas lietotnes.
          </p>
        ) : (
          <>
            <p>
              <span className="badge">Izslēgta</span> Papildus parolei pieprasīt kodu no autentifikācijas lietotnes. Tas
              aizsargā kontu, pat ja kāds uzzina tavu paroli.
            </p>
            <FormError message={error} />
            <button type="button" className="button" onClick={start} disabled={busy}>
              Ieslēgt
            </button>
          </>
        )}
      </div>
    </div>
  )
}

function RecoveryCodes({ codes, onDone }: { codes: string[]; onDone: () => void }) {
  const [saved, setSaved] = useState(false)
  const [copied, setCopied] = useState(false)
  const text = codes.join('\n')

  async function copy() {
    await navigator.clipboard.writeText(text)
    setCopied(true)
  }

  function download() {
    const url = URL.createObjectURL(new Blob([`${APP_NAME} atkopšanas kodi\n\n${text}\n`], { type: 'text/plain' }))
    const link = document.createElement('a')
    link.href = url
    link.download = `${APP_NAME.toLowerCase()}-atkopsanas-kodi.txt`
    link.click()
    URL.revokeObjectURL(url)
  }

  return (
    <div className="narrow">
      <h1>Saglabā atkopšanas kodus</h1>
      <div className="notice notice-warning">
        Ja pazaudēsi tālruni, ar šiem kodiem varēsi pieslēgties. Katrs kods derīgs vienu reizi.{' '}
        <strong>Tos vairs nevarēs apskatīt.</strong>
      </div>
      <ul className="recovery-codes">
        {codes.map((c) => (
          <li key={c}>
            <code>{c}</code>
          </li>
        ))}
      </ul>
      <div className="actions">
        <button type="button" className="button button-secondary" onClick={copy}>
          {copied ? 'Nokopēts' : 'Kopēt'}
        </button>
        <button type="button" className="button button-secondary" onClick={download}>
          Lejupielādēt .txt
        </button>
      </div>
      <label className="checkbox">
        <input type="checkbox" checked={saved} onChange={(e) => setSaved(e.target.checked)} />
        Kodi ir saglabāti drošā vietā
      </label>
      <button type="button" className="button" disabled={!saved} onClick={onDone}>
        Gatavs
      </button>
    </div>
  )
}

/** "ABCDEFGH..." → "ABCD EFGH ..." for easier manual typing */
function groupSecret(secret: string): string {
  return secret.replace(/(.{4})/g, '$1 ').trim()
}
