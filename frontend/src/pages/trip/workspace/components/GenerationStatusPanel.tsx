import type { GenerationStatus, ItineraryGenerationDetailResponse } from '../../../../api/trips'

export function GenerationStatusPanel({ generation, hasItinerary, notice, onRefresh }: {
  generation: ItineraryGenerationDetailResponse | null
  hasItinerary: boolean
  notice: string
  onRefresh: () => void
}) {
  const view = generationPresentation(generation, hasItinerary)
  const progressIndex = generation ? generationProgressIndex(generation.status) : -1
  const steps = generation?.status === 'FAILED'
    ? ['요청 접수', '후보 수집', '생성 실패', '저장 완료']
    : ['요청 접수', '후보 수집', '일정 검증', '저장 완료']
  return (
    <section className={`generation-status-panel ${view.tone}`} aria-live="polite">
      <div className="generation-status-copy">
        <span className="section-kicker">{view.eyebrow}</span>
        <h2>{view.title}</h2>
        <p>{view.description}</p>
        {generation && generation.candidateCount > 0 && <small>수집된 장소 후보 {generation.candidateCount}개</small>}
        {notice && <small className="generation-notice">{notice}</small>}
      </div>
      <ol className="generation-progress" aria-label="일정 생성 진행 단계">
        {steps.map((step, index) => (
          <li className={index < progressIndex ? 'complete' : index === progressIndex ? generation?.status === 'FAILED' ? 'failed' : 'active' : ''} key={step}>
            <span>{index + 1}</span><strong>{step}</strong>
          </li>
        ))}
      </ol>
      <button className="refresh-button" type="button" onClick={onRefresh}>최신 상태 확인</button>
    </section>
  )
}

export function WorkspaceSetupNotice({ generation }: { generation: ItineraryGenerationDetailResponse | null }) {
  const view = generation ? generationPresentation(generation, false) : null
  return (
    <section className="workspace-setup-notice" aria-live="polite">
      <strong>{view?.eyebrow ?? '일정 연결 전'}</strong>
      <p>{view?.description ?? '일정이 저장되면 방문 순서와 장소 위치가 아래 화면에 바로 표시됩니다.'}</p>
    </section>
  )
}

function generationPresentation(generation: ItineraryGenerationDetailResponse | null, hasItinerary: boolean) {
  if (generation?.status === 'COMPLETED' || (!generation && hasItinerary)) return { eyebrow: '일정 생성 완료', title: '검증을 통과한 일정이 저장되었습니다.', description: '날짜별 장소와 방문 시간을 선택해 상세 정보를 확인할 수 있습니다.', tone: 'completed' }
  if (!generation) return { eyebrow: '일정 생성 전', title: '아직 생성된 일정이 없습니다.', description: '여행 생성 화면에서 일정 생성을 요청하면 진행 상태가 이곳에 표시됩니다.', tone: 'idle' }
  const previousItineraryNotice = hasItinerary ? ' 기존에 저장된 일정은 아래에서 계속 확인할 수 있습니다.' : ''
  switch (generation.status) {
    case 'CREATED': return { eyebrow: '새 일정 요청 접수', title: '일정 생성 요청을 안전하게 저장했습니다.', description: `비동기 Worker가 요청을 가져갈 때까지 잠시 기다려 주세요.${previousItineraryNotice}`, tone: 'processing' }
    case 'COLLECTING_CANDIDATES': return { eyebrow: '새 장소 후보 수집 중', title: '여행 조건에 맞는 실제 장소를 찾고 있습니다.', description: `관심사와 이동 조건을 기준으로 일정에 사용할 후보를 정리하고 있습니다.${previousItineraryNotice}`, tone: 'processing' }
    case 'READY_FOR_PLANNING': return { eyebrow: '새 후보 수집 완료', title: '검증 가능한 일정 초안을 준비할 수 있습니다.', description: `장소 후보 수집을 마쳤으며, AI 응답을 서버 규칙으로 검증하는 단계입니다.${previousItineraryNotice}`, tone: 'ready' }
    case 'FAILED': return { eyebrow: '새 일정 생성 실패', title: '이번 요청을 완료하지 못했습니다.', description: `${generation.failureReason ?? '실패 원인을 확인한 뒤 일정 생성을 다시 요청해 주세요.'}${previousItineraryNotice}`, tone: 'failed' }
    default: return { eyebrow: '일정 생성 중', title: '일정을 준비하고 있습니다.', description: '최신 상태를 확인해 주세요.', tone: 'processing' }
  }
}

function generationProgressIndex(status: GenerationStatus) {
  return { CREATED: 0, COLLECTING_CANDIDATES: 1, READY_FOR_PLANNING: 2, COMPLETED: 3, FAILED: 2 }[status]
}
