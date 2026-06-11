import { bearerHeaders, request } from './client'

export type MeProfile = {
  id: number
  loginId: string | null
  email: string
  nickname: string
  role: string
  status: string
  emailVerified: boolean
  linkedProviders: string[]
}

export type NicknameUpdateRequest = {
  nickname: string
}

export function getMe(accessToken: string) {
  return request<MeProfile>('/api/users/me', {
    method: 'GET',
    headers: bearerHeaders(accessToken),
  })
}

export function updateMyNickname(accessToken: string, payload: NicknameUpdateRequest) {
  return request<MeProfile>('/api/users/me', {
    method: 'PATCH',
    headers: bearerHeaders(accessToken),
    body: JSON.stringify(payload),
  })
}
