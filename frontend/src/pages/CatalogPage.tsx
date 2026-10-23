import { Link } from 'react-router'
import { catalogApi } from '../api/courses'
import { formatDuration, formatPrice, lessonCount, LEVEL_LABELS } from '../lib/format'
import { useLoad } from '../lib/useLoad'

export function CatalogPage() {
  const { data, error, loading } = useLoad(() => catalogApi.list(), 'catalog')

  return (
    <>
      <h1>Kursi</h1>
      {loading && <p className="muted">Ielādē…</p>}
      {error && <p className="form-error">{error}</p>}
      {data && data.items.length === 0 && (
        <div className="empty-state">
          <p>Kursi vēl tiek gatavoti.</p>
          <p className="muted">Ieskaties drīzumā!</p>
        </div>
      )}
      <div className="course-grid">
        {data?.items.map((course) => (
          <Link key={course.slug} to={`/kursi/${course.slug}`} className="card course-card">
            <h2>{course.title}</h2>
            <p className="muted clamp">{course.description}</p>
            <div className="course-meta">
              {course.level && <span className="badge">{LEVEL_LABELS[course.level]}</span>}
              <span>{lessonCount(course.lessonCount)}</span>
              {course.totalDurationSeconds > 0 && <span>{formatDuration(course.totalDurationSeconds)}</span>}
            </div>
            <p className="price">{formatPrice(course.priceCents, course.currency)}</p>
          </Link>
        ))}
      </div>
    </>
  )
}
