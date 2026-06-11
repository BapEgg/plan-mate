import { bearerHeaders, request } from './client'

export type MeProfile = {
  id: number
  loginId: string | null
  email: string
  nickname: string
  profileImageUrl: string | null
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

export function updateMyProfileImage(accessToken: string, image: File) {
  const formData = new FormData()
  formData.append('image', image)

  return request<MeProfile>('/api/users/me/profile-image', {
    method: 'POST',
    headers: bearerHeaders(accessToken),
    body: formData,
  })
}

export function clearMyProfileImage(accessToken: string) {
  return request<MeProfile>('/api/users/me/profile-image', {
    method: 'DELETE',
    headers: bearerHeaders(accessToken),
  })
}
