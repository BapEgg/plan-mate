import { useCallback, useEffect, useState } from 'react'
import type { FormEvent } from 'react'
import { useRef } from 'react'
import './App.css'
import { MainPage } from './pages/main/MainPage'
import { TripCreatePage } from './pages/trip/TripCreatePage'
import { TripDetailPage } from './pages/trip/TripDetailPage'
import {
  ApiError,
  confirmEmail,
  confirmLoginIdRecovery,
  confirmPasswordReset,
  getAuthStatus,
  login,
  logout,
  oauth2AuthorizationUrl,
  refreshAccessToken,
  requestLoginIdRecovery,
  requestPasswordReset,
  signup,
} from './api/auth'
import type { AuthUser, OAuth2Provider } from './api/auth'
import {
  ACCESS_TOKEN_REFRESHED_EVENT,
  ACCESS_TOKEN_STORAGE_KEY,
  SESSION_EXPIRED_EVENT,
} from './api/client'

type Page = 'auth' | 'emailVerification' | 'findLoginId' | 'resetPassword' | 'main' | 'tripCreate' | 'tripDetail'
type AuthMode = 'login' | 'signup'
type NoticeTone = 'info' | 'success' | 'error'

type Notice = {
  tone: NoticeTone
  message: string
}

type AsyncStatus = 'idle' | 'loading' | 'success' | 'error'

const TRAVEL_THOUGHTS = [
  '이번엔 어디 갈까?',
  '2박 3일이면 좋겠어',
  '너무 빡빡하진 않게',
  '맛있는 건 꼭 먹자',
  '차 없이 다닐 수 있으면 좋겠어',
  '하루쯤은 여유롭게',
]

const STORY_MOMENT_DURATIONS = [700, 700, 700, 700, 700, 700, 1700]
const FINAL_STORY_MOMENT = 7

function resolvePage(): Page {
  if (window.location.pathname === '/main') {
    return 'main'
  }
  if (window.location.pathname === '/trips/new') {
    return 'tripCreate'
  }
  if (window.location.pathname.startsWith('/trips/')) {
    return 'tripDetail'
  }
  if (window.location.pathname === '/auth/email-verification') {
    return 'emailVerification'
  }
  if (window.location.pathname === '/auth/find-login-id') {
    return 'findLoginId'
  }
  if (window.location.pathname === '/auth/reset-password') {
    return 'resetPassword'
  }
  return 'auth'
}

function App() {
  const [page, setPage] = useState<Page>(() => resolvePage())
  const [authMode, setAuthMode] = useState<AuthMode>('login')
  const [notice, setNotice] = useState<Notice | null>(null)
  const [accessToken, setAccessToken] = useState(() => localStorage.getItem(ACCESS_TOKEN_STORAGE_KEY) ?? '')
  const [currentUser, setCurrentUser] = useState<AuthUser | null>(null)
  const [emailVerificationStatus, setEmailVerificationStatus] = useState<AsyncStatus>('idle')
  const [loginIdRecoveryStatus, setLoginIdRecoveryStatus] = useState<AsyncStatus>('idle')
  const [recoveredLoginId, setRecoveredLoginId] = useState('')
  const [passwordResetStatus, setPasswordResetStatus] = useState<AsyncStatus>('idle')

  const navigate = useCallback((path: string) => {
    window.history.pushState(null, '', path)
    setPage(resolvePage())
    setNotice(null)
  }, [])

  const persistAccessToken = useCallback((token: string) => {
    localStorage.setItem(ACCESS_TOKEN_STORAGE_KEY, token)
    setAccessToken(token)
  }, [])

  const clearAccessToken = useCallback(() => {
    localStorage.removeItem(ACCESS_TOKEN_STORAGE_KEY)
    setAccessToken('')
    setCurrentUser(null)
  }, [])

  const redirectToLogin = useCallback((message = '로그인 세션이 만료되었습니다. 다시 로그인해 주세요.') => {
    clearAccessToken()
    window.history.replaceState(null, '', '/')
    setPage('auth')
    setAuthMode('login')
    setNotice({ tone: 'error', message })
  }, [clearAccessToken])

  useEffect(() => {
    const handleAccessTokenRefreshed = (event: Event) => {
      const refreshed = event as CustomEvent<{ accessToken?: string }>
      if (refreshed.detail?.accessToken) {
        setAccessToken(refreshed.detail.accessToken)
      }
    }
    const handleSessionExpired = () => redirectToLogin()

    window.addEventListener(ACCESS_TOKEN_REFRESHED_EVENT, handleAccessTokenRefreshed)
    window.addEventListener(SESSION_EXPIRED_EVENT, handleSessionExpired)
    return () => {
      window.removeEventListener(ACCESS_TOKEN_REFRESHED_EVENT, handleAccessTokenRefreshed)
      window.removeEventListener(SESSION_EXPIRED_EVENT, handleSessionExpired)
    }
  }, [redirectToLogin])

  useEffect(() => {
    const timeoutId = window.setTimeout(() => {
      const hash = window.location.hash.startsWith('#') ? window.location.hash.slice(1) : ''
      const hashParams = new URLSearchParams(hash)
      const oauth2AccessToken = hashParams.get('accessToken')
      if (oauth2AccessToken) {
        persistAccessToken(oauth2AccessToken)
        window.history.replaceState(null, '', '/main')
        setPage('main')
        setNotice({ tone: 'success', message: '소셜 로그인이 완료되었습니다.' })
        return
      }

      const queryParams = new URLSearchParams(window.location.search)
      if (queryParams.get('oauth2Error') === 'true') {
        window.history.replaceState(null, '', '/')
        setPage('auth')
        setNotice({ tone: 'error', message: '소셜 로그인 처리에 실패했습니다. 다시 시도해 주세요.' })
      }
    }, 0)

    return () => window.clearTimeout(timeoutId)
  }, [persistAccessToken])

  const restoreSession = useCallback(async () => {
    try {
      let token = accessToken
      if (!token) {
        const refreshed = await refreshAccessToken()
        token = refreshed.accessToken
        persistAccessToken(token)
      }

      const status = await getAuthStatus(token)
      if (!status.authenticated || !status.user) {
        redirectToLogin('로그인이 필요합니다.')
        return
      }

      setCurrentUser({
        id: status.user.id,
        loginId: status.user.loginId,
        nickname: status.user.nickname,
        role: status.user.role,
      })
    } catch {
      redirectToLogin()
    }
  }, [accessToken, persistAccessToken, redirectToLogin])

  const verifyEmailToken = useCallback(async () => {
    const token = new URLSearchParams(window.location.search).get('token') ?? ''
    if (!token) {
      setEmailVerificationStatus('error')
      setNotice({ tone: 'error', message: '이메일 인증 토큰이 없습니다.' })
      return
    }

    setEmailVerificationStatus('loading')
    try {
      await confirmEmail(token)
      setEmailVerificationStatus('success')
      setNotice({ tone: 'success', message: '이메일 인증이 완료되었습니다. 이제 로그인할 수 있습니다.' })
    } catch (error: unknown) {
      setEmailVerificationStatus('error')
      setNotice({ tone: 'error', message: errorMessage(error) })
    }
  }, [])

  const verifyLoginIdRecoveryToken = useCallback(async () => {
    const token = new URLSearchParams(window.location.search).get('token') ?? ''
    if (!token) {
      setLoginIdRecoveryStatus('idle')
      setRecoveredLoginId('')
      return
    }

    setLoginIdRecoveryStatus('loading')
    setRecoveredLoginId('')
    try {
      const response = await confirmLoginIdRecovery(token)
      setRecoveredLoginId(response.loginId)
      setLoginIdRecoveryStatus('success')
      setNotice({ tone: 'success', message: '이메일 인증이 완료되어 아이디를 확인했습니다.' })
    } catch (error: unknown) {
      setLoginIdRecoveryStatus('error')
      setNotice({ tone: 'error', message: errorMessage(error) })
    }
  }, [])

  useEffect(() => {
    const handlePopState = () => setPage(resolvePage())
    window.addEventListener('popstate', handlePopState)
    return () => window.removeEventListener('popstate', handlePopState)
  }, [])

  useEffect(() => {
    if (page === 'main' || page === 'tripCreate' || page === 'tripDetail') {
      const timeoutId = window.setTimeout(() => {
        void restoreSession()
      }, 0)
      return () => window.clearTimeout(timeoutId)
    }
  }, [page, restoreSession])

  useEffect(() => {
    if (page === 'emailVerification') {
      const timeoutId = window.setTimeout(() => {
        void verifyEmailToken()
      }, 0)
      return () => window.clearTimeout(timeoutId)
    }
  }, [page, verifyEmailToken])

  useEffect(() => {
    if (page === 'findLoginId') {
      const timeoutId = window.setTimeout(() => {
        void verifyLoginIdRecoveryToken()
      }, 0)
      return () => window.clearTimeout(timeoutId)
    }
  }, [page, verifyLoginIdRecoveryToken])

  async function handleLogin(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const form = new FormData(event.currentTarget)
    setNotice(null)

    try {
      const response = await login({
        loginId: String(form.get('loginId') ?? '').trim(),
        password: String(form.get('password') ?? ''),
      })
      persistAccessToken(response.accessToken)
      setCurrentUser(response.user)
      navigate('/main')
    } catch (error) {
      setNotice({ tone: 'error', message: errorMessage(error) })
    }
  }

  async function handleSignup(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const form = new FormData(event.currentTarget)
    const password = String(form.get('password') ?? '')
    const passwordConfirm = String(form.get('passwordConfirm') ?? '')
    setNotice(null)

    if (password !== passwordConfirm) {
      setNotice({ tone: 'error', message: '비밀번호와 비밀번호 확인이 일치하지 않습니다.' })
      return
    }

    try {
      const response = await signup({
        loginId: String(form.get('loginId') ?? '').trim(),
        email: String(form.get('email') ?? '').trim(),
        password,
        nickname: String(form.get('nickname') ?? '').trim(),
      })
      setAuthMode('login')
      setNotice({
        tone: 'success',
        message: `${response.email}로 인증 메일을 보냈습니다. 이메일 인증 후 로그인하세요.`,
      })
    } catch (error) {
      setNotice({ tone: 'error', message: errorMessage(error) })
    }
  }

  async function handleFindLoginId(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const form = new FormData(event.currentTarget)
    const email = String(form.get('email') ?? '').trim()
    setNotice(null)

    try {
      await requestLoginIdRecovery({ email })
      setNotice({
        tone: 'success',
        message: '입력한 정보가 유효하면 아이디 찾기 인증 메일을 발송합니다.',
      })
    } catch (error) {
      setNotice({ tone: 'error', message: errorMessage(error) })
    }
  }

  async function handleResetPasswordRequest(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const form = new FormData(event.currentTarget)
    const loginId = String(form.get('loginId') ?? '').trim()
    const email = String(form.get('email') ?? '').trim()
    setNotice(null)
    setPasswordResetStatus('idle')

    try {
      await requestPasswordReset({ loginId, email })
      setNotice({
        tone: 'success',
        message: '입력한 정보가 유효하면 비밀번호 재설정 메일을 발송합니다.',
      })
    } catch (error) {
      setNotice({ tone: 'error', message: errorMessage(error) })
    }
  }

  async function handleResetPasswordConfirm(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const token = new URLSearchParams(window.location.search).get('token') ?? ''
    const form = new FormData(event.currentTarget)
    const newPassword = String(form.get('newPassword') ?? '')
    const newPasswordConfirm = String(form.get('newPasswordConfirm') ?? '')
    setNotice(null)

    if (!token) {
      setNotice({ tone: 'error', message: '비밀번호 재설정 토큰이 없습니다.' })
      return
    }
    if (newPassword !== newPasswordConfirm) {
      setNotice({ tone: 'error', message: '새 비밀번호와 비밀번호 확인이 일치하지 않습니다.' })
      return
    }

    setPasswordResetStatus('loading')
    try {
      await confirmPasswordReset({ token, newPassword })
      setPasswordResetStatus('success')
      setNotice({ tone: 'success', message: '비밀번호가 재설정되었습니다. 새 비밀번호로 로그인하세요.' })
    } catch (error) {
      setPasswordResetStatus('error')
      setNotice({ tone: 'error', message: errorMessage(error) })
    }
  }

  async function handleLogout() {
    try {
      await logout()
    } finally {
      clearAccessToken()
      navigate('/')
    }
  }

  function handleOAuth2Login(provider: OAuth2Provider) {
    window.location.href = oauth2AuthorizationUrl(provider)
  }

  if (page === 'emailVerification') {
    return (
      <AuthShell notice={notice} initiallyOpen>
        <section className="verification-card" aria-live="polite">
          <p className="eyebrow">Email verification</p>
          <h1>이메일 인증</h1>
          {emailVerificationStatus === 'loading' && <p>인증 링크를 확인하고 있습니다.</p>}
          {emailVerificationStatus === 'success' && <p>인증이 완료되었습니다. 로그인 페이지로 이동해 서비스를 이용하세요.</p>}
          {emailVerificationStatus === 'error' && <p>인증 링크를 처리하지 못했습니다. 링크가 만료되었거나 이미 사용되었을 수 있습니다.</p>}
          <button className="primary-action" type="button" onClick={() => navigate('/')}>
            로그인하러 가기
          </button>
        </section>
      </AuthShell>
    )
  }

  if (page === 'main') {
    return (
      <MainPage
        accessToken={accessToken}
        user={currentUser}
        onLogout={handleLogout}
        onCreateTrip={() => navigate('/trips/new')}
        onOpenTrip={(tripId) => navigate('/trips/' + tripId)}
      />
    )
  }

  if (page === 'tripCreate') {
    return (
      <TripCreatePage
        accessToken={accessToken}
        user={currentUser}
        onBackToMain={() => navigate('/main')}
        onCreatedTrip={(tripId) => navigate('/trips/' + tripId)}
        onLogout={handleLogout}
      />
    )
  }

  if (page === 'tripDetail') {
    return (
      <TripDetailPage
        accessToken={accessToken}
        key={window.location.pathname}
        tripId={window.location.pathname.split('/').filter(Boolean)[1] ?? ''}
        user={currentUser}
        onBackToMain={() => navigate('/main')}
        onLogout={handleLogout}
      />
    )
  }

  if (page === 'findLoginId') {
    return (
      <AuthShell notice={notice} initiallyOpen>
        <FindLoginIdCard
          onSubmit={handleFindLoginId}
          onBackToLogin={() => navigate('/')}
          onResetPassword={() => navigate('/auth/reset-password')}
          recoveryStatus={loginIdRecoveryStatus}
          recoveredLoginId={recoveredLoginId}
        />
      </AuthShell>
    )
  }

  if (page === 'resetPassword') {
    return (
      <AuthShell notice={notice} initiallyOpen>
        <ResetPasswordCard
          onRequestSubmit={handleResetPasswordRequest}
          onConfirmSubmit={handleResetPasswordConfirm}
          onBackToLogin={() => navigate('/')}
          onFindLoginId={() => navigate('/auth/find-login-id')}
          resetStatus={passwordResetStatus}
        />
      </AuthShell>
    )
  }

  return (
    <AuthShell notice={notice} initiallyOpen={Boolean(notice)}>
      <section className="auth-card" aria-labelledby="auth-card-title">
        <div className="brand-lockup" aria-label="PlanMate" translate="no">
          <span className="brand-mark" aria-hidden="true">P</span>
          <span>PlanMate</span>
        </div>
        <div className="card-heading">
          <p className="card-kicker">{authMode === 'login' ? '다시 만나 반가워요' : '첫 여행 준비'}</p>
          <h1 id="auth-card-title">
            {authMode === 'login' ? '계획 중인 여행으로 돌아가기' : '첫 여행 계획 시작하기'}
          </h1>
          <p>
            {authMode === 'login'
              ? '저장한 일정과 여행 조건을 불러옵니다.'
              : '가입 후 이메일 인증을 마치면 목적지와 여행 스타일을 입력할 수 있어요.'}
          </p>
        </div>

        <div className="auth-tabs" role="tablist" aria-label="인증 방식">
          <button
            id="login-tab"
            className={`auth-tab ${authMode === 'login' ? 'active' : ''}`}
            type="button"
            role="tab"
            aria-selected={authMode === 'login'}
            aria-controls="auth-panel"
            onClick={() => {
              setAuthMode('login')
              setNotice(null)
            }}
          >
            로그인
          </button>
          <button
            id="signup-tab"
            className={`auth-tab ${authMode === 'signup' ? 'active' : ''}`}
            type="button"
            role="tab"
            aria-selected={authMode === 'signup'}
            aria-controls="auth-panel"
            onClick={() => {
              setAuthMode('signup')
              setNotice(null)
            }}
          >
            회원가입
          </button>
        </div>

        <div id="auth-panel" role="tabpanel" aria-labelledby={`${authMode}-tab`}>
          {authMode === 'login' ? (
            <LoginForm
              onSubmit={handleLogin}
              onFindLoginId={() => navigate('/auth/find-login-id')}
              onResetPassword={() => navigate('/auth/reset-password')}
            />
          ) : (
            <SignupForm onSubmit={handleSignup} />
          )}
        </div>

        <div className="divider"><span />또는<span /></div>
        <div className="social-actions" aria-label="소셜 로그인">
          <button className="social-button google" type="button" onClick={() => handleOAuth2Login('google')}>
            <span className="social-mark google-mark" aria-hidden="true"><GoogleMark /></span>
            <span><span translate="no">Google</span>로 계속하기</span>
          </button>
          <button className="social-button naver" type="button" onClick={() => handleOAuth2Login('naver')}>
            <span className="social-mark" aria-hidden="true">N</span>
            <span>네이버로 계속하기</span>
          </button>
          <button className="social-button kakao" type="button" onClick={() => handleOAuth2Login('kakao')}>
            <span className="social-mark" aria-hidden="true">K</span>
            <span>카카오로 계속하기</span>
          </button>
        </div>
      </section>
    </AuthShell>
  )
}

function FindLoginIdCard({
  onSubmit,
  onBackToLogin,
  onResetPassword,
  recoveryStatus,
  recoveredLoginId,
}: {
  onSubmit: (event: FormEvent<HTMLFormElement>) => void
  onBackToLogin: () => void
  onResetPassword: () => void
  recoveryStatus: AsyncStatus
  recoveredLoginId: string
}) {
  const hasToken = new URLSearchParams(window.location.search).has('token')

  return (
    <section className="auth-card recovery-card" aria-label="아이디 찾기">
      <div className="brand-mark" aria-hidden="true">PM</div>
      <div className="card-heading">
        <p className="card-kicker">Find login ID</p>
        <h1>아이디 찾기</h1>
        <p>가입한 이메일로 인증 링크를 보내고, 인증이 완료되면 해당 이메일에 연결된 아이디를 안내합니다.</p>
      </div>

      {hasToken ? (
        <div className="auth-form" aria-live="polite">
          {recoveryStatus === 'loading' && <p className="form-guide">인증 링크를 확인하고 있습니다.</p>}
          {recoveryStatus === 'success' && (
            <div className="result-panel">
              <span>가입 아이디</span>
              <strong>{recoveredLoginId}</strong>
            </div>
          )}
          {recoveryStatus === 'error' && (
            <p className="form-guide">인증 링크를 처리하지 못했습니다. 링크가 만료되었거나 이미 사용되었을 수 있습니다.</p>
          )}
          <button className="primary-action" type="button" onClick={onBackToLogin}>로그인으로 돌아가기</button>
          <button className="secondary-action" type="button" onClick={onResetPassword}>비밀번호 재설정</button>
        </div>
      ) : (
        <form className="auth-form" onSubmit={onSubmit}>
          <label>
            <span>가입한 이메일</span>
            <input name="email" type="email" placeholder="가입할 때 사용한 이메일" autoComplete="email" spellCheck={false} required />
          </label>
          <p className="form-guide">보안을 위해 아이디는 이메일 인증 완료 후 안내하는 흐름으로 연결합니다.</p>
          <button className="primary-action" type="submit">인증 메일 받기</button>
          <button className="secondary-action" type="button" onClick={onBackToLogin}>로그인으로 돌아가기</button>
        </form>
      )}

      <div className="recovery-switch">
        <span>아이디는 알고 있나요?</span>
        <button className="text-action" type="button" onClick={onResetPassword}>비밀번호 재설정</button>
      </div>
    </section>
  )
}

function ResetPasswordCard({
  onRequestSubmit,
  onConfirmSubmit,
  onBackToLogin,
  onFindLoginId,
  resetStatus,
}: {
  onRequestSubmit: (event: FormEvent<HTMLFormElement>) => void
  onConfirmSubmit: (event: FormEvent<HTMLFormElement>) => void
  onBackToLogin: () => void
  onFindLoginId: () => void
  resetStatus: AsyncStatus
}) {
  const [newPassword, setNewPassword] = useState('')
  const [newPasswordConfirm, setNewPasswordConfirm] = useState('')
  const hasToken = new URLSearchParams(window.location.search).has('token')
  const passwordMismatch = newPasswordConfirm.length > 0 && newPassword !== newPasswordConfirm
  const canResetPassword = newPassword.length >= 8
    && newPassword.length <= 72
    && newPasswordConfirm.length >= 8
    && !passwordMismatch

  return (
    <section className="auth-card recovery-card" aria-label="비밀번호 재설정">
      <div className="brand-mark" aria-hidden="true">PM</div>
      <div className="card-heading">
        <p className="card-kicker">Reset password</p>
        <h1>비밀번호 재설정</h1>
        <p>아이디와 가입 이메일이 일치하면 이메일 인증 링크를 보내고, 인증 후 새 비밀번호를 설정합니다.</p>
      </div>

      {hasToken ? (
        <form className="auth-form" onSubmit={onConfirmSubmit}>
          {resetStatus === 'success' ? (
            <>
              <p className="form-guide">비밀번호 재설정이 완료되었습니다. 새 비밀번호로 로그인하세요.</p>
              <button className="primary-action" type="button" onClick={onBackToLogin}>로그인으로 돌아가기</button>
            </>
          ) : (
            <>
              <label>
                <span>새 비밀번호</span>
                <input
                  name="newPassword"
                  type="password"
                  placeholder="8자 이상"
                  autoComplete="new-password"
                  minLength={8}
                  maxLength={72}
                  value={newPassword}
                  onChange={(event) => setNewPassword(event.target.value)}
                  required
                />
              </label>
              <label>
                <span>새 비밀번호 확인</span>
                <input
                  name="newPasswordConfirm"
                  type="password"
                  placeholder="새 비밀번호를 한 번 더 입력"
                  autoComplete="new-password"
                  minLength={8}
                  maxLength={72}
                  value={newPasswordConfirm}
                  onChange={(event) => setNewPasswordConfirm(event.target.value)}
                  aria-invalid={passwordMismatch}
                  aria-describedby={passwordMismatch ? 'new-password-confirm-error' : undefined}
                  required
                />
              </label>
              {passwordMismatch && (
              <p className="field-error" id="new-password-confirm-error" aria-live="polite">
                  새 비밀번호와 비밀번호 확인이 일치하지 않습니다.
                </p>
              )}
              <p className="form-guide">이전 비밀번호는 확인할 수 없으므로 새 비밀번호로 다시 설정합니다.</p>
              <button className="primary-action" type="submit" disabled={!canResetPassword || resetStatus === 'loading'}>
                새 비밀번호 저장
              </button>
              <button className="secondary-action" type="button" onClick={onBackToLogin}>로그인으로 돌아가기</button>
            </>
          )}
        </form>
      ) : (
        <form className="auth-form" onSubmit={onRequestSubmit}>
          <label>
            <span>아이디</span>
            <input name="loginId" type="text" placeholder="아이디를 입력하세요" autoComplete="username" spellCheck={false} required />
          </label>
          <label>
            <span>가입한 이메일</span>
            <input name="email" type="email" placeholder="가입할 때 사용한 이메일" autoComplete="email" spellCheck={false} required />
          </label>
          <p className="form-guide">비밀번호 원문은 저장하지 않으므로 기존 비밀번호를 알려줄 수 없고, 새 비밀번호 설정 링크만 발송합니다.</p>
          <button className="primary-action" type="submit">재설정 메일 받기</button>
          <button className="secondary-action" type="button" onClick={onBackToLogin}>로그인으로 돌아가기</button>
        </form>
      )}

      <div className="recovery-switch">
        <span>아이디가 기억나지 않나요?</span>
        <button className="text-action" type="button" onClick={onFindLoginId}>아이디 찾기</button>
      </div>
    </section>
  )
}

function LoginForm({
  onSubmit,
  onFindLoginId,
  onResetPassword,
}: {
  onSubmit: (event: FormEvent<HTMLFormElement>) => void
  onFindLoginId: () => void
  onResetPassword: () => void
}) {
  return (
    <form className="auth-form" onSubmit={onSubmit}>
      <label>
        <span>아이디</span>
        <input name="loginId" type="text" autoComplete="username" spellCheck={false} required />
      </label>
      <label>
        <span>비밀번호</span>
        <input name="password" type="password" autoComplete="current-password" required />
      </label>
      <div className="account-recovery-actions" aria-label="계정 복구">
        <button className="text-action" type="button" onClick={onFindLoginId}>
          아이디 찾기
        </button>
        <span aria-hidden="true">|</span>
        <button className="text-action" type="button" onClick={onResetPassword}>
          비밀번호 재설정
        </button>
      </div>
      <button className="primary-action" type="submit">로그인</button>
    </form>
  )
}

function SignupForm({ onSubmit }: { onSubmit: (event: FormEvent<HTMLFormElement>) => void }) {
  const [nickname, setNickname] = useState('')
  const [password, setPassword] = useState('')
  const [passwordConfirm, setPasswordConfirm] = useState('')

  const passwordMismatch = passwordConfirm.length > 0 && password !== passwordConfirm

  return (
    <form className="auth-form" onSubmit={onSubmit}>
      <label>
        <span>아이디</span>
        <input name="loginId" type="text" placeholder="예: planmate_01" autoComplete="username" spellCheck={false} minLength={4} maxLength={50} required />
      </label>
      <label>
        <span>이메일</span>
        <input name="email" type="email" placeholder="예: travel@example.com" autoComplete="email" spellCheck={false} required />
      </label>
      <label>
        <span>닉네임</span>
        <input
          name="nickname"
          type="text"
          placeholder="2자 이상 30자 이하"
          autoComplete="nickname"
          minLength={2}
          maxLength={30}
          value={nickname}
          onChange={(event) => setNickname(event.target.value)}
          required
        />
      </label>
      <label>
        <span>비밀번호</span>
        <input
          name="password"
          type="password"
          placeholder="8자 이상"
          autoComplete="new-password"
          minLength={8}
          maxLength={72}
          value={password}
          onChange={(event) => setPassword(event.target.value)}
          required
        />
      </label>
      <label>
        <span>비밀번호 확인</span>
        <input
          name="passwordConfirm"
          type="password"
          placeholder="비밀번호를 한 번 더 입력"
          autoComplete="new-password"
          minLength={8}
          maxLength={72}
          value={passwordConfirm}
          onChange={(event) => setPasswordConfirm(event.target.value)}
          aria-invalid={passwordMismatch}
          aria-describedby={passwordMismatch ? 'password-confirm-error' : undefined}
          required
        />
      </label>
      {passwordMismatch && (
        <p className="field-error" id="password-confirm-error" aria-live="polite">
          비밀번호와 비밀번호 확인이 일치하지 않습니다.
        </p>
      )}
      <p className="form-guide">입력한 이메일로 인증 링크를 보내드려요.</p>
      <button className="primary-action" type="submit">회원가입</button>
    </form>
  )
}

function AuthShell({
  children,
  notice,
  initiallyOpen = false,
}: {
  children: React.ReactNode
  notice: Notice | null
  initiallyOpen?: boolean
}) {
  const [isAuthOpen, setIsAuthOpen] = useState(initiallyOpen || Boolean(notice))
  const dialogRef = useRef<HTMLDivElement>(null)
  const journeyButtonRef = useRef<HTMLButtonElement>(null)
  const hasOpenedRef = useRef(initiallyOpen)

  useEffect(() => {
    if (!isAuthOpen) {
      return
    }

    hasOpenedRef.current = true
    const journeyButton = journeyButtonRef.current
    const previousOverflow = document.body.style.overflow
    document.body.style.overflow = 'hidden'
    window.requestAnimationFrame(() => dialogRef.current?.focus())

    function handleKeyDown(event: KeyboardEvent) {
      if (event.key === 'Escape') {
        setIsAuthOpen(false)
        return
      }

      if (event.key !== 'Tab' || !dialogRef.current) {
        return
      }

      const focusableElements = Array.from(
        dialogRef.current.querySelectorAll<HTMLElement>(
          'button:not([disabled]), input:not([disabled]), a[href], [tabindex]:not([tabindex="-1"])',
        ),
      )
      const firstElement = focusableElements[0]
      const lastElement = focusableElements.at(-1)

      if (!firstElement || !lastElement) {
        event.preventDefault()
        return
      }

      if (event.shiftKey && document.activeElement === firstElement) {
        event.preventDefault()
        lastElement.focus()
      } else if (!event.shiftKey && document.activeElement === lastElement) {
        event.preventDefault()
        firstElement.focus()
      }
    }

    window.addEventListener('keydown', handleKeyDown)

    return () => {
      document.body.style.overflow = previousOverflow
      window.removeEventListener('keydown', handleKeyDown)
      if (hasOpenedRef.current) {
        window.requestAnimationFrame(() => journeyButton?.focus())
      }
    }
  }, [isAuthOpen])

  return (
    <main className="auth-page">
      <a className="skip-link" href="#journey-start">여행 시작하기로 바로가기</a>
      <div className="background-grid" aria-hidden="true" />
      <section className="auth-stage" aria-labelledby="auth-title" aria-hidden={isAuthOpen || undefined}>
        <aside className="brand-scene" aria-label="PlanMate 서비스 소개">
          <div className="scene-heading">
            <div className="scene-meta">
              <span className="scene-wordmark" translate="no">PlanMate</span>
            </div>
            <div className="scene-copy">
              <p className="eyebrow">이번엔 진짜 떠나볼까요?</p>
              <h2 id="auth-title">“어디 갈까?”만<br className="mobile-title-break" /> <em>고민이라면</em></h2>
              <p className="scene-description">여행에서 하고 싶은 것만 하나씩 골라보세요.</p>
            </div>
          </div>
          <TripPlanStory />
          <div className="scene-actions">
            <button
              className="journey-start-button"
              id="journey-start"
              ref={journeyButtonRef}
              type="button"
              onClick={() => setIsAuthOpen(true)}
            >
              계속하기
              <span aria-hidden="true">→</span>
            </button>
          </div>
        </aside>
      </section>
      {isAuthOpen && (
        <div
          className="auth-modal-layer"
          role="presentation"
          onMouseDown={(event) => {
            if (event.target === event.currentTarget) {
              setIsAuthOpen(false)
            }
          }}
        >
          <div
            className="auth-column"
            id="auth-content"
            ref={dialogRef}
            role="dialog"
            aria-label="PlanMate 로그인 및 회원가입"
            aria-modal="true"
            tabIndex={-1}
          >
            <button
              className="auth-modal-close"
              type="button"
              aria-label="로그인 창 닫기"
              onClick={() => setIsAuthOpen(false)}
            >
              <span aria-hidden="true">×</span>
            </button>
            {notice && (
              <p
                className={`notice ${notice.tone}`}
                role={notice.tone === 'error' ? 'alert' : 'status'}
                aria-live={notice.tone === 'error' ? 'assertive' : 'polite'}
              >
                {notice.message}
              </p>
            )}
            {children}
          </div>
        </div>
      )}
    </main>
  )
}

function GoogleMark() {
  return (
    <svg viewBox="0 0 18 18" width="18" height="18" focusable="false">
      <path fill="#4285F4" d="M17.64 9.205c0-.638-.057-1.252-.164-1.841H9v3.481h4.844a4.14 4.14 0 0 1-1.797 2.716v2.258h2.909c1.702-1.567 2.684-3.874 2.684-6.614Z" />
      <path fill="#34A853" d="M9 18c2.43 0 4.468-.806 5.956-2.181l-2.91-2.258c-.805.54-1.835.859-3.046.859-2.344 0-4.328-1.584-5.037-3.71H.956v2.332A9 9 0 0 0 9 18Z" />
      <path fill="#FBBC05" d="M3.963 10.71A5.41 5.41 0 0 1 3.681 9c0-.594.102-1.171.282-1.71V4.958H.956A9 9 0 0 0 0 9c0 1.452.347 2.827.956 4.042l3.007-2.332Z" />
      <path fill="#EA4335" d="M9 3.58c1.321 0 2.507.454 3.44 1.346l2.582-2.582C13.464.891 11.427 0 9 0A9 9 0 0 0 .956 4.958L3.963 7.29C4.672 5.164 6.656 3.58 9 3.58Z" />
    </svg>
  )
}

function TripPlanStory() {
  const [storyMoment, setStoryMoment] = useState(0)

  useEffect(() => {
    const motionPreference = window.matchMedia('(prefers-reduced-motion: reduce)')
    let timeoutId: number | undefined
    let cancelled = false

    function scheduleMoment(moment: number) {
      if (cancelled) {
        return
      }

      if (motionPreference.matches) {
        setStoryMoment(FINAL_STORY_MOMENT)
        return
      }

      setStoryMoment(moment)

      if (moment === FINAL_STORY_MOMENT) {
        return
      }

      timeoutId = window.setTimeout(
        () => scheduleMoment(moment + 1),
        STORY_MOMENT_DURATIONS[moment],
      )
    }

    function handleMotionPreferenceChange() {
      if (timeoutId !== undefined) {
        window.clearTimeout(timeoutId)
      }
      scheduleMoment(motionPreference.matches ? FINAL_STORY_MOMENT : 0)
    }

    scheduleMoment(0)
    motionPreference.addEventListener('change', handleMotionPreferenceChange)

    return () => {
      cancelled = true
      if (timeoutId !== undefined) {
        window.clearTimeout(timeoutId)
      }
      motionPreference.removeEventListener('change', handleMotionPreferenceChange)
    }
  }, [])

  return (
    <div className={`planning-story story-moment-${storyMoment}`}>
      <div className="thought-cloud" aria-hidden="true">
        {TRAVEL_THOUGHTS.map((thought, index) => (
          <span
            className={`thought-bubble thought-${index + 1} ${storyMoment > index ? 'is-visible' : ''}`}
            key={thought}
          >
            {thought}
          </span>
        ))}
      </div>

      <article className="plan-draft" aria-hidden="true">
        <ol className="plan-days">
          <BookletDay day="DAY 1" index={1} />
          <BookletDay day="DAY 2" index={2} />
          <BookletDay day="DAY 3" index={3} />
        </ol>
      </article>
    </div>
  )
}

function BookletDay({ day, index }: { day: string; index: number }) {
  const blockPatterns = [
    ['block-medium', 'block-short', 'block-wide'],
    ['block-wide', 'block-medium', 'block-short'],
    ['block-short', 'block-wide', 'block-medium'],
  ]

  return (
    <li className={`booklet-day booklet-day-${index}`}>
      <span className="plan-day-label">{day}</span>
      <span className="booklet-blocks">
        {blockPatterns[index - 1].map((blockClass, blockIndex) => (
          <i className={`booklet-block ${blockClass}`} key={`${blockClass}-${blockIndex}`} />
        ))}
      </span>
    </li>
  )
}

function errorMessage(error: unknown) {
  if (error instanceof ApiError) {
    if (error.code === 'EMAIL_NOT_VERIFIED') {
      return '이메일 인증이 필요합니다. 메일함에서 인증 링크를 확인하세요.'
    }
    if (error.code === 'INVALID_CREDENTIALS') {
      return '아이디 또는 비밀번호가 올바르지 않습니다.'
    }
    if (error.code === 'INVALID_TOKEN') {
      return '인증 링크가 올바르지 않습니다. 다시 요청해 주세요.'
    }
    if (error.code === 'EXPIRED_TOKEN') {
      return '인증 링크가 만료되었습니다. 다시 요청해 주세요.'
    }
    if (error.code === 'TOKEN_ALREADY_USED') {
      return '이미 사용된 인증 링크입니다. 다시 요청해 주세요.'
    }
    return error.message
  }
  return '요청 처리 중 오류가 발생했습니다.'
}

export default App
