import { api } from './client'

export type CourseStatus = 'DRAFT' | 'PUBLISHED' | 'ARCHIVED'
export type CourseLevel = 'BEGINNER' | 'INTERMEDIATE' | 'ADVANCED'
export type LessonType = 'AUDIO' | 'TEXT'

export interface Page<T> {
  items: T[]
  page: number
  size: number
  totalItems: number
  totalPages: number
}

// ---- Public catalog (mirrors lv.lms.course.catalog.CatalogResponses) ----

export interface CatalogCourseSummary {
  slug: string
  title: string
  description: string
  priceCents: number
  currency: string
  level: CourseLevel | null
  lessonCount: number
  totalDurationSeconds: number
}

export interface CatalogLessonOutline {
  id: number
  title: string
  type: LessonType
  durationSeconds: number | null
  preview: boolean
}

export interface CatalogCourseDetail extends CatalogCourseSummary {
  sections: { title: string; lessons: CatalogLessonOutline[] }[]
}

export interface PreviewLesson {
  id: number
  title: string
  type: LessonType
  contentText: string | null
  audioRef: string | null
  durationSeconds: number | null
}

export const catalogApi = {
  list: (page = 0) => api<Page<CatalogCourseSummary>>(`/api/v1/courses?page=${page}&size=20`),
  get: (slug: string) => api<CatalogCourseDetail>(`/api/v1/courses/${encodeURIComponent(slug)}`),
  previewLesson: (slug: string, lessonId: number) =>
    api<PreviewLesson>(`/api/v1/courses/${encodeURIComponent(slug)}/lessons/${lessonId}`),
}

// ---- Admin (mirrors lv.lms.course.admin.dto) ----

export interface AdminCourseSummary {
  id: number
  title: string
  slug: string
  status: CourseStatus
  priceCents: number
  currency: string
  level: CourseLevel | null
  sectionCount: number
  lessonCount: number
  updatedAt: string
}

export interface AdminLesson {
  id: number
  title: string
  type: LessonType
  contentText: string | null
  audioRef: string | null
  durationSeconds: number | null
  preview: boolean
  position: number
}

export interface AdminSection {
  id: number
  title: string
  position: number
  lessons: AdminLesson[]
}

export interface AdminCourse {
  id: number
  title: string
  slug: string
  description: string
  priceCents: number
  currency: string
  level: CourseLevel | null
  status: CourseStatus
  version: number
  publishedAt: string | null
  updatedAt: string
  canBeDeleted: boolean
  sections: AdminSection[]
}

export interface CourseFields {
  title: string
  slug?: string
  description: string
  priceCents: number
  level: CourseLevel | null
}

export interface LessonFields {
  title: string
  type: LessonType
  contentText: string | null
  audioRef: string | null
  durationSeconds: number | null
  preview: boolean
}

const ADMIN = '/api/v1/admin/courses'

export const courseAdminApi = {
  list: (status?: CourseStatus, page = 0) =>
    api<Page<AdminCourseSummary>>(`${ADMIN}?page=${page}&size=50${status ? `&status=${status}` : ''}`),
  get: (id: number) => api<AdminCourse>(`${ADMIN}/${id}`),
  create: (fields: CourseFields) => api<AdminCourse>(ADMIN, { method: 'POST', body: fields }),
  update: (id: number, fields: CourseFields & { slug: string; version: number }) =>
    api<AdminCourse>(`${ADMIN}/${id}`, { method: 'PUT', body: fields }),
  remove: (id: number) => api<void>(`${ADMIN}/${id}`, { method: 'DELETE' }),
  publish: (id: number) => api<AdminCourse>(`${ADMIN}/${id}/publish`, { method: 'POST' }),
  archive: (id: number) => api<AdminCourse>(`${ADMIN}/${id}/archive`, { method: 'POST' }),
  restore: (id: number) => api<AdminCourse>(`${ADMIN}/${id}/restore`, { method: 'POST' }),

  addSection: (id: number, title: string) =>
    api<AdminCourse>(`${ADMIN}/${id}/sections`, { method: 'POST', body: { title } }),
  renameSection: (id: number, sectionId: number, title: string) =>
    api<AdminCourse>(`${ADMIN}/${id}/sections/${sectionId}`, { method: 'PUT', body: { title } }),
  deleteSection: (id: number, sectionId: number) =>
    api<AdminCourse>(`${ADMIN}/${id}/sections/${sectionId}`, { method: 'DELETE' }),
  reorderSections: (id: number, ids: number[]) =>
    api<AdminCourse>(`${ADMIN}/${id}/sections/order`, { method: 'PUT', body: { ids } }),

  addLesson: (id: number, sectionId: number, fields: LessonFields) =>
    api<AdminCourse>(`${ADMIN}/${id}/sections/${sectionId}/lessons`, { method: 'POST', body: fields }),
  updateLesson: (id: number, lessonId: number, fields: LessonFields) =>
    api<AdminCourse>(`${ADMIN}/${id}/lessons/${lessonId}`, { method: 'PUT', body: fields }),
  deleteLesson: (id: number, lessonId: number) =>
    api<AdminCourse>(`${ADMIN}/${id}/lessons/${lessonId}`, { method: 'DELETE' }),
  reorderLessons: (id: number, sectionId: number, ids: number[]) =>
    api<AdminCourse>(`${ADMIN}/${id}/sections/${sectionId}/lessons/order`, { method: 'PUT', body: { ids } }),
}
