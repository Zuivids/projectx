/**
 * Small fetch wrapper for the Spring Boot API.
 *
 * - Sends the session cookie (same origin).
 * - Adds the CSRF token to every state-changing request: the backend sets an XSRF-TOKEN
 *   cookie, and we copy its value into the X-XSRF-TOKEN header.
 * - Turns RFC 9457 problem responses into ApiError.
 */

const CSRF_COOKIE = 'XSRF-TOKEN'
const CSRF_HEADER = 'X-XSRF-TOKEN'

export class ApiError extends Error {
  readonly status: number

  constructor(status: number, message: string) {
    super(message)
    this.name = 'ApiError'
    this.status = status
  }
}

type Method = 'GET' | 'POST' | 'PUT' | 'PATCH' | 'DELETE'

export async function api<T>(path: string, options: { method?: Method; body?: unknown } = {}): Promise<T> {
  const method = options.method ?? 'GET'
  const response = await send(path, method, options.body)
  // The token may have been cleared (e.g. after logout): fetch a fresh one and retry once
  if (response.status === 403 && method !== 'GET' && !readCookie(CSRF_COOKIE)) {
    return parse<T>(await send(path, method, options.body))
  }
  return parse<T>(response)
}

async function send(path: string, method: Method, body: unknown): Promise<Response> {
  const headers: Record<string, string> = { Accept: 'application/json' }
  if (body !== undefined) {
    headers['Content-Type'] = 'application/json'
  }
  if (method !== 'GET') {
    headers[CSRF_HEADER] = await csrfToken()
  }
  return fetch(path, {
    method,
    headers,
    credentials: 'same-origin',
    body: body === undefined ? undefined : JSON.stringify(body),
  })
}

async function parse<T>(response: Response): Promise<T> {
  if (response.ok) {
    const text = await response.text()
    return (text ? JSON.parse(text) : undefined) as T
  }
  let message = defaultMessage(response.status)
  try {
    const problem = (await response.json()) as { detail?: string }
    if (problem.detail) {
      message = problem.detail
    }
  } catch {
    // Not JSON: keep the default message
  }
  throw new ApiError(response.status, message)
}

async function csrfToken(): Promise<string> {
  const existing = readCookie(CSRF_COOKIE)
  if (existing) {
    return existing
  }
  await fetch('/api/v1/auth/csrf', { credentials: 'same-origin' })
  return readCookie(CSRF_COOKIE) ?? ''
}

function readCookie(name: string): string | undefined {
  const match = document.cookie.split('; ').find((c) => c.startsWith(`${name}=`))
  return match ? decodeURIComponent(match.substring(name.length + 1)) : undefined
}

function defaultMessage(status: number): string {
  if (status === 401) return 'Nepieciešams pieslēgties'
  if (status === 403) return 'Nav piekļuves'
  if (status === 429) return 'Pārāk daudz mēģinājumu, mēģini vēlāk'
  if (status >= 500) return 'Servera kļūda, mēģini vēlāk'
  return 'Pieprasījums neizdevās'
}
