import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import {
  ACCESS_TOKEN_STORAGE_KEY,
  ApiError,
  SESSION_EXPIRED_EVENT,
  bearerHeaders,
  request,
} from './client'

describe('API session refresh', () => {
  beforeEach(() => {
    localStorage.clear()
    localStorage.setItem(ACCESS_TOKEN_STORAGE_KEY, 'expired-access-token')
  })

  afterEach(() => {
    vi.unstubAllGlobals()
    vi.restoreAllMocks()
  })

  it('expires the session immediately when refresh fails', async () => {
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(jsonResponse(401, { code: 'UNAUTHORIZED' }))
      .mockResolvedValueOnce(jsonResponse(400, { code: 'INVALID_TOKEN' }))
    vi.stubGlobal('fetch', fetchMock)
    const onExpired = vi.fn()
    window.addEventListener(SESSION_EXPIRED_EVENT, onExpired, { once: true })

    await expect(request('/api/trips/1', {
      headers: bearerHeaders('expired-access-token'),
    })).rejects.toMatchObject<Partial<ApiError>>({
      status: 401,
      code: 'SESSION_EXPIRED',
    })

    expect(fetchMock).toHaveBeenCalledTimes(2)
    expect(localStorage.getItem(ACCESS_TOKEN_STORAGE_KEY)).toBeNull()
    expect(onExpired).toHaveBeenCalledTimes(1)
  })

  it('expires the session when the refreshed token is also rejected', async () => {
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(jsonResponse(401, { code: 'UNAUTHORIZED' }))
      .mockResolvedValueOnce(jsonResponse(200, { accessToken: 'refreshed-access-token' }))
      .mockResolvedValueOnce(jsonResponse(401, { code: 'UNAUTHORIZED' }))
    vi.stubGlobal('fetch', fetchMock)
    const onExpired = vi.fn()
    window.addEventListener(SESSION_EXPIRED_EVENT, onExpired, { once: true })

    await expect(request('/api/trips/1', {
      headers: bearerHeaders('expired-access-token'),
    })).rejects.toMatchObject<Partial<ApiError>>({
      status: 401,
      code: 'SESSION_EXPIRED',
    })

    expect(fetchMock).toHaveBeenCalledTimes(3)
    expect(localStorage.getItem(ACCESS_TOKEN_STORAGE_KEY)).toBeNull()
    expect(onExpired).toHaveBeenCalledTimes(1)
  })
})

function jsonResponse(status: number, body: unknown) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  })
}
