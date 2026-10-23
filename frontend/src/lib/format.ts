import type { CourseLevel, CourseStatus, LessonType } from '../api/courses'
import type { Role, UserStatus } from '../api/users'

/** 4900 → "49,00 €" */
export function formatPrice(cents: number, currency = 'EUR'): string {
  return new Intl.NumberFormat('lv-LV', { style: 'currency', currency }).format(cents / 100)
}

/** 5400 → "1 h 30 min", 300 → "5 min", 0 → "" */
export function formatDuration(seconds: number | null | undefined): string {
  if (!seconds) return ''
  const minutes = Math.round(seconds / 60)
  if (minutes < 60) return `${Math.max(minutes, 1)} min`
  const hours = Math.floor(minutes / 60)
  const rest = minutes % 60
  return rest ? `${hours} h ${rest} min` : `${hours} h`
}

/**
 * Euro amount as typed by an admin ("49", "49,9", "49.90") → cents (4990).
 * Works on the text, never with floating point, so "0.29" is exactly 29 cents.
 * @returns null when the text is not a valid amount
 */
export function parsePriceToCents(text: string): number | null {
  const match = /^\s*(\d{1,7})(?:[.,](\d{1,2}))?\s*$/.exec(text)
  if (!match) return null
  const euros = Number(match[1])
  const cents = match[2] ? Number(match[2].padEnd(2, '0')) : 0
  return euros * 100 + cents
}

/** 4990 → "49,90" for an input field */
export function centsToInput(cents: number): string {
  return `${Math.floor(cents / 100)},${String(cents % 100).padStart(2, '0')}`
}

const latvianPlural = new Intl.PluralRules('lv')

/** 1 → "1 nodarbība", 2 → "2 nodarbības", 10 → "10 nodarbību" (Latvian plural rules) */
export function lessonCount(n: number): string {
  const word = { zero: 'nodarbību', one: 'nodarbība' }[latvianPlural.select(n) as string] ?? 'nodarbības'
  return `${n} ${word}`
}

export const LEVEL_LABELS: Record<CourseLevel, string> = {
  BEGINNER: 'Iesācējiem',
  INTERMEDIATE: 'Vidējs līmenis',
  ADVANCED: 'Padziļināts',
}

export const STATUS_LABELS: Record<CourseStatus, string> = {
  DRAFT: 'Melnraksts',
  PUBLISHED: 'Publicēts',
  ARCHIVED: 'Arhivēts',
}

export const ROLE_LABELS: Record<Role, string> = {
  LEARNER: 'Apmācāmais',
  ADMIN: 'Administrators',
  SUPER_ADMIN: 'Galvenais administrators',
}

export const USER_STATUS_LABELS: Record<UserStatus, string> = {
  ACTIVE: 'Aktīvs',
  DISABLED: 'Bloķēts',
}

/** Audit log event names (lv.lms.audit.AuditEventType); unknown ones are shown as they are. */
export const AUDIT_EVENT_LABELS: Record<string, string> = {
  USER_REGISTERED: 'Reģistrējās',
  LOGIN_SUCCEEDED: 'Pieslēdzās',
  LOGIN_FAILED: 'Neveiksmīga pieslēgšanās',
  ACCOUNT_LOCKED: 'Konts bloķēts pēc kļūdām',
  BLOCKED_FOR_IP: 'IP adrese bloķēta pēc kļūdām',
  MFA_CHALLENGE_PASSED: 'MFA kods pareizs',
  MFA_CHALLENGE_FAILED: 'Nepareizs MFA kods',
  MFA_ENABLED: 'Ieslēdza MFA',
  RECOVERY_CODE_USED: 'Izmantoja atkopšanas kodu',
  LOGGED_OUT: 'Izgāja',
  SESSION_ENDED: 'Sesija beigta',
  ADMIN_ROLE_GRANTED: 'Lomu piešķīra palaišanas iestatījums',
  USER_DISABLED: 'Konts bloķēts',
  USER_ENABLED: 'Konts atbloķēts',
  USER_UNLOCKED: 'Bloķēšana pēc kļūdām atcelta',
  MFA_RESET_BY_ADMIN: 'MFA atiestatīts',
  ROLE_GRANTED: 'Piešķirta loma',
  ROLE_REVOKED: 'Noņemta loma',
}

export const LESSON_TYPE_LABELS: Record<LessonType, string> = {
  AUDIO: 'Audio',
  TEXT: 'Teksts',
}
