import { useState, type FormEvent } from 'react'
import { Link, useNavigate } from 'react-router'
import { courseAdminApi, type CourseLevel, type CourseStatus } from '../../api/courses'
import { FormError } from '../../components/FormError'
import { adminErrorText } from '../../lib/adminErrors'
import { formatPrice, LEVEL_LABELS, parsePriceToCents, STATUS_LABELS } from '../../lib/format'
import { useLoad } from '../../lib/useLoad'

const FILTERS: { label: string; status?: CourseStatus }[] = [
  { label: 'Visi' },
  { label: 'Melnraksti', status: 'DRAFT' },
  { label: 'Publicēti', status: 'PUBLISHED' },
  { label: 'Arhivēti', status: 'ARCHIVED' },
]

export function AdminCoursesPage() {
  const [filter, setFilter] = useState<CourseStatus | undefined>()
  const [creating, setCreating] = useState(false)
  const { data, error, loading } = useLoad(() => courseAdminApi.list(filter), filter ?? 'all')

  return (
    <>
      <p>
        <Link to="/admin">← Administrēšana</Link>
      </p>
      <div className="page-header">
        <h1>Kursi</h1>
        {!creating && (
          <button type="button" className="button" onClick={() => setCreating(true)}>
            Jauns kurss
          </button>
        )}
      </div>

      {creating && <NewCourseForm onCancel={() => setCreating(false)} />}

      <div className="tabs" role="tablist">
        {FILTERS.map((f) => (
          <button
            key={f.label}
            type="button"
            role="tab"
            aria-selected={filter === f.status}
            className={filter === f.status ? 'tab active' : 'tab'}
            onClick={() => setFilter(f.status)}
          >
            {f.label}
          </button>
        ))}
      </div>

      {loading && <p className="muted">Ielādē…</p>}
      {error && <p className="form-error">{error}</p>}
      {data && data.items.length === 0 && <p className="muted">Kursu nav.</p>}
      {data && data.items.length > 0 && (
        <table className="table">
          <thead>
            <tr>
              <th>Nosaukums</th>
              <th>Statuss</th>
              <th>Cena</th>
              <th>Nodarbības</th>
              <th>Mainīts</th>
            </tr>
          </thead>
          <tbody>
            {data.items.map((course) => (
              <tr key={course.id}>
                <td>
                  <Link to={`/admin/kursi/${course.id}`}>{course.title}</Link>
                </td>
                <td>
                  <span className={`badge status-${course.status.toLowerCase()}`}>{STATUS_LABELS[course.status]}</span>
                </td>
                <td>{formatPrice(course.priceCents, course.currency)}</td>
                <td>{course.lessonCount}</td>
                <td>{new Date(course.updatedAt).toLocaleDateString('lv-LV')}</td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
    </>
  )
}

function NewCourseForm({ onCancel }: { onCancel: () => void }) {
  const navigate = useNavigate()
  const [title, setTitle] = useState('')
  const [price, setPrice] = useState('')
  const [level, setLevel] = useState<CourseLevel | ''>('')
  const [description, setDescription] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)

  async function submit(event: FormEvent) {
    event.preventDefault()
    const priceCents = parsePriceToCents(price)
    if (priceCents === null) {
      setError('Cenu ievadi eiro, piemēram 49 vai 49,90')
      return
    }
    setError(null)
    setBusy(true)
    try {
      const course = await courseAdminApi.create({ title, description, priceCents, level: level || null })
      navigate(`/admin/kursi/${course.id}`)
    } catch (err) {
      setError(adminErrorText(err))
      setBusy(false)
    }
  }

  return (
    <form onSubmit={submit} className="form card">
      <h2>Jauns kurss</h2>
      <label>
        Nosaukums
        <input value={title} onChange={(e) => setTitle(e.target.value)} maxLength={200} required autoFocus />
        <span className="hint">Adrese tiks izveidota no nosaukuma; to var mainīt vēlāk.</span>
      </label>
      <div className="form-row">
        <label>
          Cena, €
          <input value={price} onChange={(e) => setPrice(e.target.value)} inputMode="decimal" placeholder="49,00" required />
        </label>
        <label>
          Līmenis
          <select value={level} onChange={(e) => setLevel(e.target.value as CourseLevel | '')}>
            <option value="">Nav norādīts</option>
            {Object.entries(LEVEL_LABELS).map(([value, label]) => (
              <option key={value} value={value}>
                {label}
              </option>
            ))}
          </select>
        </label>
      </div>
      <label>
        Apraksts
        <textarea value={description} onChange={(e) => setDescription(e.target.value)} rows={4} maxLength={20000} />
      </label>
      <FormError message={error} />
      <div className="actions">
        <button type="submit" className="button" disabled={busy}>
          {busy ? 'Izveido…' : 'Izveidot'}
        </button>
        <button type="button" className="button button-secondary" onClick={onCancel}>
          Atcelt
        </button>
      </div>
    </form>
  )
}
