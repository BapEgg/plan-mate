import { useEffect, useRef, useState } from 'react'
import type { ChangeEvent, CompositionEvent, FormEvent, KeyboardEvent } from 'react'
import type { AuthUser } from '../../api/auth'
import { ApiError } from '../../api/client'
import { autocompletePlaces } from '../../api/places'
import type { PlaceAutocompleteItem } from '../../api/places'
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
type TripCreateStep = 'DESTINATION' | 'CONDITIONS'
type SelectionPhase =
  | 'IDLE'
  | 'SEARCHING'
  | 'LOADING_PREVIEW'
  | 'ZOOMING'
  | 'READY_TO_CONFIRM'
  | 'NAVIGATING'

type PlacePreview = {
  placeId: string
  mainText: string
  secondaryText: string
  displayText: string
  searchScope: PlaceAutocompleteItem['searchScope']
}

const MIN_DESTINATION_QUERY_LENGTH = 2
const POPULAR_SEARCH_KEYWORDS = ['제주도', '강릉', '교토', '이탈리아', '방콕', '바르셀로나']

export function TripCreatePage({
  accessToken,
  user,
  onBackToMain,
  onCreatedTrip,
  onLogout,
}: TripCreatePageProps) {
  const [currentStep, setCurrentStep] = useState<TripCreateStep>('DESTINATION')
  const [title, setTitle] = useState('')
  const [searchQuery, setSearchQuery] = useState('')
  const [searchResults, setSearchResults] = useState<PlaceAutocompleteItem[]>([])
  const [selectedCandidate, setSelectedCandidate] = useState<PlaceAutocompleteItem | null>(null)
  const [selectedPreview, setSelectedPreview] = useState<PlacePreview | null>(null)
  const [draftDestination, setDraftDestination] = useState<PlaceAutocompleteItem | null>(null)
  const [selectionPhase, setSelectionPhase] = useState<SelectionPhase>('IDLE')
  const [startDate, setStartDate] = useState('')
  const [endDate, setEndDate] = useState('')
  const [submitStatus, setSubmitStatus] = useState<AsyncStatus>('idle')
  const [searchError, setSearchError] = useState('')
  const [formError, setFormError] = useState('')
  const [submitError, setSubmitError] = useState('')
  const [isComposing, setIsComposing] = useState(false)
  const [hasSearched, setHasSearched] = useState(false)

  const lastRequestedQueryRef = useRef('')
  const searchSequenceRef = useRef(0)
  const isComposingRef = useRef(false)
  const pendingSearchAfterCompositionRef = useRef(false)
  const previewCacheRef = useRef(new Map<string, PlacePreview>())
  const visualTimerRef = useRef<number[]>([])

  const trimmedTitle = title.trim()
  const trimmedSearchQuery = searchQuery.trim()
  const confirmedDestination = draftDestination
  const dateRangeValid = !startDate || !endDate || startDate <= endDate
  const canSearch = Boolean(
    accessToken
    && trimmedSearchQuery.length >= MIN_DESTINATION_QUERY_LENGTH
    && selectionPhase !== 'SEARCHING'
  )
  const canSubmit = Boolean(
    trimmedTitle
    && confirmedDestination
    && startDate
    && endDate
    && dateRangeValid
    && submitStatus !== 'loading'
  )
  const showNoResults = (
    hasSearched
    && selectionPhase !== 'SEARCHING'
    && !searchError
    && searchResults.length === 0
    && !selectedCandidate
  )

  useEffect(() => () => clearVisualTimers(), [])

  function clearVisualTimers() {
    visualTimerRef.current.forEach((timerId) => window.clearTimeout(timerId))
    visualTimerRef.current = []
  }

  function scheduleVisualTimer(callback: () => void, delay: number) {
    const timerId = window.setTimeout(callback, delay)
    visualTimerRef.current.push(timerId)
  }

  function handleSearchQueryChange(event: ChangeEvent<HTMLInputElement>) {
    setSearchQuery(event.target.value)
    setSearchError('')
    setFormError('')
    setSubmitError('')
    setHasSearched(false)
    setSearchResults([])
    lastRequestedQueryRef.current = ''

    if (selectedCandidate) {
      clearVisualTimers()
      setSelectedCandidate(null)
      setSelectedPreview(null)
      setDraftDestination(null)
      setSelectionPhase('IDLE')
    }
  }

  function handleCompositionStart() {
    isComposingRef.current = true
    setIsComposing(true)
  }

  function handleCompositionEnd(event: CompositionEvent<HTMLInputElement>) {
    isComposingRef.current = false
    setIsComposing(false)
    setSearchQuery(event.currentTarget.value)

    if (pendingSearchAfterCompositionRef.current) {
      pendingSearchAfterCompositionRef.current = false
      void requestDestinationSearch(event.currentTarget.value)
    }
  }

  function handleSearchKeyDown(event: KeyboardEvent<HTMLInputElement>) {
    if (event.key !== 'Enter') {
      return
    }

    event.preventDefault()
    if (event.nativeEvent.isComposing || isComposingRef.current) {
      pendingSearchAfterCompositionRef.current = true
      return
    }

    void requestDestinationSearch()
  }

  function handleSearchIntent() {
    if (isComposingRef.current) {
      pendingSearchAfterCompositionRef.current = true
      return
    }

    void requestDestinationSearch()
  }

  async function requestDestinationSearch(queryOverride?: string) {
    const query = (queryOverride ?? searchQuery).trim()

    if (isComposingRef.current) {
      return
    }

    if (query.length < MIN_DESTINATION_QUERY_LENGTH) {
      setSearchError('2글자 이상 입력한 뒤 검색해 주세요.')
      setHasSearched(false)
      return
    }

    if (lastRequestedQueryRef.current === query && hasSearched && !searchError) {
      return
    }

    clearVisualTimers()
    searchSequenceRef.current += 1
    const sequence = searchSequenceRef.current

    lastRequestedQueryRef.current = query
    setSearchQuery(query)
    setSearchResults([])
    setSelectedCandidate(null)
    setSelectedPreview(null)
    setDraftDestination(null)
    setSearchError('')
    setFormError('')
    setSubmitError('')
    setHasSearched(true)
    setSelectionPhase('SEARCHING')

    try {
      const response = await autocompletePlaces(accessToken, {
        query,
        languageCode: 'ko',
      })
      if (searchSequenceRef.current !== sequence) {
        return
      }
      setSearchResults(response.items.slice(0, 5))
      setSelectionPhase('IDLE')
    } catch (error: unknown) {
      if (searchSequenceRef.current !== sequence) {
        return
      }
      setSearchResults([])
      setSearchError(toSearchUserMessage(error))
      setSelectionPhase('IDLE')
    }
  }

  function handlePopularKeyword(keyword: string) {
    clearVisualTimers()
    setSearchQuery(keyword)
    setSearchResults([])
    setSelectedCandidate(null)
    setSelectedPreview(null)
    setDraftDestination(null)
    setSearchError('')
    setFormError('')
    setSubmitError('')
    setHasSearched(false)
    setSelectionPhase('IDLE')
    lastRequestedQueryRef.current = ''
    pendingSearchAfterCompositionRef.current = false
  }

  function handleCandidateSelect(candidate: PlaceAutocompleteItem) {
    if (selectedCandidate?.placeId === candidate.placeId && selectionPhase === 'READY_TO_CONFIRM') {
      confirmDestination(candidate)
      return
    }

    prepareDestinationPreview(candidate)
  }

  function prepareDestinationPreview(candidate: PlaceAutocompleteItem) {
    clearVisualTimers()
    setSelectedCandidate(candidate)
    setDraftDestination(null)
    setFormError('')
    setSubmitError('')
    setSelectionPhase('LOADING_PREVIEW')

    let preview = previewCacheRef.current.get(candidate.placeId)
    if (!preview) {
      preview = toPlacePreview(candidate)
      previewCacheRef.current.set(candidate.placeId, preview)
    }

    scheduleVisualTimer(() => {
      setSelectedPreview(preview)
      setSelectionPhase('ZOOMING')
    }, 120)

    scheduleVisualTimer(() => {
      setSelectionPhase('READY_TO_CONFIRM')
    }, 980)
  }

  function confirmDestination(candidate = selectedCandidate) {
    if (!candidate) {
      return
    }

    const preview = previewCacheRef.current.get(candidate.placeId) ?? toPlacePreview(candidate)
    previewCacheRef.current.set(candidate.placeId, preview)

    clearVisualTimers()
    setSelectedCandidate(candidate)
    setSelectedPreview(preview)
    setDraftDestination(candidate)
    setSearchQuery(candidate.displayText || candidate.mainText)
    setSearchResults([])
    setSearchError('')
    setFormError('')
    setSubmitError('')
    setSelectionPhase('NAVIGATING')

    scheduleVisualTimer(() => {
      setCurrentStep('CONDITIONS')
      setSelectionPhase('READY_TO_CONFIRM')
    }, 260)
  }

  function handleBackToDestinationStep() {
    setCurrentStep('DESTINATION')
    setDraftDestination(null)
    setSelectionPhase(selectedCandidate ? 'READY_TO_CONFIRM' : 'IDLE')
  }

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()

    if (!confirmedDestination) {
      setFormError('검색 결과에서 여행의 기준이 될 도시나 지역을 선택해 주세요.')
      setCurrentStep('DESTINATION')
      return
    }

    if (!trimmedTitle || !startDate || !endDate) {
      setFormError('모든 항목을 입력하세요.')
      return
    }

    if (!dateRangeValid) {
      setFormError('종료일은 시작일 이후여야 합니다.')
      return
    }

    const payload: CreateTripRequest = {
      title: trimmedTitle,
      destination: confirmedDestination.displayText,
      destinationPlaceId: confirmedDestination.placeId,
      startDate,
      endDate,
    }

    setSubmitStatus('loading')
    setFormError('')
    setSubmitError('')

    try {
      const created = await createTrip(accessToken, payload)
      setSubmitStatus('success')
      onCreatedTrip(created.id)
    } catch (error: unknown) {
      setSubmitStatus('error')
      setSubmitError(toUserMessage(error))
    }
  }

  return (
    <main className="trip-create-page">
      <div className="trip-create-map-grid" aria-hidden="true" />
      <TripCreateHeader
        activeStep={currentStep === 'DESTINATION' ? 1 : 2}
        user={user}
        onBackToMain={onBackToMain}
        onLogout={onLogout}
      />

      {currentStep === 'DESTINATION' ? (
        <TripCreateDestinationPage
          isComposing={isComposing}
          searchError={searchError}
          searchQuery={searchQuery}
          searchResults={searchResults}
          selectedCandidate={selectedCandidate}
          selectedPreview={selectedPreview}
          selectionPhase={selectionPhase}
          showNoResults={showNoResults}
          canSearch={canSearch}
          onCandidateConfirm={confirmDestination}
          onCandidateSelect={handleCandidateSelect}
          onCompositionEnd={handleCompositionEnd}
          onCompositionStart={handleCompositionStart}
          onPopularKeyword={handlePopularKeyword}
          onSearch={handleSearchIntent}
          onSearchKeyDown={handleSearchKeyDown}
          onSearchQueryChange={handleSearchQueryChange}
        />
      ) : (
        <TripConditionStep
          dateRangeValid={dateRangeValid}
          destination={confirmedDestination}
          formError={formError}
          startDate={startDate}
          submitError={submitError}
          submitStatus={submitStatus}
          title={title}
          endDate={endDate}
          canSubmit={canSubmit}
          onBack={handleBackToDestinationStep}
          onEndDateChange={(value) => {
            setEndDate(value)
            setFormError('')
            setSubmitError('')
          }}
          onStartDateChange={(value) => {
            setStartDate(value)
            setFormError('')
            setSubmitError('')
          }}
          onSubmit={handleSubmit}
          onTitleChange={(value) => {
            setTitle(value)
            setFormError('')
            setSubmitError('')
          }}
        />
      )}
    </main>
  )
}

function TripCreateHeader({
  activeStep,
  user,
  onBackToMain,
  onLogout,
}: {
  activeStep: 1 | 2
  user: AuthUser | null
  onBackToMain: () => void
  onLogout: () => void
}) {
  return (
    <nav className="trip-create-header" aria-label="여행 생성 내비게이션">
      <button className="trip-create-brand" type="button" onClick={onBackToMain} aria-label="메인으로 돌아가기">
        <span className="trip-create-brand-mark" aria-hidden="true">PM</span>
        <strong>PlanMate</strong>
      </button>

      <TripCreateStepIndicator activeStep={activeStep} />

      <div className="trip-create-header-actions">
        <button className="trip-help-button" type="button">도움말</button>
        <span className="trip-user-pill" aria-label="현재 사용자">{user?.nickname ?? '여행자'}</span>
        <button className="trip-logout-button" type="button" onClick={onLogout}>로그아웃</button>
      </div>
    </nav>
  )
}

function TripCreateStepIndicator({ activeStep }: { activeStep: 1 | 2 }) {
  const steps = [
    { id: 1, label: '목적지' },
    { id: 2, label: '여행 조건' },
    { id: 3, label: '일정 생성' },
  ]

  return (
    <ol className="trip-create-step-indicator" aria-label="여행 생성 단계">
      {steps.map((step, index) => (
        <li className={activeStep === step.id ? 'active' : ''} key={step.id}>
          <span>{step.id}</span>
          <strong>{step.label}</strong>
          {index < steps.length - 1 && <i aria-hidden="true" />}
        </li>
      ))}
    </ol>
  )
}

function TripCreateDestinationPage({
  canSearch,
  isComposing,
  searchError,
  searchQuery,
  searchResults,
  selectedCandidate,
  selectedPreview,
  selectionPhase,
  showNoResults,
  onCandidateConfirm,
  onCandidateSelect,
  onCompositionEnd,
  onCompositionStart,
  onPopularKeyword,
  onSearch,
  onSearchKeyDown,
  onSearchQueryChange,
}: {
  canSearch: boolean
  isComposing: boolean
  searchError: string
  searchQuery: string
  searchResults: PlaceAutocompleteItem[]
  selectedCandidate: PlaceAutocompleteItem | null
  selectedPreview: PlacePreview | null
  selectionPhase: SelectionPhase
  showNoResults: boolean
  onCandidateConfirm: (candidate?: PlaceAutocompleteItem | null) => void
  onCandidateSelect: (candidate: PlaceAutocompleteItem) => void
  onCompositionEnd: (event: CompositionEvent<HTMLInputElement>) => void
  onCompositionStart: () => void
  onPopularKeyword: (keyword: string) => void
  onSearch: () => void
  onSearchKeyDown: (event: KeyboardEvent<HTMLInputElement>) => void
  onSearchQueryChange: (event: ChangeEvent<HTMLInputElement>) => void
}) {
  return (
    <section className="trip-destination-page" aria-label="목적지 선택">
      <DestinationVisualStage
        selectedPreview={selectedPreview}
        selectionPhase={selectionPhase}
      />

      <DestinationSearchForm
        canSearch={canSearch}
        isComposing={isComposing}
        searchError={searchError}
        searchQuery={searchQuery}
        selectionPhase={selectionPhase}
        onCompositionEnd={onCompositionEnd}
        onCompositionStart={onCompositionStart}
        onSearch={onSearch}
        onSearchKeyDown={onSearchKeyDown}
        onSearchQueryChange={onSearchQueryChange}
      />

      <PopularDestinationChips onSelectKeyword={onPopularKeyword} />

      <DestinationCandidateList
        candidates={searchResults}
        selectedCandidate={selectedCandidate}
        selectionPhase={selectionPhase}
        showNoResults={showNoResults}
        onCandidateConfirm={onCandidateConfirm}
        onCandidateSelect={onCandidateSelect}
      />
    </section>
  )
}

function DestinationVisualStage({
  selectedPreview,
  selectionPhase,
}: {
  selectedPreview: PlacePreview | null
  selectionPhase: SelectionPhase
}) {
  const hasPreview = Boolean(selectedPreview)
  const isZooming = selectionPhase === 'ZOOMING' || selectionPhase === 'READY_TO_CONFIRM' || selectionPhase === 'NAVIGATING'
  const isMarkerVisible = selectionPhase === 'READY_TO_CONFIRM' || selectionPhase === 'NAVIGATING'
  const title = selectedPreview
    ? `${selectedPreview.mainText} 여행을 시작할까요?`
    : '어디로 떠나볼까요?'
  const description = selectedPreview
    ? '이곳이 맞다면 한 번 더 선택해 여행 조건을 정해 주세요.'
    : '도시나 지역을 검색하면 여행의 시작점을 함께 찾아드릴게요.'

  return (
    <div className={`destination-visual-stage ${hasPreview ? 'has-preview' : ''} ${isZooming ? 'is-zooming' : ''}`}>
      <div className="destination-stage-badge">
        <span />
        단계 1 / 3 · 목적지 선택
      </div>
      <div className="destination-globe-wrap" aria-hidden="true">
        {!hasPreview ? <InitialGlobeVisual /> : <DestinationZoomVisual />}
        <DestinationMarker
          isVisible={isMarkerVisible}
          label={selectedPreview?.mainText ?? '여행지'}
        />
      </div>
      <div className="destination-stage-copy" aria-live="polite">
        <h1>{title}</h1>
        <p>{description}</p>
      </div>
    </div>
  )
}

function InitialGlobeVisual() {
  return (
    <div className="initial-globe-visual">
      <span className="globe-orbit orbit-one" />
      <span className="globe-orbit orbit-two" />
      <span className="globe-dot dot-one" />
      <span className="globe-dot dot-two" />
      <span className="globe-dot dot-three" />
      <div className="globe-sphere">
        <span className="continent continent-one" />
        <span className="continent continent-two" />
        <span className="continent continent-three" />
        <span className="continent continent-four" />
        <span className="initial-marker" />
      </div>
    </div>
  )
}

function DestinationZoomVisual() {
  return (
    <div className="destination-zoom-visual">
      <span className="focus-vignette" />
      <span className="terrain-line terrain-one" />
      <span className="terrain-line terrain-two" />
      <span className="terrain-line terrain-three" />
      <span className="focus-ring focus-ring-one" />
      <span className="focus-ring focus-ring-two" />
      <span className="route-speck speck-one" />
      <span className="route-speck speck-two" />
      <span className="route-speck speck-three" />
    </div>
  )
}

function DestinationMarker({ isVisible, label }: { isVisible: boolean; label: string }) {
  return (
    <div className={`destination-marker-stage ${isVisible ? 'visible' : ''}`}>
      <span className="destination-marker-pin" />
      <span className="destination-marker-pulse" />
      <strong>{label}</strong>
    </div>
  )
}

function DestinationSearchForm({
  canSearch,
  isComposing,
  searchError,
  searchQuery,
  selectionPhase,
  onCompositionEnd,
  onCompositionStart,
  onSearch,
  onSearchKeyDown,
  onSearchQueryChange,
}: {
  canSearch: boolean
  isComposing: boolean
  searchError: string
  searchQuery: string
  selectionPhase: SelectionPhase
  onCompositionEnd: (event: CompositionEvent<HTMLInputElement>) => void
  onCompositionStart: () => void
  onSearch: () => void
  onSearchKeyDown: (event: KeyboardEvent<HTMLInputElement>) => void
  onSearchQueryChange: (event: ChangeEvent<HTMLInputElement>) => void
}) {
  const guide = getSearchGuide({
    isComposing,
    queryLength: searchQuery.trim().length,
    selectionPhase,
  })

  return (
    <div className="destination-search-section">
      <label className="destination-search-label">
        <span className="trip-create-sr-only">도시, 국가 또는 지역 검색</span>
        <span className="destination-search-control">
          <input
            type="text"
            placeholder="도시, 국가 또는 지역을 검색해 주세요"
            value={searchQuery}
            maxLength={120}
            onChange={onSearchQueryChange}
            onCompositionStart={onCompositionStart}
            onCompositionEnd={onCompositionEnd}
            onKeyDown={onSearchKeyDown}
            autoComplete="off"
            aria-busy={selectionPhase === 'SEARCHING'}
            aria-describedby="destination-search-guide destination-search-status"
          />
          <button
            className="destination-search-icon-button"
            type="button"
            disabled={!canSearch}
            onClick={onSearch}
            aria-label="목적지 검색"
          >
            <span className="destination-search-icon" aria-hidden="true" />
          </button>
        </span>
      </label>
      <p
        className={`destination-search-guide ${searchError && selectionPhase !== 'SEARCHING' ? 'error' : ''}`}
        id="destination-search-guide"
      >
        {searchError && selectionPhase !== 'SEARCHING' ? searchError : guide}
      </p>
    </div>
  )
}

function PopularDestinationChips({ onSelectKeyword }: { onSelectKeyword: (keyword: string) => void }) {
  return (
    <div className="popular-destination-row" aria-label="인기 검색어">
      <span>인기 검색</span>
      <div>
        {POPULAR_SEARCH_KEYWORDS.map((keyword) => (
          <button type="button" key={keyword} onClick={() => onSelectKeyword(keyword)}>
            {keyword}
          </button>
        ))}
      </div>
    </div>
  )
}

function DestinationCandidateList({
  candidates,
  selectedCandidate,
  selectionPhase,
  showNoResults,
  onCandidateConfirm,
  onCandidateSelect,
}: {
  candidates: PlaceAutocompleteItem[]
  selectedCandidate: PlaceAutocompleteItem | null
  selectionPhase: SelectionPhase
  showNoResults: boolean
  onCandidateConfirm: (candidate?: PlaceAutocompleteItem | null) => void
  onCandidateSelect: (candidate: PlaceAutocompleteItem) => void
}) {
  return (
    <section className="destination-candidate-area" id="destination-search-status" aria-live="polite">
      {selectionPhase === 'SEARCHING' && (
        <p className="destination-search-state">도시나 지역을 검색하고 있어요.</p>
      )}

      {showNoResults && (
        <div className="destination-search-empty">
          <strong>도시나 지역을 찾을 수 없어요.</strong>
          <span>철자를 확인하거나 상위 지역 또는 국가명을 함께 입력해 주세요.</span>
        </div>
      )}

      {candidates.length > 0 && (
        <ul className="destination-candidate-list" aria-label="검색 후보">
          {candidates.map((candidate) => (
            <li key={candidate.placeId}>
              <DestinationCandidateCard
                candidate={candidate}
                isSelected={selectedCandidate?.placeId === candidate.placeId}
                selectionPhase={selectionPhase}
                onConfirm={() => onCandidateConfirm(candidate)}
                onSelect={() => onCandidateSelect(candidate)}
              />
            </li>
          ))}
        </ul>
      )}
    </section>
  )
}

function DestinationCandidateCard({
  candidate,
  isSelected,
  selectionPhase,
  onConfirm,
  onSelect,
}: {
  candidate: PlaceAutocompleteItem
  isSelected: boolean
  selectionPhase: SelectionPhase
  onConfirm: () => void
  onSelect: () => void
}) {
  const isPreparing = isSelected && (selectionPhase === 'LOADING_PREVIEW' || selectionPhase === 'ZOOMING')
  const isReady = isSelected && selectionPhase === 'READY_TO_CONFIRM'

  return (
    <article className={`destination-candidate-card ${isSelected ? 'selected' : ''}`}>
      <button
        className="candidate-select-button"
        type="button"
        aria-pressed={isSelected}
        onClick={onSelect}
      >
        <span className="candidate-check" aria-hidden="true">{isSelected ? '✓' : ''}</span>
        <span className="candidate-copy">
          <span className="candidate-topline">
            <strong>{candidate.mainText}</strong>
            <em>{searchScopeLabel(candidate.searchScope)}</em>
          </span>
          {candidate.secondaryText && <small>{candidate.secondaryText}</small>}
          {isPreparing && <small className="candidate-status">목적지 미리보기를 준비하고 있어요.</small>}
          {isReady && <small className="candidate-status">이곳이 맞다면 한 번 더 눌러 주세요.</small>}
        </span>
      </button>
      {isReady && (
        <button className="candidate-confirm-button" type="button" onClick={onConfirm}>
          {candidate.mainText} 여행 시작하기
        </button>
      )}
    </article>
  )
}

function TripConditionStep({
  canSubmit,
  dateRangeValid,
  destination,
  endDate,
  formError,
  startDate,
  submitError,
  submitStatus,
  title,
  onBack,
  onEndDateChange,
  onStartDateChange,
  onSubmit,
  onTitleChange,
}: {
  canSubmit: boolean
  dateRangeValid: boolean
  destination: PlaceAutocompleteItem | null
  endDate: string
  formError: string
  startDate: string
  submitError: string
  submitStatus: AsyncStatus
  title: string
  onBack: () => void
  onEndDateChange: (value: string) => void
  onStartDateChange: (value: string) => void
  onSubmit: (event: FormEvent<HTMLFormElement>) => void
  onTitleChange: (value: string) => void
}) {
  return (
    <form className="trip-condition-page" onSubmit={onSubmit}>
      <section className="condition-hero-card" aria-label="선택한 목적지">
        <span>목적지 선택 완료</span>
        <h1>{destination?.mainText ?? '선택한 지역'} 여행 조건을 정해볼까요?</h1>
        <p>{destination?.secondaryText ?? destination?.displayText}</p>
        <button type="button" onClick={onBack}>이전 단계로 돌아가기</button>
      </section>

      <section className="condition-form-card" aria-label="여행 조건 입력">
        <div className="trip-section-heading">
          <span>STEP 2</span>
          <h2>여행 조건 입력</h2>
          <p>선택한 목적지를 기준으로 여행방 이름과 기간을 정해 주세요.</p>
        </div>

        <label>
          <span>여행방 이름</span>
          <input
            name="title"
            type="text"
            placeholder="예: 강릉 2박 3일"
            maxLength={60}
            value={title}
            onChange={(event) => onTitleChange(event.target.value)}
            required
          />
        </label>

        <div className="trip-create-date-grid">
          <label>
            <span>시작일</span>
            <input
              name="startDate"
              type="date"
              value={startDate}
              onChange={(event) => onStartDateChange(event.target.value)}
              required
            />
          </label>
          <label>
            <span>종료일</span>
            <input
              name="endDate"
              type="date"
              value={endDate}
              onChange={(event) => onEndDateChange(event.target.value)}
              aria-invalid={!dateRangeValid}
              required
            />
          </label>
        </div>

        <div className="condition-next-card">
          <strong>다음 단계에서는</strong>
          <ul>
            <li>선택한 도시나 지역을 기준으로 장소 후보를 준비해요.</li>
            <li>상세 화면에서 숙소와 여행 취향을 이어서 입력해요.</li>
            <li>AI 일정 생성에서 하루 단위 동선을 조합해요.</li>
          </ul>
        </div>

        {formError && <p className="trip-create-field-error">{formError}</p>}
        {submitError && <p className="trip-create-submit-error" role="alert">{submitError}</p>}

        <div className="trip-create-sticky-actions">
          <button className="trip-create-secondary-action" type="button" onClick={onBack}>
            이전 단계
          </button>
          <button className="trip-create-primary-action" type="submit" disabled={!canSubmit}>
            {submitStatus === 'loading' ? '생성 중' : '여행방 만들고 일정 준비하기'}
          </button>
        </div>
      </section>
    </form>
  )
}

function getSearchGuide({
  isComposing,
  queryLength,
  selectionPhase,
}: {
  isComposing: boolean
  queryLength: number
  selectionPhase: SelectionPhase
}) {
  if (selectionPhase === 'SEARCHING') {
    return '검색 결과를 불러오고 있어요.'
  }
  if (isComposing) {
    return '한글 입력을 확정한 뒤 Enter 또는 돋보기 아이콘으로 검색해 주세요.'
  }
  if (queryLength > 0 && queryLength < MIN_DESTINATION_QUERY_LENGTH) {
    return '2글자 이상 입력하면 검색할 수 있어요.'
  }
  return 'Enter를 누르거나 돋보기 아이콘을 클릭하면 검색합니다.'
}

function toPlacePreview(candidate: PlaceAutocompleteItem): PlacePreview {
  return {
    placeId: candidate.placeId,
    mainText: candidate.mainText,
    secondaryText: candidate.secondaryText,
    displayText: candidate.displayText,
    searchScope: candidate.searchScope,
  }
}

function searchScopeLabel(searchScope: PlaceAutocompleteItem['searchScope']) {
  return searchScope === 'CITY' ? '도시' : '지역'
}

function toSearchUserMessage(error: unknown) {
  if (error instanceof ApiError && error.status === 401) {
    return '로그인이 만료되었습니다. 다시 로그인하세요.'
  }
  return '장소 검색 서비스를 일시적으로 사용할 수 없어요. 잠시 후 다시 시도해 주세요.'
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
