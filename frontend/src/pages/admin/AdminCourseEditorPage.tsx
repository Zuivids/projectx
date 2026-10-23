import { useState, type FormEvent } from 'react'
import { Link, useNavigate, useParams } from 'react-router'
import { courseAdminApi, type AdminCourse, type CourseLevel } from '../../api/courses'
import { FormError } from '../../components/FormError'
import { adminErrorText } from '../../lib/adminErrors'
import { centsToInput, LEVEL_LABELS, parsePriceToCents, STATUS_LABELS } from '../../lib/format'
import { useLoad } from '../../lib/useLoad'
import { NotFoundPage } from '../NotFoundPage'
import { SectionsEditor } from './SectionsEditor'

/**
 * Course editor. Every save returns the whole course from the server, which replaces what
 * is on screen, so the editor always shows the saved state.
 */
export function AdminCourseEditorPage() {
  const { courseId = '' } = useParams()
  const id = Number(courseId)
  const navigate = useNavigate()
  const { data: course, error, status, loading, setData } = useLoad(() => courseAdminApi.get(id), courseId)
  const [actionError, setActionError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)

  if (status === 404) return <NotFoundPage />
  if (loading) return <p className="muted">Ielādē…</p>
  if (error || !course) return <p className="form-error">{error}</p>

  /** Runs a change on the server and shows the returned course. */
  async function run(action: () => Promise<AdminCourse>): Promise<boolean> {
    setActionError(null)
    setBusy(true)
    try {
      setData(await action())
      return true
    } catch (err) {
      setActionError(adminErrorText(err))
      return false
    } finally {
      setBusy(false)
    }
  }

  async function remove() {
    if (!window.confirm('Dzēst šo kursu? To nevarēs atsaukt.')) return
    setBusy(true)
    try {
      await courseAdminApi.remove(id)
      navigate('/admin/kursi')
    } catch (err) {
      setActionError(adminErrorText(err))
      setBusy(false)
    }
  }

  return (
    <div className="editor">
      <p>
        <Link to="/admin/kursi">← Visi kursi</Link>
      </p>
      <div className="page-header">
        <h1>{course.title}</h1>
        <span className={`badge status-${course.status.toLowerCase()}`}>{STATUS_LABELS[course.status]}</span>
      </div>

      <div className="actions">
        {course.status === 'DRAFT' && (
          <button type="button" className="button" disabled={busy} onClick={() => run(() => courseAdminApi.publish(id))}>
            Publicēt
          </button>
        )}
        {course.status === 'PUBLISHED' && (
          <>
            <Link to={`/kursi/${course.slug}`} className="button button-secondary">
              Skatīt katalogā
            </Link>
            <button
              type="button"
              className="button button-secondary"
              disabled={busy}
              onClick={() => run(() => courseAdminApi.archive(id))}
            >
              Arhivēt
            </button>
          </>
        )}
        {course.status === 'ARCHIVED' && (
          <button
            type="button"
            className="button button-secondary"
            disabled={busy}
            onClick={() => run(() => courseAdminApi.restore(id))}
          >
            Atjaunot kā melnrakstu
          </button>
        )}
        {course.canBeDeleted && (
          <button type="button" className="button button-danger" disabled={busy} onClick={remove}>
            Dzēst
          </button>
        )}
      </div>
      <FormError message={actionError} />

      {/* key: the form starts over from the saved values after every save */}
      <DetailsForm key={`${course.id}-${course.version}`} course={course} onSave={run} />

      <SectionsEditor course={course} busy={busy} onChange={run} />
    </div>
  )
}

function DetailsForm({
  course,
  onSave,
}: {
  course: AdminCourse
  onSave: (action: () => Promise<AdminCourse>) => Promise<boolean>
}) {
  const [title, setTitle] = useState(course.title)
  const [slug, setSlug] = useState(course.slug)
  const [price, setPrice] = useState(centsToInput(course.priceCents))
  const [level, setLevel] = useState<CourseLevel | ''>(course.level ?? '')
  const [description, setDescription] = useState(course.description)
  const [error, setError] = useState<string | null>(null)

  async function submit(event: FormEvent) {
    event.preventDefault()
    const priceCents = parsePriceToCents(price)
    if (priceCents === null) {
      setError('Cenu ievadi eiro, piemēram 49 vai 49,90')
      return
    }
    setError(null)
    await onSave(() =>
      courseAdminApi.update(course.id, {
        title,
        slug,
        description,
        priceCents,
        level: level || null,
        version: course.version,
      }),
    )
  }

  return (
    <form onSubmit={submit} className="form card">
      <h2>Kursa informācija</h2>
      <label>
        Nosaukums
        <input value={title} onChange={(e) => setTitle(e.target.value)} maxLength={200} required />
      </label>
      <label>
        Adrese
        <input
          value={slug}
          onChange={(e) => setSlug(e.target.value)}
          pattern="[a-z0-9]+(-[a-z0-9]+)*"
          maxLength={120}
          required
        />
        <span className="hint">
          /kursi/{slug || '…'} · mazie burti, cipari un domuzīmes.
          {course.status === 'PUBLISHED' && ' Mainot publicēta kursa adresi, vecās saites vairs nedarbosies.'}
        </span>
      </label>
      <div className="form-row">
        <label>
          Cena, €
          <input value={price} onChange={(e) => setPrice(e.target.value)} inputMode="decimal" required />
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
        <textarea value={description} onChange={(e) => setDescription(e.target.value)} rows={5} maxLength={20000} />
      </label>
      <FormError message={error} />
      <div>
        <button type="submit" className="button">
          Saglabāt
        </button>
      </div>
    </form>
  )
}
