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

export async function request<T>(path: string, options: RequestInit = {}): Promise<T> {
  const isFormData = options.body instanceof FormData
  const response = await fetch(`${API_BASE_URL}${path}`, {
    credentials: 'include',
    ...options,
    headers: {
      ...(isFormData ? {} : { 'Content-Type': 'application/json' }),
      ...options.headers,
    },
  })

  if (!response.ok) {
    throw await toApiError(response)
  }

  if (response.status === 204) {
    return undefined as T
  }

  return response.json() as Promise<T>
}

export async function requestText(path: string, options: RequestInit = {}): Promise<string> {
  const response = await fetch(`${API_BASE_URL}${path}`, {
    credentials: 'include',
    ...options,
    headers: {
      ...options.headers,
    },
  })

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

async function toApiError(response: Response): Promise<ApiError> {
  try {
    const body = await response.json()
    const validationReport = parseAiItineraryValidationReport(body.validationReport)
    return new ApiError(response.status, body.message ?? 'Request failed.', body.code, validationReport)
  } catch {
    return new ApiError(response.status, 'Request failed.')
  }
}
