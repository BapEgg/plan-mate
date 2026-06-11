import { API_BASE_URL, bearerHeaders, request } from './client'

export { ApiError } from './client'

export type AuthUser = {
  id: number
  loginId: string | null
  email?: string
  nickname: string
  role: string
}

export type SignupRequest = {
  loginId: string
  email: string
  password: string
  nickname: string
}

export type SignupResponse = {
  id: number
  loginId: string
  email: string
  nickname: string
  role: string
  status: string
  emailVerified: boolean
}

export type LoginRequest = {
  loginId: string
  password: string
}

export type LoginResponse = {
  accessToken: string
  tokenType: string
  expiresIn: number
  user: AuthUser
}

export type TokenRefreshResponse = {
  accessToken: string
  tokenType: string
  expiresIn: number
}

export type AuthStatusResponse = {
  authenticated: boolean
  user: {
    id: number
    loginId: string | null
    nickname: string
    role: string
  } | null
}

export type EmailVerificationResponse = {
  verified: boolean
}

export type GuidanceResponse = {
  message: string
}

export type LoginIdRecoveryRequest = {
  email: string
}

export type LoginIdRecoveryResponse = {
  loginId: string
}

export type PasswordResetRequest = {
  loginId: string
  email: string
}

export type PasswordResetConfirmRequest = {
  token: string
  newPassword: string
}

export type PasswordResetResponse = {
  reset: boolean
}

export type OAuth2Provider = 'google' | 'kakao' | 'naver'

export function signup(payload: SignupRequest) {
  return request<SignupResponse>('/api/auth/signup', {
    method: 'POST',
    body: JSON.stringify(payload),
  })
}

export function login(payload: LoginRequest) {
  return request<LoginResponse>('/api/auth/login', {
    method: 'POST',
    body: JSON.stringify(payload),
  })
}

export function confirmEmail(token: string) {
  return request<EmailVerificationResponse>('/api/auth/email-verifications/confirm', {
    method: 'POST',
    body: JSON.stringify({ token }),
  })
}

export function requestLoginIdRecovery(payload: LoginIdRecoveryRequest) {
  return request<GuidanceResponse>('/api/auth/login-id-recoveries', {
    method: 'POST',
    body: JSON.stringify(payload),
  })
}

export function confirmLoginIdRecovery(token: string) {
  return request<LoginIdRecoveryResponse>('/api/auth/login-id-recoveries/confirm', {
    method: 'POST',
    body: JSON.stringify({ token }),
  })
}

export function requestPasswordReset(payload: PasswordResetRequest) {
  return request<GuidanceResponse>('/api/auth/password-reset-requests', {
    method: 'POST',
    body: JSON.stringify(payload),
  })
}

export function confirmPasswordReset(payload: PasswordResetConfirmRequest) {
  return request<PasswordResetResponse>('/api/auth/password-resets/confirm', {
    method: 'POST',
    body: JSON.stringify(payload),
  })
}

export function refreshAccessToken() {
  return request<TokenRefreshResponse>('/api/auth/refresh', {
    method: 'POST',
  })
}

export function getAuthStatus(accessToken: string) {
  return request<AuthStatusResponse>('/api/auth/status', {
    method: 'GET',
    headers: bearerHeaders(accessToken),
  })
}

export function logout() {
  return request<void>('/api/auth/logout', {
    method: 'POST',
  })
}

export function oauth2AuthorizationUrl(provider: OAuth2Provider) {
  return `${API_BASE_URL}/oauth2/authorization/${provider}`
}
