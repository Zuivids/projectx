import { Link, useParams } from 'react-router'
import { catalogApi } from '../api/courses'
import { formatDuration, formatPrice, lessonCount, LESSON_TYPE_LABELS, LEVEL_LABELS } from '../lib/format'
import { useLoad } from '../lib/useLoad'
import { NotFoundPage } from './NotFoundPage'

export function CoursePage() {
  const { slug = '' } = useParams()
  const { data: course, error, status, loading } = useLoad(() => catalogApi.get(slug), slug)

  if (status === 404) return <NotFoundPage />
  if (loading) return <p className="muted">Ielādē…</p>
  if (error || !course) return <p className="form-error">{error}</p>

  return (
    <div className="course-page">
      <p>
        <Link to="/kursi">← Visi kursi</Link>
      </p>
      <h1>{course.title}</h1>
      <div className="course-meta">
        {course.level && <span className="badge">{LEVEL_LABELS[course.level]}</span>}
        <span>{lessonCount(course.lessonCount)}</span>
        {course.totalDurationSeconds > 0 && <span>{formatDuration(course.totalDurationSeconds)}</span>}
      </div>
      {/* Plain text: React escapes it, and line breaks are kept by CSS */}
      <p className="description">{course.description}</p>

      <div className="card buy-box">
        <p className="price">{formatPrice(course.priceCents, course.currency)}</p>
        <button type="button" className="button" disabled>
          Pirkt
        </button>
        <p className="muted">Pirkšana būs pieejama drīzumā.</p>
      </div>

      <h2>Kursa saturs</h2>
      {course.sections.map((section, index) => (
        <section key={index} className="outline-section">
          <h3>{section.title}</h3>
          <ol className="outline">
            {section.lessons.map((lesson) => (
              <li key={lesson.id}>
                <span>
                  {lesson.preview ? (
                    <Link to={`/kursi/${course.slug}/nodarbibas/${lesson.id}`}>{lesson.title}</Link>
                  ) : (
                    lesson.title
                  )}
                  {lesson.preview && <span className="badge badge-success">Bezmaksas</span>}
                </span>
                <span className="muted">
                  {LESSON_TYPE_LABELS[lesson.type]}
                  {lesson.durationSeconds ? ` · ${formatDuration(lesson.durationSeconds)}` : ''}
                </span>
              </li>
            ))}
          </ol>
        </section>
      ))}
    </div>
  )
}
