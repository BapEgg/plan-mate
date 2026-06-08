import './App.css'

const travelNotes = [
  '숙소 이동',
  '동선 최적화',
  '친구 초대',
  '일정 공유',
]

const socialProviders = [
  { name: 'Google', label: 'Google로 계속하기', mark: 'G', tone: 'google' },
  { name: 'Naver', label: 'Naver로 계속하기', mark: 'N', tone: 'naver' },
  { name: 'Kakao', label: 'Kakao로 계속하기', mark: 'K', tone: 'kakao' },
]

function App() {
  return (
    <main className="auth-page">
      <div className="background-noise" aria-hidden="true" />
      <div className="route-orbit route-orbit-top" aria-hidden="true" />
      <div className="route-orbit route-orbit-bottom" aria-hidden="true" />

      <section className="auth-stage" aria-labelledby="auth-title">
        <aside className="brand-scene" aria-label="PlanMate service preview">
          <div className="floating-pin pin-green" aria-hidden="true">
            <span>⌖</span>
          </div>
          <div className="floating-pin pin-amber" aria-hidden="true">
            <span>✦</span>
          </div>
          <div className="floating-pin pin-blue" aria-hidden="true">
            <span>↗</span>
          </div>

          <div className="route-card" aria-hidden="true">
            <div className="map-preview">
              <span className="map-dot dot-one" />
              <span className="map-dot dot-two" />
              <span className="map-marker">⌖</span>
            </div>
            <div className="plan-row plan-row-primary">
              <span className="plan-icon">▣</span>
              <span className="plan-line plan-line-long" />
              <span className="plan-line plan-line-short" />
            </div>
            <div className="plan-row plan-row-warm">
              <span className="plan-icon">★</span>
              <span className="plan-line plan-line-long" />
              <span className="plan-line plan-line-short" />
            </div>
          </div>

          <div className="brand-copy">
            <p className="eyebrow">PlanMate</p>
            <h2>흩어진 여행 정보를 하나의 실행 가능한 일정으로</h2>
            <ul>
              {travelNotes.map((note) => (
                <li key={note}>{note}</li>
              ))}
            </ul>
          </div>
        </aside>

        <section className="auth-card" aria-label="Authentication form">
          <div className="brand-mark" aria-hidden="true">
            <span>PM</span>
          </div>
          <div className="card-heading">
            <p className="card-kicker">여행 플래너</p>
            <h1 id="auth-title">친구들과 함께 여행을 계획하세요</h1>
            <p>로그인하고 숙소, 동선, 일정 후보를 한 곳에서 정리하세요.</p>
          </div>

          <div className="auth-tabs" role="tablist" aria-label="인증 방식">
            <button className="auth-tab active" type="button" role="tab" aria-selected="true">
              로그인
            </button>
            <button className="auth-tab" type="button" role="tab" aria-selected="false">
              회원가입
            </button>
          </div>

          <form className="auth-form">
            <label>
              <span>아이디</span>
              <input type="text" placeholder="아이디를 입력하세요" autoComplete="username" />
            </label>
            <label>
              <span>비밀번호</span>
              <input type="password" placeholder="비밀번호를 입력하세요" autoComplete="current-password" />
            </label>

            <div className="account-links">
              <a href="#find-id">아이디 찾기</a>
              <span aria-hidden="true">|</span>
              <a href="#reset-password">비밀번호 찾기</a>
            </div>

            <button className="primary-action" type="button">
              로그인
            </button>
          </form>

          <div className="divider">
            <span />
            <p>또는</p>
            <span />
          </div>

          <div className="social-actions" aria-label="소셜 로그인">
            {socialProviders.map((provider) => (
              <button className="social-button" type="button" key={provider.name}>
                <span className={`social-mark ${provider.tone}`}>{provider.mark}</span>
                {provider.label}
              </button>
            ))}
          </div>

          <p className="terms">
            계속 진행하면 서비스 약관 및 개인정보 보호정책에 동의하는 것으로 간주됩니다.
          </p>
        </section>
      </section>
    </main>
  )
}

export default App
