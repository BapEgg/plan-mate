import { useCallback, useEffect, useState } from 'react'
import type { FormEvent } from 'react'
import './App.css'
import {
  ApiError,
  confirmEmail,
  getAuthStatus,
  login,
  logout,
  refreshAccessToken,
  signup,
} from './api/auth'
import type { AuthUser } from './api/auth'

type Page = 'auth' | 'emailVerification' | 'main'
type AuthMode = 'login' | 'signup'
type NoticeTone = 'info' | 'success' | 'error'

type Notice = {
  tone: NoticeTone
  message: string
}

const ACCESS_TOKEN_KEY = 'planmate.accessToken'

function resolvePage(): Page {
  if (window.location.pathname === '/main') {
    return 'main'
  }
  if (window.location.pathname === '/auth/email-verification') {
    return 'emailVerification'
  }
  return 'auth'
}

function App() {
  const [page, setPage] = useState<Page>(() => resolvePage())
  const [authMode, setAuthMode] = useState<AuthMode>('login')
  const [notice, setNotice] = useState<Notice | null>(null)
  const [accessToken, setAccessToken] = useState(() => localStorage.getItem(ACCESS_TOKEN_KEY) ?? '')
  const [currentUser, setCurrentUser] = useState<AuthUser | null>(null)
  const [emailVerificationStatus, setEmailVerificationStatus] = useState<'idle' | 'loading' | 'success' | 'error'>('idle')

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

  useEffect(() => {
    const handlePopState = () => setPage(resolvePage())
    window.addEventListener('popstate', handlePopState)
    return () => window.removeEventListener('popstate', handlePopState)
  }, [])

  useEffect(() => {
    if (page === 'main') {
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

  async function handleLogout() {
    try {
      await logout()
    } finally {
      clearAccessToken()
      navigate('/')
    }
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
    return <MainPage user={currentUser} onLogout={handleLogout} />
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

        {authMode === 'login' ? <LoginForm onSubmit={handleLogin} /> : <SignupForm onSubmit={handleSignup} />}

        <div className="divider"><span />또는<span /></div>
        <div className="social-actions" aria-label="소셜 로그인">
          <button className="social-button" type="button" disabled>Google 준비 중</button>
          <button className="social-button" type="button" disabled>Naver 준비 중</button>
          <button className="social-button" type="button" disabled>Kakao 준비 중</button>
        </div>
      </section>
    </AuthShell>
  )
}

function LoginForm({ onSubmit }: { onSubmit: (event: FormEvent<HTMLFormElement>) => void }) {
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

function MainPage({ user, onLogout }: { user: AuthUser | null; onLogout: () => void }) {
  return (
    <main className="main-page">
      <nav className="main-nav">
        <strong>PlanMate</strong>
        <button className="ghost-button" type="button" onClick={onLogout}>로그아웃</button>
      </nav>
      <section className="hero-panel">
        <p className="eyebrow">Welcome</p>
        <h1>{user ? `${user.nickname}님, 다음 여행을 계획해볼까요?` : '여행 계획을 불러오는 중입니다.'}</h1>
        <p>현재는 인증 연결 확인용 메인 페이지입니다. 다음 단계에서 여행 생성, 숙소 등록, AI 일정 생성 기능을 이 화면에 연결하면 됩니다.</p>
        <div className="main-actions">
          <button className="primary-action" type="button">새 여행 만들기</button>
          <button className="secondary-action" type="button">내 여행 보기</button>
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
    return error.message
  }
  return '요청 처리 중 오류가 발생했습니다.'
}

export default App
