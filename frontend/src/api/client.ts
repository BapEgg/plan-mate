import { parseAiItineraryValidationReport } from './itineraryValidation'
import type { AiItineraryValidationReport } from './itineraryValidation'

export class ApiError extends Error {
  readonly status: number
  readonly code?: string
  readonly validationReport?: AiItineraryValidationReport

  constructor(status: number, message: string, code?: string, validationReport?: AiItineraryValidationReport) {
    super(message)
    this.name = 'ApiError'
    this.status = status
    this.code = code
    this.validationReport = validationReport
  }
}

export const API_BASE_URL = import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8080'
export const ACCESS_TOKEN_STORAGE_KEY = 'planmate.accessToken'
export const ACCESS_TOKEN_REFRESHED_EVENT = 'planmate:access-token-refreshed'
export const SESSION_EXPIRED_EVENT = 'planmate:session-expired'

type RefreshResponse = {
  accessToken: string
}

let activeRefresh: Promise<string> | null = null

export async function request<T>(path: string, options: RequestInit = {}): Promise<T> {
  const response = await fetchWithSessionRefresh(path, options, true)

  if (!response.ok) {
    throw await toApiError(response)
  }

  if (response.status === 204) {
    return undefined as T
  }

  return response.json() as Promise<T>
}

export async function requestText(path: string, options: RequestInit = {}): Promise<string> {
  const response = await fetchWithSessionRefresh(path, options, false)

  if (!response.ok) {
    throw await toApiError(response)
  }

  return response.text()
}

export function bearerHeaders(accessToken: string) {
  return {
    Authorization: `Bearer ${accessToken}`,
  }
}

async function fetchWithSessionRefresh(path: string, options: RequestInit, useJsonContentType: boolean) {
  const headers = buildHeaders(options, useJsonContentType)
  const response = await fetch(`${API_BASE_URL}${path}`, {
    credentials: 'include',
    ...options,
    headers,
  })

  if (response.status !== 401 || !headers.has('Authorization') || path === '/api/auth/refresh') {
    return response
  }

  try {
    const accessToken = await refreshSessionOnce()
    const retryHeaders = new Headers(headers)
    retryHeaders.set('Authorization', `Bearer ${accessToken}`)
    return await fetch(`${API_BASE_URL}${path}`, {
      credentials: 'include',
      ...options,
      headers: retryHeaders,
    })
  } catch {
    expireSession()
    throw new ApiError(401, '로그인 세션이 만료되었습니다. 다시 로그인해 주세요.', 'SESSION_EXPIRED')
  }
}

function buildHeaders(options: RequestInit, useJsonContentType: boolean) {
  const headers = new Headers(options.headers)
  if (useJsonContentType && !(options.body instanceof FormData) && !headers.has('Content-Type')) {
    headers.set('Content-Type', 'application/json')
  }
  return headers
}

function refreshSessionOnce() {
  if (activeRefresh) {
    return activeRefresh
  }

  activeRefresh = fetch(`${API_BASE_URL}/api/auth/refresh`, {
    method: 'POST',
    credentials: 'include',
    headers: { 'Content-Type': 'application/json' },
  })
    .then(async (response) => {
      if (!response.ok) {
        throw await toApiError(response)
      }
      const body = await response.json() as RefreshResponse
      if (!body.accessToken) {
        throw new ApiError(401, '새 로그인 토큰을 받지 못했습니다.', 'INVALID_REFRESH_RESPONSE')
      }
      localStorage.setItem(ACCESS_TOKEN_STORAGE_KEY, body.accessToken)
      window.dispatchEvent(new CustomEvent(ACCESS_TOKEN_REFRESHED_EVENT, {
        detail: { accessToken: body.accessToken },
      }))
      return body.accessToken
    })
    .finally(() => {
      activeRefresh = null
    })

  return activeRefresh
}

function expireSession() {
  localStorage.removeItem(ACCESS_TOKEN_STORAGE_KEY)
  window.dispatchEvent(new Event(SESSION_EXPIRED_EVENT))
}

async function toApiError(response: Response): Promise<ApiError> {
  try {
    const body = await response.json()
    const validationReport = parseAiItineraryValidationReport(body.validationReport)
    return new ApiError(response.status, body.message ?? 'Request failed.', body.code, validationReport)
  } catch {
    return new ApiError(response.status, 'Request failed.')
  }
}
