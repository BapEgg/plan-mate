import { useState } from 'react'
import type { FormEvent } from 'react'
import type { AuthUser } from '../../api/auth'
import { ApiError } from '../../api/client'
import { createTrip } from '../../api/trips'
import type { CreateTripRequest } from '../../api/trips'
import './TripCreatePage.css'

type TripCreatePageProps = {
  accessToken: string
  user: AuthUser | null
  onBackToMain: () => void
  onCreatedTrip: (tripId: string) => void
  onLogout: () => void
}

type AsyncStatus = 'idle' | 'loading' | 'success' | 'error'

export function TripCreatePage({
  accessToken,
  user,
  onBackToMain,
  onCreatedTrip,
  onLogout,
}: TripCreatePageProps) {
  const [status, setStatus] = useState<AsyncStatus>('idle')
  const [formError, setFormError] = useState('')
  const [submitError, setSubmitError] = useState('')

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const form = new FormData(event.currentTarget)
    const payload: CreateTripRequest = {
      title: String(form.get('title') ?? '').trim(),
      destination: String(form.get('destination') ?? '').trim(),
      startDate: String(form.get('startDate') ?? ''),
      endDate: String(form.get('endDate') ?? ''),
    }

    if (!payload.title || !payload.destination || !payload.startDate || !payload.endDate) {
      setFormError('모든 항목을 입력하세요.')
      return
    }

    if (payload.startDate > payload.endDate) {
      setFormError('종료일은 시작일 이후여야 합니다.')
      return
    }

    setStatus('loading')
    setFormError('')
    setSubmitError('')

    try {
      const created = await createTrip(accessToken, payload)
      setStatus('success')
      onCreatedTrip(created.id)
    } catch (error: unknown) {
      setStatus('error')
      setSubmitError(toUserMessage(error))
    }
  }

  return (
    <main className="trip-create-page">
      <div className="trip-create-map-grid" aria-hidden="true" />
      <nav className="trip-create-nav" aria-label="여행 생성 내비게이션">
        <button className="trip-create-back-button" type="button" onClick={onBackToMain}>
          ← 메인
        </button>
        <div className="trip-create-brand">
          <span className="trip-create-brand-mark" aria-hidden="true">PM</span>
          <strong>PlanMate</strong>
        </div>
        <div className="trip-create-user-menu">
          <span>{user?.nickname ?? '여행자'}</span>
          <button className="ghost-button" type="button" onClick={onLogout}>
            로그아웃
          </button>
        </div>
      </nav>

      <section className="trip-create-shell" aria-labelledby="trip-create-title">
        <div className="trip-create-intro">
          <p className="eyebrow">New trip</p>
          <h1 id="trip-create-title">새 여행 만들기</h1>
          <p>
            여행 제목, 대표 여행지, 기간을 먼저 정하면 상세 화면에서 숙소, 예산, 이동수단, 취향을 이어서 채울 수 있습니다.
          </p>
        </div>

        <form className="trip-create-form" onSubmit={handleSubmit}>
          <label>
            <span>여행 제목</span>
            <input name="title" type="text" placeholder="예: 강릉 2박 3일" maxLength={60} required />
          </label>
          <label>
            <span>대표 여행지</span>
            <input name="destination" type="text" placeholder="예: 강릉" maxLength={60} required />
          </label>
          <div className="trip-create-date-grid">
            <label>
              <span>시작일</span>
              <input name="startDate" type="date" required />
            </label>
            <label>
              <span>종료일</span>
              <input name="endDate" type="date" required />
            </label>
          </div>
          {formError && <p className="field-error">{formError}</p>}
          {submitError && <p className="trip-create-submit-error" role="alert">{submitError}</p>}
          <p className="form-guide">
            생성 후 바로 여행 상세 화면으로 이동합니다.
          </p>
          <div className="trip-create-actions">
            <button className="primary-action" type="submit" disabled={!accessToken || status === 'loading'}>
              {status === 'loading' ? '생성 중' : '여행 카드 생성'}
            </button>
            <button className="secondary-action" type="button" onClick={onBackToMain}>
              취소
            </button>
          </div>
        </form>
      </section>
    </main>
  )
}

function toUserMessage(error: unknown) {
  if (error instanceof ApiError) {
    if (error.status === 401) {
      return '로그인이 만료되었습니다. 다시 로그인하세요.'
    }
    return error.message
  }
  return '요청 처리 중 오류가 발생했습니다.'
}
