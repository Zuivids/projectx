import { describe, expect, it, vi } from 'vitest'
import { api, ApiError } from './client'

function jsonResponse(status: number, body?: unknown): Response {
  return new Response(body === undefined ? null : JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  })
}

describe('api client', () => {
  it('fetches a CSRF token before the first POST and sends it in the header', async () => {
    const fetchMock = vi.fn(async (url: string) => {
      if (url === '/api/v1/auth/csrf') {
        document.cookie = 'XSRF-TOKEN=token-123; path=/'
        return jsonResponse(200, {})
      }
      return jsonResponse(200, { ok: true })
    })
    vi.stubGlobal('fetch', fetchMock)

    await api('/api/v1/auth/login', { method: 'POST', body: { email: 'a@b.lv' } })

    expect(fetchMock).toHaveBeenCalledTimes(2)
    const [, init] = fetchMock.mock.calls[1] as unknown as [string, RequestInit]
    expect((init.headers as Record<string, string>)['X-XSRF-TOKEN']).toBe('token-123')
    expect(init.credentials).toBe('same-origin')
  })

  it('does not send a CSRF header on GET', async () => {
    const fetchMock = vi.fn(async () => jsonResponse(200, {}))
    vi.stubGlobal('fetch', fetchMock)

    await api('/api/v1/me')

    const [, init] = fetchMock.mock.calls[0] as unknown as [string, RequestInit]
    expect(init.headers).not.toHaveProperty('X-XSRF-TOKEN')
  })

  it('turns a problem response into ApiError with the server detail', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn(async () => jsonResponse(401, { status: 401, detail: 'Invalid email or password' })),
    )

    const error = await api('/api/v1/me').catch((e: unknown) => e)

    expect(error).toBeInstanceOf(ApiError)
    expect((error as ApiError).status).toBe(401)
    expect((error as ApiError).message).toBe('Invalid email or password')
  })

  it('retries once with a fresh token when the CSRF cookie was cleared', async () => {
    let posts = 0
    const fetchMock = vi.fn(async (url: string) => {
      if (url === '/api/v1/auth/csrf') {
        document.cookie = 'XSRF-TOKEN=fresh; path=/'
        return jsonResponse(200, {})
      }
      posts++
      if (posts === 1) {
        // Server rejected the token and cleared the cookie (as after logout)
        document.cookie = 'XSRF-TOKEN=; expires=Thu, 01 Jan 1970 00:00:00 GMT; path=/'
        return jsonResponse(403)
      }
      return jsonResponse(200, { status: 'AUTHENTICATED' })
    })
    document.cookie = 'XSRF-TOKEN=stale; path=/'
    vi.stubGlobal('fetch', fetchMock)

    const result = await api<{ status: string }>('/api/v1/auth/login', { method: 'POST', body: {} })

    expect(result.status).toBe('AUTHENTICATED')
    expect(posts).toBe(2)
  })
})
