import { ApiError } from '../api/client'

/**
 * Latvian texts for the course admin API's error messages. The server's details are
 * English, so they are matched here in one place. (When the UI gets translation files,
 * backlog W-09, the server should send stable error codes instead of texts.)
 */
const MESSAGES: Record<string, string> = {
  'The course was changed by someone else. Reload it and try again.':
    'Kursu pa to laiku mainīja kāds cits. Pārlādē lapu un mēģini vēlreiz.',
  'Another course already uses this address (slug)': 'Šo adresi jau izmanto cits kurss.',
  'A course needs at least one lesson before it can be published':
    'Pirms publicēšanas kursam jāpievieno vismaz viena nodarbība.',
  'A published course must keep at least one lesson': 'Publicētam kursam jāpaliek vismaz vienai nodarbībai.',
  'Only drafts that were never published can be deleted. Archive it instead.':
    'Dzēst var tikai melnrakstu, kas nekad nav publicēts. Publicētu kursu arhivē.',
  'The new order must list every item exactly once': 'Saraksts ir novecojis. Pārlādē lapu.',
}

export function adminErrorText(err: unknown): string {
  if (!(err instanceof ApiError)) return 'Neizdevās sazināties ar serveri'
  const known = MESSAGES[err.message]
  if (known) return known
  if (err.status === 400) return 'Pārbaudi ievadītos datus.'
  if (err.status === 404) return 'Nav atrasts (iespējams, to izdzēsa kāds cits). Pārlādē lapu.'
  if (err.status === 409) return 'Šo darbību pašlaik nevar veikt.'
  return err.message
}
