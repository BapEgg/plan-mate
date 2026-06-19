import { useCallback, useEffect, useState } from 'react'
import type { FormEvent } from 'react'
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

type Page = 'auth' | 'emailVerification' | 'findLoginId' | 'resetPassword' | 'main' | 'tripCreate' | 'tripDetail'
type AuthMode = 'login' | 'signup'
type NoticeTone = 'info' | 'success' | 'error'

type Notice = {
  tone: NoticeTone
  message: string
}

type AsyncStatus = 'idle' | 'loading' | 'success' | 'error'

const ACCESS_TOKEN_KEY = 'planmate.accessToken'

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
  const [accessToken, setAccessToken] = useState(() => localStorage.getItem(ACCESS_TOKEN_KEY) ?? '')
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
    localStorage.setItem(ACCESS_TOKEN_KEY, token)
    setAccessToken(token)
  }, [])

  const clearAccessToken = useCallback(() => {
    localStorage.removeItem(ACCESS_TOKEN_KEY)
    setAccessToken('')
    setCurrentUser(null)
  }, [])

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
        clearAccessToken()
        navigate('/')
        setNotice({ tone: 'error', message: '로그인이 필요합니다.' })
        return
      }

      setCurrentUser({
        id: status.user.id,
        loginId: status.user.loginId,
        nickname: status.user.nickname,
        role: status.user.role,
      })
    } catch {
      clearAccessToken()
      navigate('/')
      setNotice({ tone: 'error', message: '로그인이 필요합니다.' })
    }
  }, [accessToken, clearAccessToken, navigate, persistAccessToken])

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
      <AuthShell notice={notice}>
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
        tripId={window.location.pathname.split('/').filter(Boolean)[1] ?? ''}
        user={currentUser}
        onBackToMain={() => navigate('/main')}
        onLogout={handleLogout}
      />
    )
  }

  if (page === 'findLoginId') {
    return (
      <AuthShell notice={notice}>
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
      <AuthShell notice={notice}>
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
    <AuthShell notice={notice}>
      <section className="auth-card" aria-label="인증 폼">
        <div className="brand-mark" aria-hidden="true">PM</div>
        <div className="card-heading">
          <p className="card-kicker">PlanMate</p>
          <h1>여행 계획을 실행 가능한 일정으로</h1>
          <p>회원가입 후 이메일 인증을 완료하면 여행 일정 만들기를 시작할 수 있습니다.</p>
        </div>

        <div className="auth-tabs" role="tablist" aria-label="인증 방식">
          <button
            className={`auth-tab ${authMode === 'login' ? 'active' : ''}`}
            type="button"
            role="tab"
            aria-selected={authMode === 'login'}
            onClick={() => {
              setAuthMode('login')
              setNotice(null)
            }}
          >
            로그인
          </button>
          <button
            className={`auth-tab ${authMode === 'signup' ? 'active' : ''}`}
            type="button"
            role="tab"
            aria-selected={authMode === 'signup'}
            onClick={() => {
              setAuthMode('signup')
              setNotice(null)
            }}
          >
            회원가입
          </button>
        </div>

        {authMode === 'login' ? (
          <LoginForm
            onSubmit={handleLogin}
            onFindLoginId={() => navigate('/auth/find-login-id')}
            onResetPassword={() => navigate('/auth/reset-password')}
          />
        ) : (
          <SignupForm onSubmit={handleSignup} />
        )}

        <div className="divider"><span />또는<span /></div>
        <div className="social-actions" aria-label="소셜 로그인">
          <button className="social-button google" type="button" onClick={() => handleOAuth2Login('google')}>
            Google로 계속하기
          </button>
          <button className="social-button naver" type="button" onClick={() => handleOAuth2Login('naver')}>
            Naver로 계속하기
          </button>
          <button className="social-button kakao" type="button" onClick={() => handleOAuth2Login('kakao')}>
            Kakao로 계속하기
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
            <input name="email" type="email" placeholder="가입할 때 사용한 이메일" autoComplete="email" required />
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
                <p className="field-error" id="new-password-confirm-error">
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
            <input name="loginId" type="text" placeholder="아이디를 입력하세요" autoComplete="username" required />
          </label>
          <label>
            <span>가입한 이메일</span>
            <input name="email" type="email" placeholder="가입할 때 사용한 이메일" autoComplete="email" required />
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
        <input name="loginId" type="text" placeholder="아이디를 입력하세요" autoComplete="username" required />
      </label>
      <label>
        <span>비밀번호</span>
        <input name="password" type="password" placeholder="비밀번호를 입력하세요" autoComplete="current-password" required />
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

  const nicknameValid = nickname.trim().length >= 2 && nickname.trim().length <= 30
  const passwordValid = password.length >= 8 && password.length <= 72
  const passwordMismatch = passwordConfirm.length > 0 && password !== passwordConfirm
  const canSubmit = nicknameValid
    && passwordValid
    && passwordConfirm.length >= 8
    && !passwordMismatch

  return (
    <form className="auth-form" onSubmit={onSubmit}>
      <label>
        <span>아이디</span>
        <input name="loginId" type="text" placeholder="영문, 숫자, ., _, - 조합" autoComplete="username" minLength={4} maxLength={50} required />
      </label>
      <label>
        <span>이메일</span>
        <input name="email" type="email" placeholder="인증 메일을 받을 주소" autoComplete="email" required />
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
        <p className="field-error" id="password-confirm-error">
          비밀번호와 비밀번호 확인이 일치하지 않습니다.
        </p>
      )}
      <p className="form-guide">모든 항목을 입력하고 비밀번호가 일치해야 회원가입할 수 있습니다.</p>
      <button className="primary-action" type="submit" disabled={!canSubmit}>회원가입</button>
    </form>
  )
}

function AuthShell({ children, notice }: { children: React.ReactNode; notice: Notice | null }) {
  return (
    <main className="auth-page">
      <div className="background-grid" aria-hidden="true" />
      <section className="auth-stage" aria-labelledby="auth-title">
        <aside className="brand-scene" aria-label="PlanMate 서비스 소개">
          <p className="eyebrow">AI-assisted travel planner</p>
          <h2 id="auth-title">흩어진 여행 정보를 하나의 실행 가능한 일정으로 정리합니다.</h2>
          <div className="route-card" aria-hidden="true">
            <span className="route-node node-start">서울</span>
            <span className="route-line" />
            <span className="route-node node-middle">강릉</span>
            <span className="route-line" />
            <span className="route-node node-end">속초</span>
          </div>
          <ul className="feature-list">
            <li>숙소와 이동 시간을 반영한 일정</li>
            <li>친구 초대와 공동 편집 준비</li>
            <li>이메일 인증 기반 안전한 로그인</li>
          </ul>
        </aside>
        <div className="auth-column">
          {notice && <p className={`notice ${notice.tone}`}>{notice.message}</p>}
          {children}
        </div>
      </section>
    </main>
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
