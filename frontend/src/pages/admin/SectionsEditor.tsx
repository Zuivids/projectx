import { useState, type FormEvent } from 'react'
import {
  courseAdminApi,
  type AdminCourse,
  type AdminLesson,
  type AdminSection,
  type LessonFields,
  type LessonType,
} from '../../api/courses'
import { formatDuration, LESSON_TYPE_LABELS } from '../../lib/format'

type Run = (action: () => Promise<AdminCourse>) => Promise<boolean>

interface Props {
  course: AdminCourse
  busy: boolean
  onChange: Run
}

/** Sections and lessons: add, rename, edit, delete and move up/down. */
export function SectionsEditor({ course, busy, onChange }: Props) {
  const [newSection, setNewSection] = useState('')

  async function addSection(event: FormEvent) {
    event.preventDefault()
    if (await onChange(() => courseAdminApi.addSection(course.id, newSection))) {
      setNewSection('')
    }
  }

  function moveSection(index: number, delta: number) {
    const ids = moved(course.sections.map((s) => s.id), index, delta)
    return onChange(() => courseAdminApi.reorderSections(course.id, ids))
  }

  return (
    <section className="sections-editor">
      <h2>Saturs</h2>
      {course.sections.length === 0 && <p className="muted">Sāc ar pirmo sadaļu, piemēram “Ievads”.</p>}
      {course.sections.map((section, index) => (
        <SectionCard
          key={section.id}
          course={course}
          section={section}
          busy={busy}
          onChange={onChange}
          onMoveUp={index > 0 ? () => moveSection(index, -1) : undefined}
          onMoveDown={index < course.sections.length - 1 ? () => moveSection(index, 1) : undefined}
        />
      ))}
      <form onSubmit={addSection} className="inline-form">
        <input
          value={newSection}
          onChange={(e) => setNewSection(e.target.value)}
          placeholder="Jaunas sadaļas nosaukums"
          aria-label="Jaunas sadaļas nosaukums"
          maxLength={200}
          required
        />
        <button type="submit" className="button button-secondary" disabled={busy}>
          Pievienot sadaļu
        </button>
      </form>
    </section>
  )
}

function SectionCard({
  course,
  section,
  busy,
  onChange,
  onMoveUp,
  onMoveDown,
}: {
  course: AdminCourse
  section: AdminSection
  busy: boolean
  onChange: Run
  onMoveUp?: () => void
  onMoveDown?: () => void
}) {
  const [title, setTitle] = useState(section.title)
  const [adding, setAdding] = useState(false)
  const [editing, setEditing] = useState<number | null>(null)

  function moveLesson(index: number, delta: number) {
    const ids = moved(section.lessons.map((l) => l.id), index, delta)
    return onChange(() => courseAdminApi.reorderLessons(course.id, section.id, ids))
  }

  function deleteSection() {
    const lessons = section.lessons.length
    const question = lessons
      ? `Dzēst sadaļu “${section.title}”? Tiks dzēstas arī visas tās nodarbības (${lessons}).`
      : `Dzēst sadaļu “${section.title}”?`
    if (window.confirm(question)) {
      void onChange(() => courseAdminApi.deleteSection(course.id, section.id))
    }
  }

  return (
    <div className="card section-card">
      <div className="section-header">
        <input
          className="section-title"
          value={title}
          onChange={(e) => setTitle(e.target.value)}
          aria-label="Sadaļas nosaukums"
          maxLength={200}
        />
        {title !== section.title && title.trim() && (
          <button
            type="button"
            className="button button-small"
            disabled={busy}
            onClick={() => onChange(() => courseAdminApi.renameSection(course.id, section.id, title))}
          >
            Saglabāt
          </button>
        )}
        <MoveButtons label="sadaļu" busy={busy} onUp={onMoveUp} onDown={onMoveDown} />
        <button type="button" className="link-button danger" disabled={busy} onClick={deleteSection}>
          Dzēst
        </button>
      </div>

      <ol className="lesson-list">
        {section.lessons.map((lesson, index) =>
          editing === lesson.id ? (
            <li key={lesson.id}>
              <LessonForm
                initial={lesson}
                busy={busy}
                onCancel={() => setEditing(null)}
                onSubmit={async (fields) => {
                  if (await onChange(() => courseAdminApi.updateLesson(course.id, lesson.id, fields))) {
                    setEditing(null)
                  }
                }}
              />
            </li>
          ) : (
            <li key={lesson.id} className="lesson-row">
              <span className="lesson-title">
                {lesson.title}
                {lesson.preview && <span className="badge badge-success">Bezmaksas</span>}
              </span>
              <span className="muted">
                {LESSON_TYPE_LABELS[lesson.type]}
                {lesson.durationSeconds ? ` · ${formatDuration(lesson.durationSeconds)}` : ''}
              </span>
              <span className="row-actions">
                <MoveButtons
                  label="nodarbību"
                  busy={busy}
                  onUp={index > 0 ? () => moveLesson(index, -1) : undefined}
                  onDown={index < section.lessons.length - 1 ? () => moveLesson(index, 1) : undefined}
                />
                <button type="button" className="link-button" onClick={() => setEditing(lesson.id)}>
                  Labot
                </button>
                <button
                  type="button"
                  className="link-button danger"
                  disabled={busy}
                  onClick={() => {
                    if (window.confirm(`Dzēst nodarbību “${lesson.title}”?`)) {
                      void onChange(() => courseAdminApi.deleteLesson(course.id, lesson.id))
                    }
                  }}
                >
                  Dzēst
                </button>
              </span>
            </li>
          ),
        )}
      </ol>

      {adding ? (
        <LessonForm
          busy={busy}
          onCancel={() => setAdding(false)}
          onSubmit={async (fields) => {
            if (await onChange(() => courseAdminApi.addLesson(course.id, section.id, fields))) {
              setAdding(false)
            }
          }}
        />
      ) : (
        <button type="button" className="link-button" onClick={() => setAdding(true)}>
          + Pievienot nodarbību
        </button>
      )}
    </div>
  )
}

function LessonForm({
  initial,
  busy,
  onSubmit,
  onCancel,
}: {
  initial?: AdminLesson
  busy: boolean
  onSubmit: (fields: LessonFields) => void
  onCancel: () => void
}) {
  const [title, setTitle] = useState(initial?.title ?? '')
  const [type, setType] = useState<LessonType>(initial?.type ?? 'AUDIO')
  const [minutes, setMinutes] = useState(initial?.durationSeconds ? String(Math.round(initial.durationSeconds / 60)) : '')
  const [preview, setPreview] = useState(initial?.preview ?? false)
  const [contentText, setContentText] = useState(initial?.contentText ?? '')
  const [audioRef, setAudioRef] = useState(initial?.audioRef ?? '')

  function submit(event: FormEvent) {
    event.preventDefault()
    onSubmit({
      title,
      type,
      contentText: contentText || null,
      audioRef: audioRef || null,
      durationSeconds: minutes ? Number(minutes) * 60 : null,
      preview,
    })
  }

  return (
    <form onSubmit={submit} className="form lesson-form">
      <label>
        Nodarbības nosaukums
        <input value={title} onChange={(e) => setTitle(e.target.value)} maxLength={200} required autoFocus />
      </label>
      <div className="form-row">
        <label>
          Veids
          <select value={type} onChange={(e) => setType(e.target.value as LessonType)}>
            <option value="AUDIO">Audio</option>
            <option value="TEXT">Teksts</option>
          </select>
        </label>
        <label>
          Ilgums, min
          <input
            type="number"
            min={0}
            max={1440}
            value={minutes}
            onChange={(e) => setMinutes(e.target.value)}
          />
        </label>
      </div>
      <label>
        {type === 'TEXT' ? 'Nodarbības teksts' : 'Piezīmes vai transkripts'}
        <textarea value={contentText} onChange={(e) => setContentText(e.target.value)} rows={type === 'TEXT' ? 8 : 3} />
      </label>
      {type === 'AUDIO' && (
        <label>
          Audio faila atsauce
          <input value={audioRef} onChange={(e) => setAudioRef(e.target.value)} maxLength={500} />
          <span className="hint">Audio failu glabāšana vēl nav izvēlēta (D-06); lauku var atstāt tukšu.</span>
        </label>
      )}
      <label className="checkbox">
        <input type="checkbox" checked={preview} onChange={(e) => setPreview(e.target.checked)} />
        Bezmaksas priekšskatījums (redzams bez pirkuma)
      </label>
      <div className="actions">
        <button type="submit" className="button" disabled={busy}>
          Saglabāt
        </button>
        <button type="button" className="button button-secondary" onClick={onCancel}>
          Atcelt
        </button>
      </div>
    </form>
  )
}

function MoveButtons({ label, busy, onUp, onDown }: { label: string; busy: boolean; onUp?: () => void; onDown?: () => void }) {
  return (
    <span className="move-buttons">
      <button type="button" className="icon-button" disabled={busy || !onUp} onClick={onUp} aria-label={`Pārvietot ${label} uz augšu`}>
        ↑
      </button>
      <button type="button" className="icon-button" disabled={busy || !onDown} onClick={onDown} aria-label={`Pārvietot ${label} uz leju`}>
        ↓
      </button>
    </span>
  )
}

/** ids with the item at `index` moved by `delta` places */
function moved(ids: number[], index: number, delta: number): number[] {
  const result = [...ids]
  const [item] = result.splice(index, 1)
  result.splice(index + delta, 0, item)
  return result
}
