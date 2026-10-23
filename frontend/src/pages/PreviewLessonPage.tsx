import { Link, useParams } from 'react-router'
import { catalogApi } from '../api/courses'
import { formatDuration } from '../lib/format'
import { useLoad } from '../lib/useLoad'
import { NotFoundPage } from './NotFoundPage'

export function PreviewLessonPage() {
  const { slug = '', lessonId = '' } = useParams()
  const { data: lesson, error, status, loading } = useLoad(
    () => catalogApi.previewLesson(slug, Number(lessonId)),
    `${slug}/${lessonId}`,
  )

  if (status === 404) return <NotFoundPage />
  if (loading) return <p className="muted">Ielādē…</p>

  return (
    <div className="lesson-page">
      <p>
        <Link to={`/kursi/${slug}`}>← Atpakaļ uz kursu</Link>
      </p>
      {status === 403 ? (
        <div className="notice">Šī nodarbība būs pieejama pēc kursa iegādes.</div>
      ) : error || !lesson ? (
        <p className="form-error">{error}</p>
      ) : (
        <>
          <h1>{lesson.title}</h1>
          {lesson.durationSeconds ? <p className="muted">{formatDuration(lesson.durationSeconds)}</p> : null}
          {lesson.type === 'AUDIO' && (
            <div className="notice">Audio atskaņošana būs pieejama drīzumā.</div>
          )}
          {lesson.contentText && <div className="lesson-text">{lesson.contentText}</div>}
        </>
      )}
    </div>
  )
}
