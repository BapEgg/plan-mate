import { useEffect, useRef, useState } from 'react'
import type { ChangeEvent, CompositionEvent, FormEvent, KeyboardEvent, ReactNode } from 'react'
import type { AuthUser } from '../../api/auth'
import { ApiError } from '../../api/client'
import { autocompleteAccommodations, autocompletePlaces, autocompletePlacesInDestination } from '../../api/places'
import type { PlaceAutocompleteItem } from '../../api/places'
import {
  createItineraryGeneration,
  createTrip,
  getAiRequest,
  getItineraryGeneration,
  getManualPrompt,
  getTripDetail,
  submitManualResponse,
  validateManualResponse,
} from '../../api/trips'
import type { AiItineraryDraft, CreateTripRequest, ItineraryGenerationCreateResponse, ItineraryGenerationDetailResponse } from '../../api/trips'
import type { AiItineraryValidationReport } from '../../api/itineraryValidation'
import { connectTripRealtimeEvents, ITINERARY_GENERATION_STATUS_CHANGED } from '../../api/realtime'
import type { ItineraryGenerationStatusChangedPayload } from '../../api/realtime'
import { AiItineraryValidationReportPanel } from './AiItineraryValidationReportPanel'
import coupleMascotUrl from '../../assets/mascots/couple.png'
import coworkersMascotUrl from '../../assets/mascots/coworkers.png'
import familyMascotUrl from '../../assets/mascots/family.png'
import friendsMascotUrl from '../../assets/mascots/friends.png'
import otherMascotUrl from '../../assets/mascots/other.png'
import parentsMascotUrl from '../../assets/mascots/parents.png'
import soloMascotUrl from '../../assets/mascots/solo.png'
import './TripCreatePage.css'

type TripCreatePageProps = {
  accessToken: string
  user: AuthUser | null
  onBackToMain: () => void
  onCreatedTrip: (tripId: string) => void
  onLogout: () => void
}

type AsyncStatus = 'idle' | 'loading' | 'success' | 'error'
type TripCreateStep = 'DESTINATION' | 'INFO'
type TripInfoStepId =
  | 'BASIC'
  | 'COMPANION'
  | 'BUDGET'
  | 'PREFERENCE'
  | 'ACCOMMODATION'
  | 'REQUESTS'
  | 'REVIEW'
  | 'GENERATING'
type StepDirection = 'forward' | 'backward'
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

type TripDuration = {
  days: number
  nights: number
  dateLabels: string[]
}

type CompanionType = 'SOLO' | 'COUPLE' | 'FRIENDS' | 'FAMILY' | 'PARENTS' | 'COWORKERS' | 'OTHER'
type ChildAgeGroup = 'INFANT' | 'PRESCHOOL' | 'ELEMENTARY' | 'TEEN'
type CurrencyCode = 'KRW' | 'JPY' | 'USD' | 'EUR'
type BudgetLevel = 'VALUE' | 'BALANCED' | 'COMFORT'
type BudgetItem = 'LODGING' | 'TRANSPORT' | 'FOOD' | 'FLIGHT'
type TravelPace = 'RELAXED' | 'BALANCED' | 'PACKED'
type InterestId =
  | 'FOOD'
  | 'SIGHTSEEING'
  | 'CAFE'
  | 'CULTURE'
  | 'NATURE'
  | 'SHOPPING'
  | 'PHOTO'
  | 'NIGHT_VIEW'
  | 'ACTIVITY'
  | 'REST'
  | 'ART'
  | 'THEME_PARK'
  | 'LOCAL'
type TransportMode = 'WALK' | 'PUBLIC_TRANSIT' | 'RENTAL_CAR' | 'TAXI' | 'BIKE' | 'TOUR'
type AccommodationMode = 'UNDECIDED' | 'PLACE_SEARCH'
type AccommodationArea = 'TOURIST_CENTER' | 'TRANSIT' | 'QUIET' | 'ANYWHERE'
type ScheduleTimeMode = 'DEFAULT' | 'CUSTOM'
type AvoidItem =
  | 'EARLY_MORNING'
  | 'LATE_NIGHT'
  | 'LONG_WALK'
  | 'MANY_TRANSFERS'
  | 'CROWDED_PLACE'
  | 'SHOPPING'
  | 'MUSEUM'
  | 'EXPENSIVE_RESTAURANT'
  | 'TIGHT_SCHEDULE'
type TrackedItineraryGeneration = ItineraryGenerationCreateResponse & {
  failureReason?: string | null
}

const MIN_DESTINATION_QUERY_LENGTH = 2
const MIN_ACCOMMODATION_QUERY_LENGTH = 2
const MIN_MUST_VISIT_QUERY_LENGTH = 2
const DEFAULT_DAILY_START_TIME = '08:00'
const DEFAULT_DAILY_END_TIME = '20:00'
const POPULAR_SEARCH_KEYWORDS = ['제주도', '강릉', '교토', '이탈리아', '방콕', '바르셀로나']
const TRIP_INFO_STEPS: Array<{ id: Exclude<TripInfoStepId, 'GENERATING'>; label: string; shortLabel: string }> = [
  { id: 'BASIC', label: '기본 정보', shortLabel: '기본' },
  { id: 'COMPANION', label: '동행 정보', shortLabel: '동행' },
  { id: 'BUDGET', label: '예산', shortLabel: '예산' },
  { id: 'PREFERENCE', label: '여행 성향', shortLabel: '성향' },
  { id: 'ACCOMMODATION', label: '숙소', shortLabel: '숙소' },
  { id: 'REQUESTS', label: '추가 요청', shortLabel: '요청' },
  { id: 'REVIEW', label: '입력 확인', shortLabel: '확인' },
]
const COMPANION_OPTIONS: Array<{ id: CompanionType; label: string; description: string }> = [
  { id: 'SOLO', label: '혼자', description: '나만의 속도로 이동해요.' },
  { id: 'COUPLE', label: '커플', description: '둘이 보내는 시간을 고려해요.' },
  { id: 'FRIENDS', label: '친구', description: '함께 즐길 장소를 넓혀요.' },
  { id: 'FAMILY', label: '가족', description: '휴식과 이동 부담을 조절해요.' },
  { id: 'PARENTS', label: '부모님과', description: '걷는 거리와 휴식을 신경 써요.' },
  { id: 'COWORKERS', label: '동료', description: '단체 이동에 맞춰요.' },
  { id: 'OTHER', label: '기타', description: '동행 특성에 맞게 조정해요.' },
]
const MASCOT_IMAGE_BY_COMPANION: Record<CompanionType, string> = {
  SOLO: soloMascotUrl,
  COUPLE: coupleMascotUrl,
  FRIENDS: friendsMascotUrl,
  FAMILY: familyMascotUrl,
  PARENTS: parentsMascotUrl,
  COWORKERS: coworkersMascotUrl,
  OTHER: otherMascotUrl,
}
const CHILD_AGE_OPTIONS: Array<{ id: ChildAgeGroup; label: string }> = [
  { id: 'INFANT', label: '영유아' },
  { id: 'PRESCHOOL', label: '미취학' },
  { id: 'ELEMENTARY', label: '초등학생' },
  { id: 'TEEN', label: '청소년' },
]
const CURRENCY_OPTIONS: Array<{ id: CurrencyCode; label: string }> = [
  { id: 'KRW', label: 'KRW 대한민국 원' },
  { id: 'JPY', label: 'JPY 일본 엔' },
  { id: 'USD', label: 'USD 미국 달러' },
  { id: 'EUR', label: 'EUR 유로' },
]
const BUDGET_LEVEL_OPTIONS: Array<{ id: BudgetLevel; label: string; description: string }> = [
  { id: 'VALUE', label: '가성비 중심', description: '필수 비용을 아끼고 핵심 장소에 집중해요.' },
  { id: 'BALANCED', label: '적당히', description: '식사, 이동, 방문지를 균형 있게 잡아요.' },
  { id: 'COMFORT', label: '여유롭게', description: '편한 이동과 좋은 경험에 예산을 더 써요.' },
]
const BUDGET_ITEMS: Array<{ id: BudgetItem; label: string }> = [
  { id: 'LODGING', label: '숙박비' },
  { id: 'TRANSPORT', label: '교통비' },
  { id: 'FOOD', label: '식비' },
  { id: 'FLIGHT', label: '항공권' },
]
const PACE_OPTIONS: Array<{ id: TravelPace; label: string; description: string }> = [
  { id: 'RELAXED', label: '느긋하게', description: '하루 2~3곳, 충분한 휴식' },
  { id: 'BALANCED', label: '적당하게', description: '하루 3~4곳, 관광과 휴식 균형' },
  { id: 'PACKED', label: '알차게', description: '하루 4~5곳, 많은 장소 방문' },
]
const INTEREST_OPTIONS: Array<{ id: InterestId; label: string }> = [
  { id: 'FOOD', label: '음식' },
  { id: 'SIGHTSEEING', label: '관광 명소' },
  { id: 'CAFE', label: '카페' },
  { id: 'CULTURE', label: '역사·문화' },
  { id: 'NATURE', label: '자연' },
  { id: 'SHOPPING', label: '쇼핑' },
  { id: 'PHOTO', label: '사진' },
  { id: 'NIGHT_VIEW', label: '야경' },
  { id: 'ACTIVITY', label: '체험' },
  { id: 'REST', label: '휴식' },
  { id: 'ART', label: '전시·예술' },
  { id: 'THEME_PARK', label: '테마파크' },
  { id: 'LOCAL', label: '로컬 여행' },
]
const TRANSPORT_OPTIONS: Array<{ id: TransportMode; label: string }> = [
  { id: 'WALK', label: '도보' },
  { id: 'PUBLIC_TRANSIT', label: '대중교통' },
  { id: 'RENTAL_CAR', label: '렌터카' },
  { id: 'TAXI', label: '택시' },
  { id: 'BIKE', label: '자전거' },
  { id: 'TOUR', label: '투어 차량' },
]
const ACCOMMODATION_MODE_OPTIONS: Array<{ id: AccommodationMode; label: string; description: string }> = [
  { id: 'UNDECIDED', label: '아직 숙소를 정하지 않았어요', description: '목적지의 주요 지역을 기준으로 일정을 만들어요.' },
  { id: 'PLACE_SEARCH', label: '지도에서 정확한 숙소를 선택할게요', description: '숙소 검색은 다음 연동 단계에서 정확히 선택해요.' },
]
const ACCOMMODATION_AREA_OPTIONS: Array<{ id: AccommodationArea; label: string }> = [
  { id: 'TOURIST_CENTER', label: '중심 관광지 근처' },
  { id: 'TRANSIT', label: '대중교통이 편한 곳' },
  { id: 'QUIET', label: '조용한 지역' },
  { id: 'ANYWHERE', label: '상관없어요' },
]
const AVOID_OPTIONS: Array<{ id: AvoidItem; label: string }> = [
  { id: 'EARLY_MORNING', label: '이른 아침 시작' },
  { id: 'LATE_NIGHT', label: '늦은 밤 일정' },
  { id: 'LONG_WALK', label: '긴 도보' },
  { id: 'MANY_TRANSFERS', label: '잦은 환승' },
  { id: 'CROWDED_PLACE', label: '붐비는 장소' },
  { id: 'SHOPPING', label: '쇼핑 일정' },
  { id: 'MUSEUM', label: '박물관·전시' },
  { id: 'EXPENSIVE_RESTAURANT', label: '비싼 식당' },
  { id: 'TIGHT_SCHEDULE', label: '빡빡한 일정' },
]
const MANUAL_HANDOFF_ENABLED = import.meta.env.VITE_MANUAL_HANDOFF_ENABLED === 'true'

function isGenerationReadyForManualHandoff(generation: TrackedItineraryGeneration | null) {
  return generation?.status === 'READY_FOR_PLANNING'
}

function generationStatusMessage(generation: TrackedItineraryGeneration) {
  if (generation.status === 'FAILED') {
    return generation.failureReason
      ? `?쇱젙 ?앹꽦???ㅽ뙣?덉뒿?덈떎. ${generation.failureReason}`
      : '?쇱젙 ?앹꽦???ㅽ뙣?덉뒿?덈떎.'
  }
  if (generation.status === 'COMPLETED') {
    return '?쇱젙????λ릺?덉뒿?덈떎. ?곸꽭 ?붾㈃?쇰줈 ?대룞?⑸땲??'
  }
  if (isGenerationReadyForManualHandoff(generation)) {
    return `후보 ${generation.candidateCount}개를 수집했고 ${generation.status} 상태가 되었습니다.`
  }
  return `일정 생성 요청을 접수했습니다. 현재 ${generation.status} 상태입니다.`
}

function generationCandidateMessage(generation: TrackedItineraryGeneration) {
  if (generation.status === 'FAILED') {
    return generation.failureReason ?? '?꾨낫 ?섏쭛 ?먮뒗 ?쇱젙 以鍮?以??ㅽ뙣?덉뒿?덈떎.'
  }
  if (generation.status === 'COMPLETED') {
    return '??λ맂 ?쇱젙???곸꽭 ?붾㈃?먯꽌 ?뺤씤?????덉뒿?덈떎.'
  }
  if (isGenerationReadyForManualHandoff(generation)) {
    return `후보 ${generation.candidateCount}개를 실제 Google Places 결과에서 수집했습니다.`
  }
  return '후보 수집은 비동기 작업으로 처리됩니다.'
}

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
  const [createdTripId, setCreatedTripId] = useState('')
  const [itineraryGeneration, setItineraryGeneration] = useState<TrackedItineraryGeneration | null>(null)
  const [manualPrompt, setManualPrompt] = useState('')
  const [aiRequestJson, setAiRequestJson] = useState('')
  const [manualResponseJson, setManualResponseJson] = useState('')
  const [manualStatus, setManualStatus] = useState<AsyncStatus>('idle')
  const [manualMessage, setManualMessage] = useState('')
  const [manualValidationReport, setManualValidationReport] = useState<AiItineraryValidationReport | null>(null)
  const [searchError, setSearchError] = useState('')
  const [formError, setFormError] = useState('')
  const [submitError, setSubmitError] = useState('')
  const [isComposing, setIsComposing] = useState(false)
  const [hasSearched, setHasSearched] = useState(false)
  const [infoStep, setInfoStep] = useState<TripInfoStepId>('BASIC')
  const [stepDirection, setStepDirection] = useState<StepDirection>('forward')
  const [companionCount, setCompanionCount] = useState(2)
  const [companionType, setCompanionType] = useState<CompanionType>('FRIENDS')
  const [hasChildren, setHasChildren] = useState(false)
  const [childCount, setChildCount] = useState(1)
  const [childAgeGroup, setChildAgeGroup] = useState<ChildAgeGroup>('ELEMENTARY')
  const [hasSeniors, setHasSeniors] = useState(false)
  const [seniorCount, setSeniorCount] = useState(1)
  const [currencyCode, setCurrencyCode] = useState<CurrencyCode>('KRW')
  const [budgetAmount, setBudgetAmount] = useState('')
  const [budgetLevel, setBudgetLevel] = useState<BudgetLevel>('BALANCED')
  const [includedBudgetItems, setIncludedBudgetItems] = useState<BudgetItem[]>(['LODGING', 'TRANSPORT', 'FOOD'])
  const [travelPace, setTravelPace] = useState<TravelPace>('BALANCED')
  const [interests, setInterests] = useState<InterestId[]>(['FOOD', 'CAFE', 'REST'])
  const [primaryTransportMode, setPrimaryTransportMode] = useState<TransportMode>('PUBLIC_TRANSIT')
  const [secondaryTransportModes, setSecondaryTransportModes] = useState<TransportMode[]>(['WALK'])
  const [accommodationMode, setAccommodationMode] = useState<AccommodationMode>('UNDECIDED')
  const [accommodationArea, setAccommodationArea] = useState<AccommodationArea>('TRANSIT')
  const [accommodationQuery, setAccommodationQuery] = useState('')
  const [accommodationResults, setAccommodationResults] = useState<PlaceAutocompleteItem[]>([])
  const [selectedAccommodation, setSelectedAccommodation] = useState<PlaceAutocompleteItem | null>(null)
  const [accommodationSearchStatus, setAccommodationSearchStatus] = useState<AsyncStatus>('idle')
  const [accommodationSearchError, setAccommodationSearchError] = useState('')
  const [isAccommodationComposing, setIsAccommodationComposing] = useState(false)
  const [checkInTime, setCheckInTime] = useState('15:00')
  const [checkOutTime, setCheckOutTime] = useState('11:00')
  const [scheduleTimeMode, setScheduleTimeMode] = useState<ScheduleTimeMode>('DEFAULT')
  const [dailyStartTime, setDailyStartTime] = useState(DEFAULT_DAILY_START_TIME)
  const [dailyEndTime, setDailyEndTime] = useState(DEFAULT_DAILY_END_TIME)
  const [mustVisitQuery, setMustVisitQuery] = useState('')
  const [mustVisitResults, setMustVisitResults] = useState<PlaceAutocompleteItem[]>([])
  const [mustVisitPlaces, setMustVisitPlaces] = useState<PlaceAutocompleteItem[]>([])
  const [mustVisitSearchStatus, setMustVisitSearchStatus] = useState<AsyncStatus>('idle')
  const [mustVisitSearchError, setMustVisitSearchError] = useState('')
  const [isMustVisitComposing, setIsMustVisitComposing] = useState(false)
  const [avoidItems, setAvoidItems] = useState<AvoidItem[]>([])
  const [freeRequest, setFreeRequest] = useState('')

  const lastRequestedQueryRef = useRef('')
  const searchSequenceRef = useRef(0)
  const isComposingRef = useRef(false)
  const pendingSearchAfterCompositionRef = useRef(false)
  const accommodationSearchSequenceRef = useRef(0)
  const accommodationComposingRef = useRef(false)
  const mustVisitSearchSequenceRef = useRef(0)
  const mustVisitComposingRef = useRef(false)
  const previewCacheRef = useRef(new Map<string, PlacePreview>())
  const visualTimerRef = useRef<number[]>([])
  const completedNavigationScheduledRef = useRef(false)

  const trimmedTitle = title.trim()
  const trimmedSearchQuery = searchQuery.trim()
  const confirmedDestination = draftDestination
  const dateRangeValid = !startDate || !endDate || startDate <= endDate
  const tripDuration = getTripDuration(startDate, endDate)
  const budgetNumericAmount = parseCurrencyAmount(budgetAmount)
  const appliedDailyStartTime = scheduleTimeMode === 'DEFAULT' ? DEFAULT_DAILY_START_TIME : dailyStartTime
  const appliedDailyEndTime = scheduleTimeMode === 'DEFAULT' ? DEFAULT_DAILY_END_TIME : dailyEndTime
  const dailyScheduleRangeValid = appliedDailyStartTime < appliedDailyEndTime
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
    && dailyScheduleRangeValid
    && interests.length > 0
    && (accommodationMode === 'UNDECIDED' || Boolean(selectedAccommodation))
    && childAndSeniorCountValid(companionCount, hasChildren ? childCount : 0, hasSeniors ? seniorCount : 0)
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

  useEffect(() => {
    if (!MANUAL_HANDOFF_ENABLED || !createdTripId || !itineraryGeneration?.generationId) {
      return undefined
    }

    let active = true
    const generationId = itineraryGeneration.generationId

    async function refetchGeneration() {
      try {
        const latest = await getItineraryGeneration(accessToken, createdTripId, generationId)
        if (!active) {
          return
        }
        await applyGenerationDetail(latest)
      } catch (error: unknown) {
        if (!active) {
          return
        }
        setManualStatus('error')
        setManualMessage(toUserMessage(error))
      }
    }

    const connection = connectTripRealtimeEvents({
      accessToken,
      tripId: createdTripId,
      onConnect: () => {
        void refetchGeneration()
      },
      onError: (message) => {
        if (active) {
          setManualMessage(message)
        }
      },
      onEvent: (event) => {
        if (event.type !== ITINERARY_GENERATION_STATUS_CHANGED || event.payload.generationId !== generationId) {
          return
        }
        void applyGenerationPayload(event.payload).catch((error: unknown) => {
          if (active) {
            setManualStatus('error')
            setManualMessage(toUserMessage(error))
          }
        })
      },
    })

    return () => {
      active = false
      connection.disconnect()
    }
  }, [accessToken, createdTripId, itineraryGeneration?.generationId])

  async function handleAccommodationSearch() {
    if (accommodationMode !== 'PLACE_SEARCH') {
      return
    }
    if (accommodationComposingRef.current) {
      return
    }
    if (!confirmedDestination) {
      setAccommodationSearchError('목적지를 먼저 선택해 주세요.')
      setAccommodationSearchStatus('error')
      return
    }

    const query = accommodationQuery.trim()
    if (query.length < MIN_ACCOMMODATION_QUERY_LENGTH || selectedAccommodation?.displayText === accommodationQuery) {
      setAccommodationSearchError('숙소명 또는 주소를 2글자 이상 입력해 주세요.')
      setAccommodationSearchStatus('error')
      return
    }

    const sequence = accommodationSearchSequenceRef.current + 1
    accommodationSearchSequenceRef.current = sequence
    setAccommodationSearchStatus('loading')
    setAccommodationSearchError('')
    try {
      const response = await autocompleteAccommodations(accessToken, {
        query,
        destinationPlaceId: confirmedDestination.placeId,
        languageCode: 'ko',
      })
      if (accommodationSearchSequenceRef.current !== sequence) {
        return
      }
      setAccommodationResults(response.items.slice(0, 5))
      setAccommodationSearchStatus('success')
    } catch (error: unknown) {
      if (accommodationSearchSequenceRef.current !== sequence) {
        return
      }
      setAccommodationResults([])
      setAccommodationSearchError(toSearchUserMessage(error))
      setAccommodationSearchStatus('error')
    }
  }

  function clearVisualTimers() {
    visualTimerRef.current.forEach((timerId) => window.clearTimeout(timerId))
    visualTimerRef.current = []
  }

  function scheduleVisualTimer(callback: () => void, delay: number) {
    const timerId = window.setTimeout(callback, delay)
    visualTimerRef.current.push(timerId)
  }

  async function applyGenerationDetail(generation: ItineraryGenerationDetailResponse) {
    await applyGenerationSnapshot({
      generationId: generation.generationId,
      status: generation.status,
      candidateCount: generation.candidateCount,
      failureReason: generation.failureReason,
    })
  }

  async function applyGenerationPayload(payload: ItineraryGenerationStatusChangedPayload) {
    await applyGenerationSnapshot({
      generationId: payload.generationId,
      status: payload.status,
      candidateCount: payload.candidateCount,
      failureReason: payload.failureReason,
    })
  }

  async function applyGenerationSnapshot(generation: TrackedItineraryGeneration) {
    setItineraryGeneration(generation)
    setManualMessage(generationStatusMessage(generation))

    if (generation.status === 'READY_FOR_PLANNING') {
      setManualStatus('success')
      setSubmitStatus('success')
    }
    if (generation.status === 'FAILED') {
      setManualStatus('error')
      setSubmitStatus('error')
      return
    }
    if (generation.status === 'COMPLETED' && createdTripId) {
      setManualStatus('success')
      setSubmitStatus('success')
      await openCompletedTrip(createdTripId)
    }
  }

  async function openCompletedTrip(tripIdToOpen: string) {
    if (completedNavigationScheduledRef.current) {
      return
    }
    completedNavigationScheduledRef.current = true
    await getTripDetail(accessToken, tripIdToOpen)
    scheduleVisualTimer(() => onCreatedTrip(tripIdToOpen), 700)
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
      setInfoStep('BASIC')
      setStepDirection('forward')
      setSelectionPhase('NAVIGATING')

    scheduleVisualTimer(() => {
      setCurrentStep('INFO')
      setSelectionPhase('READY_TO_CONFIRM')
    }, 260)
  }

  function handleBackToDestinationStep() {
    setCurrentStep('DESTINATION')
    setDraftDestination(null)
    setSelectionPhase(selectedCandidate ? 'READY_TO_CONFIRM' : 'IDLE')
  }

  function goToInfoStep(nextStep: TripInfoStepId, direction: StepDirection) {
    setStepDirection(direction)
    setInfoStep(nextStep)
    setFormError('')
    setSubmitError('')
  }

  function handleInfoNext() {
    const error = getTripInfoStepError(infoStep, {
      title: trimmedTitle,
      startDate,
      endDate,
      dateRangeValid,
      companionCount,
      childCount: hasChildren ? childCount : 0,
      seniorCount: hasSeniors ? seniorCount : 0,
      budgetAmount: budgetNumericAmount,
      budgetLevel,
      interests,
      accommodationMode,
      selectedAccommodation,
      dailyScheduleRangeValid,
    })

    if (error) {
      setFormError(error)
      return
    }

    const currentIndex = TRIP_INFO_STEPS.findIndex((step) => step.id === infoStep)
    const nextStep = TRIP_INFO_STEPS[currentIndex + 1]
    if (nextStep) {
      goToInfoStep(nextStep.id, 'forward')
    }
  }

  function handleInfoBack() {
    const currentIndex = TRIP_INFO_STEPS.findIndex((step) => step.id === infoStep)
    const previousStep = TRIP_INFO_STEPS[currentIndex - 1]
    if (previousStep) {
      goToInfoStep(previousStep.id, 'backward')
      return
    }

    handleBackToDestinationStep()
  }

  function handleInfoEditStep(stepId: Exclude<TripInfoStepId, 'GENERATING'>) {
    goToInfoStep(stepId, 'backward')
  }

  function handleCompanionCountChange(nextCount: number) {
    const normalizedCount = clamp(nextCount, 1, 20)
    setCompanionCount(normalizedCount)
    if (normalizedCount === 1) {
      setCompanionType('SOLO')
      setHasChildren(false)
      setHasSeniors(false)
      return
    }
    if (companionType === 'SOLO') {
      setCompanionType('FRIENDS')
    }
  }

  function handleCompanionTypeChange(nextType: CompanionType) {
    setCompanionType(nextType)
    if (nextType === 'SOLO') {
      setCompanionCount(1)
      setHasChildren(false)
      setHasSeniors(false)
    } else if (companionCount === 1) {
      setCompanionCount(2)
    }
  }

  function toggleBudgetItem(item: BudgetItem) {
    setIncludedBudgetItems((current) => toggleArrayValue(current, item))
  }

  function toggleInterest(interest: InterestId) {
    setInterests((current) => {
      if (current.includes(interest)) {
        return current.filter((item) => item !== interest)
      }
      if (current.length >= 5) {
        return current
      }
      return [...current, interest]
    })
  }

  function toggleSecondaryTransport(mode: TransportMode) {
    if (mode === primaryTransportMode) {
      return
    }
    setSecondaryTransportModes((current) => toggleArrayValue(current, mode))
  }

  function handleAccommodationModeChange(nextMode: AccommodationMode) {
    setAccommodationMode(nextMode)
    setAccommodationSearchError('')
    setFormError('')
    if (nextMode === 'UNDECIDED') {
      setAccommodationQuery('')
      setAccommodationResults([])
      setSelectedAccommodation(null)
      setAccommodationSearchStatus('idle')
      return
    }
    setAccommodationArea('TRANSIT')
  }

  function handleAccommodationQueryChange(value: string) {
    setAccommodationQuery(value)
    setAccommodationSearchError('')
    setFormError('')
    if (value.trim().length < MIN_ACCOMMODATION_QUERY_LENGTH || selectedAccommodation?.displayText === value) {
      setAccommodationResults([])
      setAccommodationSearchStatus('idle')
    }
    if (selectedAccommodation && value !== selectedAccommodation.displayText) {
      setSelectedAccommodation(null)
    }
  }

  function handleAccommodationCompositionStart() {
    accommodationComposingRef.current = true
    setIsAccommodationComposing(true)
  }

  function handleAccommodationCompositionEnd(event: CompositionEvent<HTMLInputElement>) {
    accommodationComposingRef.current = false
    setIsAccommodationComposing(false)
    setAccommodationQuery(event.currentTarget.value)
  }

  function handleAccommodationSelect(candidate: PlaceAutocompleteItem) {
    setSelectedAccommodation(candidate)
    setAccommodationQuery(candidate.displayText || candidate.mainText)
    setAccommodationResults([])
    setAccommodationSearchError('')
    setFormError('')
  }

  function handleScheduleTimeModeChange(nextMode: ScheduleTimeMode) {
    setScheduleTimeMode(nextMode)
    setFormError('')
    if (nextMode === 'DEFAULT') {
      setDailyStartTime(DEFAULT_DAILY_START_TIME)
      setDailyEndTime(DEFAULT_DAILY_END_TIME)
    }
  }

  function toggleAvoidItem(item: AvoidItem) {
    setAvoidItems((current) => toggleArrayValue(current, item))
  }

  async function handleMustVisitSearch() {
    if (mustVisitComposingRef.current) {
      return
    }
    if (!confirmedDestination) {
      setMustVisitSearchError('목적지를 먼저 선택해 주세요.')
      setMustVisitSearchStatus('error')
      return
    }
    if (mustVisitPlaces.length >= 5) {
      setMustVisitSearchError('꼭 가고 싶은 곳은 최대 5개까지 선택할 수 있어요.')
      setMustVisitSearchStatus('error')
      return
    }

    const query = mustVisitQuery.trim()
    if (query.length < MIN_MUST_VISIT_QUERY_LENGTH) {
      setMustVisitSearchError('장소명 또는 주소를 2글자 이상 입력해 주세요.')
      setMustVisitSearchStatus('error')
      return
    }

    const sequence = mustVisitSearchSequenceRef.current + 1
    mustVisitSearchSequenceRef.current = sequence
    setMustVisitSearchStatus('loading')
    setMustVisitSearchError('')
    try {
      const response = await autocompletePlacesInDestination(accessToken, {
        query,
        destinationPlaceId: confirmedDestination.placeId,
        languageCode: 'ko',
      })
      if (mustVisitSearchSequenceRef.current !== sequence) {
        return
      }
      setMustVisitResults(response.items.slice(0, 5))
      setMustVisitSearchStatus('success')
    } catch (error: unknown) {
      if (mustVisitSearchSequenceRef.current !== sequence) {
        return
      }
      setMustVisitResults([])
      setMustVisitSearchError(toSearchUserMessage(error))
      setMustVisitSearchStatus('error')
    }
  }

  function handleMustVisitQueryChange(value: string) {
    setMustVisitQuery(value)
    setMustVisitSearchError('')
    setFormError('')
    if (value.trim().length < MIN_MUST_VISIT_QUERY_LENGTH) {
      setMustVisitResults([])
      setMustVisitSearchStatus('idle')
    }
  }

  function handleMustVisitCompositionStart() {
    mustVisitComposingRef.current = true
    setIsMustVisitComposing(true)
  }

  function handleMustVisitCompositionEnd(event: CompositionEvent<HTMLInputElement>) {
    mustVisitComposingRef.current = false
    setIsMustVisitComposing(false)
    setMustVisitQuery(event.currentTarget.value)
  }

  function handleMustVisitSelect(candidate: PlaceAutocompleteItem) {
    setMustVisitPlaces((current) => {
      if (current.some((place) => place.placeId === candidate.placeId)) {
        return current
      }
      if (current.length >= 5) {
        return current
      }
      return [...current, candidate]
    })
    setMustVisitQuery('')
    setMustVisitResults([])
    setMustVisitSearchError('')
    setMustVisitSearchStatus('idle')
    setFormError('')
  }

  function removeMustVisitPlace(placeId: string) {
    setMustVisitPlaces((current) => current.filter((place) => place.placeId !== placeId))
  }

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()

    if (!confirmedDestination) {
      setFormError('검색 결과에서 여행의 기준이 될 도시나 지역을 선택해 주세요.')
      setCurrentStep('DESTINATION')
      return
    }

    if (!trimmedTitle || !startDate || !endDate) {
      setFormError('여행방 이름과 여행 기간을 입력해 주세요.')
      return
    }

    if (!dateRangeValid) {
      setFormError('돌아오는 날은 출발일과 같거나 이후여야 합니다.')
      return
    }

    const reviewError = getTripInfoStepError('REVIEW', {
      title: trimmedTitle,
      startDate,
      endDate,
      dateRangeValid,
      companionCount,
      childCount: hasChildren ? childCount : 0,
      seniorCount: hasSeniors ? seniorCount : 0,
      budgetAmount: budgetNumericAmount,
      budgetLevel,
      interests,
      accommodationMode,
      selectedAccommodation,
      dailyScheduleRangeValid,
    })

    if (reviewError) {
      setFormError(reviewError)
      setInfoStep('REVIEW')
      return
    }

    const payload: CreateTripRequest = {
      title: trimmedTitle,
      destinationPlaceId: confirmedDestination.placeId,
      startDate,
      endDate,
      companion: {
        count: companionCount,
        type: companionType,
        hasChildren,
        childCount: hasChildren ? childCount : 0,
        childAgeGroup: hasChildren ? childAgeGroup : null,
        hasSeniors,
        seniorCount: hasSeniors ? seniorCount : 0,
      },
      budget: {
        currencyCode,
        amount: budgetNumericAmount > 0 ? budgetNumericAmount : null,
        level: budgetLevel,
        includedItems: includedBudgetItems,
      },
      preferences: {
        travelPace,
        interests,
      },
      transportation: {
        primaryMode: primaryTransportMode,
        secondaryModes: secondaryTransportModes,
      },
      accommodation: {
        mode: accommodationMode,
        preferredArea: accommodationMode === 'UNDECIDED' ? accommodationArea : null,
        placeId: accommodationMode === 'PLACE_SEARCH' ? selectedAccommodation?.placeId ?? null : null,
        checkInTime: accommodationMode === 'PLACE_SEARCH' ? checkInTime || null : null,
        checkOutTime: accommodationMode === 'PLACE_SEARCH' ? checkOutTime || null : null,
      },
      schedulePreference: {
        dailyStartTime: scheduleTimeMode === 'CUSTOM' ? dailyStartTime : null,
        dailyEndTime: scheduleTimeMode === 'CUSTOM' ? dailyEndTime : null,
      },
      additionalRequest: {
        mustVisitPlaceIds: mustVisitPlaces.map((place) => place.placeId),
        avoidConditions: avoidItems,
        freeRequest: freeRequest.trim() || null,
      },
    }

    setSubmitStatus('loading')
    setFormError('')
    setSubmitError('')
    setCreatedTripId('')
    setItineraryGeneration(null)
    setManualPrompt('')
    setAiRequestJson('')
    setManualResponseJson('')
    setManualStatus('idle')
    setManualMessage('')
    setManualValidationReport(null)
    completedNavigationScheduledRef.current = false
    setStepDirection('forward')
    setInfoStep('GENERATING')

    try {
      const created = await createTrip(accessToken, payload)
      setCreatedTripId(created.id)
      if (MANUAL_HANDOFF_ENABLED) {
        const generation = await createItineraryGeneration(accessToken, created.id)
        setItineraryGeneration(generation)
        setManualMessage(generationStatusMessage(generation))
        setSubmitStatus('success')
        return
      }
      setSubmitStatus('success')
      scheduleVisualTimer(() => onCreatedTrip(created.id), 700)
    } catch (error: unknown) {
      setSubmitStatus('error')
      setInfoStep('REVIEW')
      setSubmitError(toUserMessage(error))
    }
  }

  async function handleLoadManualPrompt() {
    if (!createdTripId || !itineraryGeneration) {
      return
    }
    setManualStatus('loading')
    setManualMessage('')
    try {
      const prompt = await getManualPrompt(accessToken, createdTripId, itineraryGeneration.generationId)
      setManualPrompt(prompt)
      setManualStatus('success')
      setManualMessage('프롬프트를 불러왔습니다.')
    } catch (error: unknown) {
      setManualStatus('error')
      setManualMessage(toUserMessage(error))
    }
  }

  async function handleLoadAiRequest() {
    if (!createdTripId || !itineraryGeneration) {
      return
    }
    setManualStatus('loading')
    setManualMessage('')
    try {
      const aiRequest = await getAiRequest(accessToken, createdTripId, itineraryGeneration.generationId)
      setAiRequestJson(JSON.stringify(aiRequest, null, 2))
      setManualStatus('success')
      setManualMessage('AI request JSON을 불러왔습니다.')
    } catch (error: unknown) {
      setManualStatus('error')
      setManualMessage(toUserMessage(error))
    }
  }

  async function handleValidateManualResponse() {
    if (!createdTripId || !itineraryGeneration) {
      return
    }
    let parsed: AiItineraryDraft
    try {
      parsed = JSON.parse(manualResponseJson) as AiItineraryDraft
    } catch {
      setManualStatus('error')
      setManualMessage('ChatGPT response JSON is invalid.')
      setManualValidationReport(null)
      return
    }

    setManualStatus('loading')
    setManualMessage('')
    try {
      const report = await validateManualResponse(accessToken, createdTripId, itineraryGeneration.generationId, parsed)
      setManualValidationReport(report)
      setManualStatus(report.errors.length > 0 ? 'error' : 'success')
      setManualMessage(validationReportMessage(report))
    } catch (error: unknown) {
      setManualStatus('error')
      setManualMessage(toUserMessage(error))
      if (error instanceof ApiError && error.validationReport) {
        setManualValidationReport(error.validationReport)
      }
    }
  }

  function handleManualResponseChange(value: string) {
    setManualResponseJson(value)
    setManualValidationReport(null)
  }

  async function handleSubmitManualResponse() {
    if (!createdTripId || !itineraryGeneration) {
      return
    }
    let parsed: AiItineraryDraft
    try {
      parsed = JSON.parse(manualResponseJson) as AiItineraryDraft
    } catch {
      setManualStatus('error')
      setManualValidationReport(null)
      setManualMessage('ChatGPT 응답 JSON 형식이 올바르지 않습니다.')
      return
    }

    setManualStatus('loading')
    setManualMessage('')
    try {
      const result = await submitManualResponse(accessToken, createdTripId, itineraryGeneration.generationId, parsed)
      await applyGenerationDetail(result)
      setManualValidationReport(null)
      return
      setItineraryGeneration({
        generationId: result.generationId,
        status: result.status,
        candidateCount: result.candidateCount,
      })
      setManualStatus('success')
      setManualMessage('일정이 저장되었습니다. 여행 상세 화면으로 이동합니다.')
      void openCompletedTrip(createdTripId)
    } catch (error: unknown) {
      setManualStatus('error')
      setManualMessage(toUserMessage(error))
      if (error instanceof ApiError && error.validationReport) {
        setManualValidationReport(error.validationReport)
      }
    }
  }

  async function copyText(value: string) {
    if (!value) {
      return
    }
    await navigator.clipboard.writeText(value)
    setManualMessage('클립보드에 복사했습니다.')
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
          infoStep={infoStep}
          stepDirection={stepDirection}
          startDate={startDate}
          submitError={submitError}
          submitStatus={submitStatus}
          title={title}
          endDate={endDate}
          canSubmit={canSubmit}
          tripDuration={tripDuration}
          companionCount={companionCount}
          companionType={companionType}
          hasChildren={hasChildren}
          childCount={childCount}
          childAgeGroup={childAgeGroup}
          hasSeniors={hasSeniors}
          seniorCount={seniorCount}
          currencyCode={currencyCode}
          budgetAmount={budgetAmount}
          budgetNumericAmount={budgetNumericAmount}
          budgetLevel={budgetLevel}
          includedBudgetItems={includedBudgetItems}
          travelPace={travelPace}
          interests={interests}
          primaryTransportMode={primaryTransportMode}
          secondaryTransportModes={secondaryTransportModes}
          accommodationMode={accommodationMode}
          accommodationArea={accommodationArea}
          accommodationQuery={accommodationQuery}
          accommodationResults={accommodationResults}
          selectedAccommodation={selectedAccommodation}
          accommodationSearchStatus={accommodationSearchStatus}
          accommodationSearchError={accommodationSearchError}
          isAccommodationComposing={isAccommodationComposing}
          checkInTime={checkInTime}
          checkOutTime={checkOutTime}
          scheduleTimeMode={scheduleTimeMode}
          dailyStartTime={dailyStartTime}
          dailyEndTime={dailyEndTime}
          appliedDailyStartTime={appliedDailyStartTime}
          appliedDailyEndTime={appliedDailyEndTime}
          mustVisitQuery={mustVisitQuery}
          mustVisitResults={mustVisitResults}
          mustVisitPlaces={mustVisitPlaces}
          mustVisitSearchStatus={mustVisitSearchStatus}
          mustVisitSearchError={mustVisitSearchError}
          isMustVisitComposing={isMustVisitComposing}
          avoidItems={avoidItems}
          freeRequest={freeRequest}
          aiRequestJson={aiRequestJson}
          createdTripId={createdTripId}
          generation={itineraryGeneration}
          manualMessage={manualMessage}
          manualPrompt={manualPrompt}
          manualValidationReport={manualValidationReport}
          manualResponseJson={manualResponseJson}
          manualStatus={manualStatus}
          onBack={handleBackToDestinationStep}
          onAiRequestLoad={handleLoadAiRequest}
          onCopyText={copyText}
          onInfoBack={handleInfoBack}
          onInfoNext={handleInfoNext}
          onInfoEditStep={handleInfoEditStep}
          onManualPromptLoad={handleLoadManualPrompt}
          onManualResponseChange={handleManualResponseChange}
          onManualResponseSubmit={handleSubmitManualResponse}
          onManualResponseValidate={handleValidateManualResponse}
          onOpenCreatedTrip={onCreatedTrip}
          onEndDateChange={(value) => {
            setEndDate(value)
            setFormError(getDateRangeError(startDate, value))
            setSubmitError('')
          }}
          onStartDateChange={(value) => {
            setStartDate(value)
            setFormError(getDateRangeError(value, endDate))
            setSubmitError('')
          }}
          onSubmit={handleSubmit}
          onTitleChange={(value) => {
            setTitle(value)
            setFormError('')
            setSubmitError('')
          }}
          onTitleSuggestionSelect={(value) => {
            setTitle(value)
            setFormError('')
            setSubmitError('')
          }}
          onCompanionCountChange={handleCompanionCountChange}
          onCompanionTypeChange={handleCompanionTypeChange}
          onHasChildrenChange={(value) => {
            setHasChildren(value)
            setFormError('')
          }}
          onChildCountChange={(value) => setChildCount(clamp(value, 1, companionCount))}
          onChildAgeGroupChange={setChildAgeGroup}
          onHasSeniorsChange={(value) => {
            setHasSeniors(value)
            setFormError('')
          }}
          onSeniorCountChange={(value) => setSeniorCount(clamp(value, 1, companionCount))}
          onCurrencyCodeChange={setCurrencyCode}
          onBudgetAmountChange={(value) => {
            setBudgetAmount(formatCurrencyInput(value))
            setFormError('')
          }}
          onBudgetLevelChange={setBudgetLevel}
          onBudgetItemToggle={toggleBudgetItem}
          onTravelPaceChange={setTravelPace}
          onInterestToggle={toggleInterest}
          onPrimaryTransportModeChange={(mode) => {
            setPrimaryTransportMode(mode)
            setSecondaryTransportModes((current) => current.filter((item) => item !== mode))
          }}
          onSecondaryTransportToggle={toggleSecondaryTransport}
          onAccommodationModeChange={handleAccommodationModeChange}
          onAccommodationAreaChange={setAccommodationArea}
          onAccommodationQueryChange={handleAccommodationQueryChange}
          onAccommodationSearch={handleAccommodationSearch}
          onAccommodationSelect={handleAccommodationSelect}
          onAccommodationCompositionStart={handleAccommodationCompositionStart}
          onAccommodationCompositionEnd={handleAccommodationCompositionEnd}
          onCheckInTimeChange={setCheckInTime}
          onCheckOutTimeChange={setCheckOutTime}
          onScheduleTimeModeChange={handleScheduleTimeModeChange}
          onDailyStartTimeChange={setDailyStartTime}
          onDailyEndTimeChange={setDailyEndTime}
          onMustVisitQueryChange={handleMustVisitQueryChange}
          onMustVisitSearch={handleMustVisitSearch}
          onMustVisitSelect={handleMustVisitSelect}
          onMustVisitRemove={removeMustVisitPlace}
          onMustVisitCompositionStart={handleMustVisitCompositionStart}
          onMustVisitCompositionEnd={handleMustVisitCompositionEnd}
          onAvoidItemToggle={toggleAvoidItem}
          onFreeRequestChange={setFreeRequest}
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
    { id: 2, label: '여행 정보' },
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
    ? '이곳이 맞다면 한 번 더 선택해 여행 정보를 정해 주세요.'
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
  infoStep,
  stepDirection,
  startDate,
  submitError,
  submitStatus,
  title,
  tripDuration,
  companionCount,
  companionType,
  hasChildren,
  childCount,
  childAgeGroup,
  hasSeniors,
  seniorCount,
  currencyCode,
  budgetAmount,
  budgetNumericAmount,
  budgetLevel,
  includedBudgetItems,
  travelPace,
  interests,
  primaryTransportMode,
  secondaryTransportModes,
  accommodationMode,
  accommodationArea,
  accommodationQuery,
  accommodationResults,
  selectedAccommodation,
  accommodationSearchStatus,
  accommodationSearchError,
  isAccommodationComposing,
  checkInTime,
  checkOutTime,
  scheduleTimeMode,
  dailyStartTime,
  dailyEndTime,
  appliedDailyStartTime,
  appliedDailyEndTime,
  mustVisitQuery,
  mustVisitResults,
  mustVisitPlaces,
  mustVisitSearchStatus,
  mustVisitSearchError,
  isMustVisitComposing,
  avoidItems,
  freeRequest,
  aiRequestJson,
  createdTripId,
  generation,
  manualMessage,
  manualPrompt,
  manualValidationReport,
  manualResponseJson,
  manualStatus,
  onBack,
  onAiRequestLoad,
  onCopyText,
  onInfoBack,
  onInfoNext,
  onInfoEditStep,
  onManualPromptLoad,
  onManualResponseChange,
  onManualResponseSubmit,
  onManualResponseValidate,
  onOpenCreatedTrip,
  onEndDateChange,
  onStartDateChange,
  onSubmit,
  onTitleChange,
  onTitleSuggestionSelect,
  onCompanionCountChange,
  onCompanionTypeChange,
  onHasChildrenChange,
  onChildCountChange,
  onChildAgeGroupChange,
  onHasSeniorsChange,
  onSeniorCountChange,
  onCurrencyCodeChange,
  onBudgetAmountChange,
  onBudgetLevelChange,
  onBudgetItemToggle,
  onTravelPaceChange,
  onInterestToggle,
  onPrimaryTransportModeChange,
  onSecondaryTransportToggle,
  onAccommodationModeChange,
  onAccommodationAreaChange,
  onAccommodationQueryChange,
  onAccommodationSearch,
  onAccommodationSelect,
  onAccommodationCompositionStart,
  onAccommodationCompositionEnd,
  onCheckInTimeChange,
  onCheckOutTimeChange,
  onScheduleTimeModeChange,
  onDailyStartTimeChange,
  onDailyEndTimeChange,
  onMustVisitQueryChange,
  onMustVisitSearch,
  onMustVisitSelect,
  onMustVisitRemove,
  onMustVisitCompositionStart,
  onMustVisitCompositionEnd,
  onAvoidItemToggle,
  onFreeRequestChange,
}: {
  canSubmit: boolean
  dateRangeValid: boolean
  destination: PlaceAutocompleteItem | null
  endDate: string
  formError: string
  infoStep: TripInfoStepId
  stepDirection: StepDirection
  startDate: string
  submitError: string
  submitStatus: AsyncStatus
  title: string
  tripDuration: TripDuration | null
  companionCount: number
  companionType: CompanionType
  hasChildren: boolean
  childCount: number
  childAgeGroup: ChildAgeGroup
  hasSeniors: boolean
  seniorCount: number
  currencyCode: CurrencyCode
  budgetAmount: string
  budgetNumericAmount: number
  budgetLevel: BudgetLevel
  includedBudgetItems: BudgetItem[]
  travelPace: TravelPace
  interests: InterestId[]
  primaryTransportMode: TransportMode
  secondaryTransportModes: TransportMode[]
  accommodationMode: AccommodationMode
  accommodationArea: AccommodationArea
  accommodationQuery: string
  accommodationResults: PlaceAutocompleteItem[]
  selectedAccommodation: PlaceAutocompleteItem | null
  accommodationSearchStatus: AsyncStatus
  accommodationSearchError: string
  isAccommodationComposing: boolean
  checkInTime: string
  checkOutTime: string
  scheduleTimeMode: ScheduleTimeMode
  dailyStartTime: string
  dailyEndTime: string
  appliedDailyStartTime: string
  appliedDailyEndTime: string
  mustVisitQuery: string
  mustVisitResults: PlaceAutocompleteItem[]
  mustVisitPlaces: PlaceAutocompleteItem[]
  mustVisitSearchStatus: AsyncStatus
  mustVisitSearchError: string
  isMustVisitComposing: boolean
  avoidItems: AvoidItem[]
  freeRequest: string
  aiRequestJson: string
  createdTripId: string
  generation: TrackedItineraryGeneration | null
  manualMessage: string
  manualPrompt: string
  manualValidationReport: AiItineraryValidationReport | null
  manualResponseJson: string
  manualStatus: AsyncStatus
  onBack: () => void
  onAiRequestLoad: () => void
  onCopyText: (value: string) => void
  onInfoBack: () => void
  onInfoNext: () => void
  onInfoEditStep: (stepId: Exclude<TripInfoStepId, 'GENERATING'>) => void
  onManualPromptLoad: () => void
  onManualResponseChange: (value: string) => void
  onManualResponseSubmit: () => void
  onManualResponseValidate: () => void
  onOpenCreatedTrip: (tripId: string) => void
  onEndDateChange: (value: string) => void
  onStartDateChange: (value: string) => void
  onSubmit: (event: FormEvent<HTMLFormElement>) => void
  onTitleChange: (value: string) => void
  onTitleSuggestionSelect: (value: string) => void
  onCompanionCountChange: (value: number) => void
  onCompanionTypeChange: (value: CompanionType) => void
  onHasChildrenChange: (value: boolean) => void
  onChildCountChange: (value: number) => void
  onChildAgeGroupChange: (value: ChildAgeGroup) => void
  onHasSeniorsChange: (value: boolean) => void
  onSeniorCountChange: (value: number) => void
  onCurrencyCodeChange: (value: CurrencyCode) => void
  onBudgetAmountChange: (value: string) => void
  onBudgetLevelChange: (value: BudgetLevel) => void
  onBudgetItemToggle: (value: BudgetItem) => void
  onTravelPaceChange: (value: TravelPace) => void
  onInterestToggle: (value: InterestId) => void
  onPrimaryTransportModeChange: (value: TransportMode) => void
  onSecondaryTransportToggle: (value: TransportMode) => void
  onAccommodationModeChange: (value: AccommodationMode) => void
  onAccommodationAreaChange: (value: AccommodationArea) => void
  onAccommodationQueryChange: (value: string) => void
  onAccommodationSearch: () => void
  onAccommodationSelect: (value: PlaceAutocompleteItem) => void
  onAccommodationCompositionStart: () => void
  onAccommodationCompositionEnd: (event: CompositionEvent<HTMLInputElement>) => void
  onCheckInTimeChange: (value: string) => void
  onCheckOutTimeChange: (value: string) => void
  onScheduleTimeModeChange: (value: ScheduleTimeMode) => void
  onDailyStartTimeChange: (value: string) => void
  onDailyEndTimeChange: (value: string) => void
  onMustVisitQueryChange: (value: string) => void
  onMustVisitSearch: () => void
  onMustVisitSelect: (value: PlaceAutocompleteItem) => void
  onMustVisitRemove: (value: string) => void
  onMustVisitCompositionStart: () => void
  onMustVisitCompositionEnd: (event: CompositionEvent<HTMLInputElement>) => void
  onAvoidItemToggle: (value: AvoidItem) => void
  onFreeRequestChange: (value: string) => void
}) {
  const stepIndex = TRIP_INFO_STEPS.findIndex((step) => step.id === infoStep)
  const currentStep = TRIP_INFO_STEPS[stepIndex] ?? TRIP_INFO_STEPS[0]
  const nextStep = TRIP_INFO_STEPS[stepIndex + 1]
  const nextButtonLabel = getTripInfoNextLabel(infoStep)
  const showSubmitButton = infoStep === 'REVIEW'

  const summary = {
    destination,
    title,
    startDate,
    endDate,
    tripDuration,
    companionCount,
    companionType,
    hasChildren,
    childCount,
    hasSeniors,
    seniorCount,
    currencyCode,
    budgetNumericAmount,
    budgetLevel,
    includedBudgetItems,
    travelPace,
    interests,
    primaryTransportMode,
    secondaryTransportModes,
    accommodationMode,
    accommodationArea,
    selectedAccommodation,
    appliedDailyStartTime,
    appliedDailyEndTime,
    mustVisitPlaces,
    avoidItems,
    freeRequest,
  }

  if (infoStep === 'GENERATING') {
    return (
      <section className="trip-info-page" aria-label="일정 생성 진행">
        <GeneratingTripPanel
          aiRequestJson={aiRequestJson}
          companionType={companionType}
          createdTripId={createdTripId}
          destination={destination}
          generation={generation}
          manualMessage={manualMessage}
          manualPrompt={manualPrompt}
          manualValidationReport={manualValidationReport}
          manualResponseJson={manualResponseJson}
          manualStatus={manualStatus}
          onAiRequestLoad={onAiRequestLoad}
          onCopyText={onCopyText}
          onOpenCreatedTrip={onOpenCreatedTrip}
          onManualPromptLoad={onManualPromptLoad}
          onManualResponseChange={onManualResponseChange}
          onManualResponseSubmit={onManualResponseSubmit}
          onManualResponseValidate={onManualResponseValidate}
          submitError={submitError}
          submitStatus={submitStatus}
          title={title}
        />
      </section>
    )
  }

  return (
    <form
      className="trip-info-page"
      onSubmit={(event) => {
        if (infoStep === 'REVIEW') {
          onSubmit(event)
          return
        }
        event.preventDefault()
        onInfoNext()
      }}
    >
      <TripInfoProgress currentStep={infoStep} nextStep={nextStep?.label} />

      <section className="trip-info-shell">
        <TripInfoVisual
          accommodationMode={accommodationMode}
          avoidItems={avoidItems}
          budgetLevel={budgetLevel}
          companionCount={companionCount}
          companionType={companionType}
          destination={destination}
          infoStep={infoStep}
          interests={interests}
          mustVisitPlaces={mustVisitPlaces}
          title={title}
          travelPace={travelPace}
          tripDuration={tripDuration}
        />

        <section className={`trip-info-panel ${stepDirection}`} key={infoStep} aria-label={currentStep.label}>
          <div className="trip-info-panel-heading">
            <span>여행 정보 {stepIndex + 1} / {TRIP_INFO_STEPS.length}</span>
            <h1>{getTripInfoQuestion(infoStep)}</h1>
            <p>{getTripInfoDescription(infoStep)}</p>
          </div>

          {infoStep === 'BASIC' && (
            <BasicInfoPanel
              dateRangeValid={dateRangeValid}
              destination={destination}
              endDate={endDate}
              startDate={startDate}
              title={title}
              tripDuration={tripDuration}
              onEndDateChange={onEndDateChange}
              onStartDateChange={onStartDateChange}
              onTitleChange={onTitleChange}
              onTitleSuggestionSelect={onTitleSuggestionSelect}
            />
          )}

          {infoStep === 'COMPANION' && (
            <CompanionInfoPanel
              childAgeGroup={childAgeGroup}
              childCount={childCount}
              companionCount={companionCount}
              companionType={companionType}
              hasChildren={hasChildren}
              hasSeniors={hasSeniors}
              seniorCount={seniorCount}
              onChildAgeGroupChange={onChildAgeGroupChange}
              onChildCountChange={onChildCountChange}
              onCompanionCountChange={onCompanionCountChange}
              onCompanionTypeChange={onCompanionTypeChange}
              onHasChildrenChange={onHasChildrenChange}
              onHasSeniorsChange={onHasSeniorsChange}
              onSeniorCountChange={onSeniorCountChange}
            />
          )}

          {infoStep === 'BUDGET' && (
            <BudgetInfoPanel
              budgetAmount={budgetAmount}
              budgetLevel={budgetLevel}
              budgetNumericAmount={budgetNumericAmount}
              companionCount={companionCount}
              currencyCode={currencyCode}
              includedBudgetItems={includedBudgetItems}
              tripDuration={tripDuration}
              onBudgetAmountChange={onBudgetAmountChange}
              onBudgetItemToggle={onBudgetItemToggle}
              onBudgetLevelChange={onBudgetLevelChange}
              onCurrencyCodeChange={onCurrencyCodeChange}
            />
          )}

          {infoStep === 'PREFERENCE' && (
            <PreferenceInfoPanel
              interests={interests}
              primaryTransportMode={primaryTransportMode}
              secondaryTransportModes={secondaryTransportModes}
              travelPace={travelPace}
              onInterestToggle={onInterestToggle}
              onPrimaryTransportModeChange={onPrimaryTransportModeChange}
              onSecondaryTransportToggle={onSecondaryTransportToggle}
              onTravelPaceChange={onTravelPaceChange}
            />
          )}

          {infoStep === 'ACCOMMODATION' && (
            <AccommodationInfoPanel
              accommodationArea={accommodationArea}
              accommodationMode={accommodationMode}
              accommodationQuery={accommodationQuery}
              accommodationResults={accommodationResults}
              selectedAccommodation={selectedAccommodation}
              accommodationSearchStatus={accommodationSearchStatus}
              accommodationSearchError={accommodationSearchError}
              isAccommodationComposing={isAccommodationComposing}
              checkInTime={checkInTime}
              checkOutTime={checkOutTime}
              scheduleTimeMode={scheduleTimeMode}
              dailyStartTime={dailyStartTime}
              dailyEndTime={dailyEndTime}
              onAccommodationAreaChange={onAccommodationAreaChange}
              onAccommodationModeChange={onAccommodationModeChange}
              onAccommodationQueryChange={onAccommodationQueryChange}
              onAccommodationSearch={onAccommodationSearch}
              onAccommodationSelect={onAccommodationSelect}
              onAccommodationCompositionStart={onAccommodationCompositionStart}
              onAccommodationCompositionEnd={onAccommodationCompositionEnd}
              onCheckInTimeChange={onCheckInTimeChange}
              onCheckOutTimeChange={onCheckOutTimeChange}
              onScheduleTimeModeChange={onScheduleTimeModeChange}
              onDailyStartTimeChange={onDailyStartTimeChange}
              onDailyEndTimeChange={onDailyEndTimeChange}
            />
          )}

          {infoStep === 'REQUESTS' && (
            <RequestsInfoPanel
              avoidItems={avoidItems}
              freeRequest={freeRequest}
              mustVisitQuery={mustVisitQuery}
              mustVisitResults={mustVisitResults}
              mustVisitPlaces={mustVisitPlaces}
              mustVisitSearchStatus={mustVisitSearchStatus}
              mustVisitSearchError={mustVisitSearchError}
              isMustVisitComposing={isMustVisitComposing}
              onAvoidItemToggle={onAvoidItemToggle}
              onFreeRequestChange={onFreeRequestChange}
              onMustVisitQueryChange={onMustVisitQueryChange}
              onMustVisitSearch={onMustVisitSearch}
              onMustVisitSelect={onMustVisitSelect}
              onMustVisitRemove={onMustVisitRemove}
              onMustVisitCompositionStart={onMustVisitCompositionStart}
              onMustVisitCompositionEnd={onMustVisitCompositionEnd}
            />
          )}

          {infoStep === 'REVIEW' && (
            <ReviewInfoPanel
              summary={summary}
              onEditStep={onInfoEditStep}
            />
          )}

          {formError && <p className="trip-create-field-error">{formError}</p>}
          {submitError && <p className="trip-create-submit-error" role="alert">{submitError}</p>}

          <TripInfoSummary summary={summary} />

          <div className="trip-create-sticky-actions">
            <button className="trip-create-secondary-action" type="button" onClick={onInfoBack}>
              이전
            </button>
            {showSubmitButton ? (
              <button className="trip-create-primary-action" type="submit" disabled={!canSubmit}>
                {submitStatus === 'loading' ? '일정 준비 중' : 'AI 여행 일정 만들기'}
              </button>
            ) : (
              <button className="trip-create-primary-action" type="button" onClick={onInfoNext}>
                {nextButtonLabel}
              </button>
            )}
          </div>

          {infoStep === 'REQUESTS' && (
            <button className="trip-info-skip-button" type="button" onClick={onInfoNext}>
              특별한 요청 없이 넘어가기
            </button>
          )}
        </section>
      </section>

      <button className="trip-info-destination-edit" type="button" onClick={onBack}>
        목적지 다시 선택
      </button>
    </form>
  )
}

type TripInfoSummaryData = {
  destination: PlaceAutocompleteItem | null
  title: string
  startDate: string
  endDate: string
  tripDuration: TripDuration | null
  companionCount: number
  companionType: CompanionType
  hasChildren: boolean
  childCount: number
  hasSeniors: boolean
  seniorCount: number
  currencyCode: CurrencyCode
  budgetNumericAmount: number
  budgetLevel: BudgetLevel
  includedBudgetItems: BudgetItem[]
  travelPace: TravelPace
  interests: InterestId[]
  primaryTransportMode: TransportMode
  secondaryTransportModes: TransportMode[]
  accommodationMode: AccommodationMode
  accommodationArea: AccommodationArea
  selectedAccommodation: PlaceAutocompleteItem | null
  appliedDailyStartTime: string
  appliedDailyEndTime: string
  mustVisitPlaces: PlaceAutocompleteItem[]
  avoidItems: AvoidItem[]
  freeRequest: string
}

function TripInfoProgress({
  currentStep,
  nextStep,
}: {
  currentStep: TripInfoStepId
  nextStep?: string
}) {
  const currentIndex = TRIP_INFO_STEPS.findIndex((step) => step.id === currentStep)

  return (
    <section className="trip-info-progress-card" aria-label="여행 정보 입력 진행률">
      <div>
        <span>여행 정보 {currentIndex + 1} / {TRIP_INFO_STEPS.length}</span>
        <strong>{TRIP_INFO_STEPS[currentIndex]?.label ?? '여행 정보'}</strong>
      </div>
      <ol className="trip-info-dot-progress">
        {TRIP_INFO_STEPS.map((step, index) => (
          <li
            className={`${index < currentIndex ? 'done' : ''} ${index === currentIndex ? 'active' : ''}`}
            key={step.id}
          >
            <span aria-hidden="true" />
            <small>{step.shortLabel}</small>
          </li>
        ))}
      </ol>
      <p>{nextStep ? `다음: ${nextStep}` : '입력한 내용을 확인해요.'}</p>
    </section>
  )
}

function TripInfoVisual({
  accommodationMode,
  avoidItems,
  budgetLevel,
  companionCount,
  companionType,
  destination,
  infoStep,
  interests,
  mustVisitPlaces,
  title,
  travelPace,
  tripDuration,
}: {
  accommodationMode: AccommodationMode
  avoidItems: AvoidItem[]
  budgetLevel: BudgetLevel
  companionCount: number
  companionType: CompanionType
  destination: PlaceAutocompleteItem | null
  infoStep: TripInfoStepId
  interests: InterestId[]
  mustVisitPlaces: PlaceAutocompleteItem[]
  title: string
  travelPace: TravelPace
  tripDuration: TripDuration | null
}) {
  const destinationName = destination?.mainText ?? '여행지'
  const mascotImage = MASCOT_IMAGE_BY_COMPANION[companionType]
  const mascotAlt = `${companionTypeLabel(companionType)} 여행 마스코트`

  return (
    <aside className={`trip-info-visual step-${infoStep.toLowerCase()}`} aria-label="단계별 여행 정보 미리보기">
      <span className="trip-info-visual-badge">PlanMate Preview</span>
      {infoStep === 'BASIC' && (
        <>
          <div className="info-map-illustration">
            <img className="step-mascot-image" src={mascotImage} alt="" aria-hidden="true" />
            <span className="info-map-pin">{destinationName}</span>
            <span className="info-map-route" />
            <span className="info-calendar-tile">{tripDuration ? tripDurationLabel(tripDuration) : '날짜 선택'}</span>
          </div>
          <div className="info-date-strip">
            {(tripDuration?.dateLabels ?? ['출발일', '여행일', '돌아오는 날']).slice(0, 5).map((label) => (
              <span key={label}>{label}</span>
            ))}
          </div>
        </>
      )}

      {infoStep === 'COMPANION' && (
        <>
          <div className="companion-mascot-stage">
            <img className="companion-mascot-image" src={mascotImage} alt={mascotAlt} />
            <span className="companion-count-badge">
              총 {companionCount}명
            </span>
          </div>
          <p>{companionTypeLabel(companionType)} 여행 · 총 {companionCount}명</p>
        </>
      )}

      {infoStep === 'BUDGET' && (
        <>
          <div className={`budget-journey-visual level-${budgetLevel.toLowerCase()}`}>
            <img className="step-mascot-image" src={mascotImage} alt="" aria-hidden="true" />
            <span className="budget-suitcase" />
            <span className="budget-coin coin-one" />
            <span className="budget-coin coin-two" />
            <span className="budget-coin coin-three" />
            <span className="budget-ticket">예산</span>
          </div>
          <div className="budget-icons">
            <span>숙박</span>
            <span>교통</span>
            <span>식비</span>
          </div>
          <p>{budgetLevelLabel(budgetLevel)} 예산으로 균형을 잡아요.</p>
        </>
      )}

      {infoStep === 'PREFERENCE' && (
        <>
          <div className={`pace-route pace-${travelPace.toLowerCase()}`}>
            <img className="step-mascot-image" src={mascotImage} alt="" aria-hidden="true" />
            <span>숙소</span>
            <i />
            <span>장소</span>
            <i />
            <span>식사</span>
            {travelPace === 'PACKED' && <><i /><span>야경</span></>}
          </div>
          <div className="interest-pin-row">
            {interests.slice(0, 5).map((interest) => (
              <span key={interest}>{interestLabel(interest)}</span>
            ))}
          </div>
        </>
      )}

      {infoStep === 'ACCOMMODATION' && (
        <>
          <div className="stay-illustration">
            <img className="step-mascot-image" src={mascotImage} alt="" aria-hidden="true" />
            <span className="stay-building" />
            <span className="stay-pin" />
            <span className="stay-route" />
          </div>
          <p>{accommodationModeLabel(accommodationMode)} 기준으로 동선을 준비해요.</p>
        </>
      )}

      {infoStep === 'REQUESTS' && (
        <>
          <div className="request-illustration">
            <img className="step-mascot-image" src={mascotImage} alt="" aria-hidden="true" />
            <span className="star-pin">{mustVisitPlaces.length}</span>
            <span className="avoid-pin">{avoidItems.length}</span>
            <span className="speech-bubble" />
          </div>
          <p>꼭 갈 곳과 피하고 싶은 일정을 마지막으로 정리해요.</p>
        </>
      )}

      {infoStep === 'REVIEW' && (
        <>
          <div className="review-constellation">
            <img className="step-mascot-image" src={mascotImage} alt="" aria-hidden="true" />
            <span>{destinationName}</span>
            <span>{tripDuration ? `${tripDuration.days}일` : '기간'}</span>
            <span>{companionCount}명</span>
            <span>{interests.length}개 취향</span>
          </div>
          <p>{title || `${destinationName} 여행`} 조건을 한 번 더 확인해요.</p>
        </>
      )}
    </aside>
  )
}

function BasicInfoPanel({
  dateRangeValid,
  destination,
  endDate,
  startDate,
  title,
  tripDuration,
  onEndDateChange,
  onStartDateChange,
  onTitleChange,
  onTitleSuggestionSelect,
}: {
  dateRangeValid: boolean
  destination: PlaceAutocompleteItem | null
  endDate: string
  startDate: string
  title: string
  tripDuration: TripDuration | null
  onEndDateChange: (value: string) => void
  onStartDateChange: (value: string) => void
  onTitleChange: (value: string) => void
  onTitleSuggestionSelect: (value: string) => void
}) {
  const suggestions = getTitleSuggestions(destination?.mainText ?? '여행', tripDuration)

  return (
    <div className="trip-info-fields">
      <label className="trip-info-field">
        <span>여행방 이름</span>
        <input
          name="title"
          type="text"
          placeholder="예: 후쿠오카 먹고 쉬는 여행"
          maxLength={60}
          value={title}
          onChange={(event) => onTitleChange(event.target.value)}
          required
        />
      </label>

      <div className="title-suggestion-row" aria-label="여행방 이름 추천">
        {suggestions.map((suggestion) => (
          <button type="button" key={suggestion} onClick={() => onTitleSuggestionSelect(suggestion)}>
            {suggestion}
          </button>
        ))}
      </div>

      <div className="trip-date-range-card">
        <label>
          <span>출발일</span>
          <input
            name="startDate"
            type="date"
            value={startDate}
            onChange={(event) => onStartDateChange(event.target.value)}
            required
          />
        </label>
        <span className="date-range-connector" aria-hidden="true" />
        <label>
          <span>돌아오는 날</span>
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

      <div className="duration-result-card">
        <strong>{tripDuration ? tripDurationSentence(tripDuration) : '날짜를 선택하면 여행 기간을 계산해요.'}</strong>
        <div>
          {(tripDuration?.dateLabels ?? ['출발일', '여행일', '돌아오는 날']).slice(0, 7).map((label) => (
            <span key={label}>{label}</span>
          ))}
        </div>
      </div>
    </div>
  )
}

function CompanionInfoPanel({
  childAgeGroup,
  childCount,
  companionCount,
  companionType,
  hasChildren,
  hasSeniors,
  seniorCount,
  onChildAgeGroupChange,
  onChildCountChange,
  onCompanionCountChange,
  onCompanionTypeChange,
  onHasChildrenChange,
  onHasSeniorsChange,
  onSeniorCountChange,
}: {
  childAgeGroup: ChildAgeGroup
  childCount: number
  companionCount: number
  companionType: CompanionType
  hasChildren: boolean
  hasSeniors: boolean
  seniorCount: number
  onChildAgeGroupChange: (value: ChildAgeGroup) => void
  onChildCountChange: (value: number) => void
  onCompanionCountChange: (value: number) => void
  onCompanionTypeChange: (value: CompanionType) => void
  onHasChildrenChange: (value: boolean) => void
  onHasSeniorsChange: (value: boolean) => void
  onSeniorCountChange: (value: number) => void
}) {
  return (
    <div className="trip-info-fields">
      <CounterControl
        label="여행 인원"
        max={20}
        min={1}
        value={companionCount}
        onChange={onCompanionCountChange}
      />

      <OptionGrid label="동행자 유형">
        {COMPANION_OPTIONS.map((option) => (
          <OptionCard
            description={option.description}
            imageSrc={MASCOT_IMAGE_BY_COMPANION[option.id]}
            isSelected={companionType === option.id}
            key={option.id}
            label={option.label}
            onClick={() => onCompanionTypeChange(option.id)}
          />
        ))}
      </OptionGrid>

      <div className="care-grid">
        <CareOptionCard
          count={childCount}
          isActive={hasChildren}
          label="어린이와 함께해요"
          onCountChange={onChildCountChange}
          onToggle={onHasChildrenChange}
        >
          <div className="chip-grid compact">
            {CHILD_AGE_OPTIONS.map((option) => (
              <ToggleChip
                isSelected={childAgeGroup === option.id}
                key={option.id}
                label={option.label}
                onClick={() => onChildAgeGroupChange(option.id)}
              />
            ))}
          </div>
        </CareOptionCard>

        <CareOptionCard
          count={seniorCount}
          isActive={hasSeniors}
          label="시니어와 함께해요"
          onCountChange={onSeniorCountChange}
          onToggle={onHasSeniorsChange}
        />
      </div>
    </div>
  )
}

function BudgetInfoPanel({
  budgetAmount,
  budgetLevel,
  budgetNumericAmount,
  companionCount,
  currencyCode,
  includedBudgetItems,
  tripDuration,
  onBudgetAmountChange,
  onBudgetItemToggle,
  onBudgetLevelChange,
  onCurrencyCodeChange,
}: {
  budgetAmount: string
  budgetLevel: BudgetLevel
  budgetNumericAmount: number
  companionCount: number
  currencyCode: CurrencyCode
  includedBudgetItems: BudgetItem[]
  tripDuration: TripDuration | null
  onBudgetAmountChange: (value: string) => void
  onBudgetItemToggle: (value: BudgetItem) => void
  onBudgetLevelChange: (value: BudgetLevel) => void
  onCurrencyCodeChange: (value: CurrencyCode) => void
}) {
  const perPerson = budgetNumericAmount > 0 ? Math.round(budgetNumericAmount / companionCount) : 0
  const perDay = budgetNumericAmount > 0 && tripDuration ? Math.round(budgetNumericAmount / tripDuration.days) : 0

  return (
    <div className="trip-info-fields">
      <div className="budget-input-row">
        <label className="trip-info-field currency-field">
          <span>통화</span>
          <select value={currencyCode} onChange={(event) => onCurrencyCodeChange(event.target.value as CurrencyCode)}>
            {CURRENCY_OPTIONS.map((option) => (
              <option key={option.id} value={option.id}>{option.label}</option>
            ))}
          </select>
        </label>
        <label className="trip-info-field">
          <span>총예산</span>
          <input
            inputMode="numeric"
            placeholder="1,100,000"
            value={budgetAmount}
            onChange={(event) => onBudgetAmountChange(event.target.value)}
          />
        </label>
      </div>

      <OptionGrid label="아직 정확한 예산이 없다면 수준을 골라주세요.">
        {BUDGET_LEVEL_OPTIONS.map((option) => (
          <OptionCard
            description={option.description}
            isSelected={budgetLevel === option.id}
            key={option.id}
            label={option.label}
            onClick={() => onBudgetLevelChange(option.id)}
          />
        ))}
      </OptionGrid>

      <OptionGrid label="이 예산에 포함된 항목">
        {BUDGET_ITEMS.map((item) => (
          <ToggleCard
            isSelected={includedBudgetItems.includes(item.id)}
            key={item.id}
            label={item.label}
            onClick={() => onBudgetItemToggle(item.id)}
          />
        ))}
      </OptionGrid>

      <div className="budget-calculation-card">
        <span>{companionCount}명 · {tripDuration ? tripDurationLabel(tripDuration) : '기간 선택 전'} 기준</span>
        <dl>
          <div>
            <dt>1인당 예산</dt>
            <dd>{perPerson > 0 ? formatCurrencyAmount(perPerson, currencyCode) : '입력 전'}</dd>
          </div>
          <div>
            <dt>하루 평균 예산</dt>
            <dd>{perDay > 0 ? formatCurrencyAmount(perDay, currencyCode) : '입력 전'}</dd>
          </div>
        </dl>
      </div>
    </div>
  )
}

function PreferenceInfoPanel({
  interests,
  primaryTransportMode,
  secondaryTransportModes,
  travelPace,
  onInterestToggle,
  onPrimaryTransportModeChange,
  onSecondaryTransportToggle,
  onTravelPaceChange,
}: {
  interests: InterestId[]
  primaryTransportMode: TransportMode
  secondaryTransportModes: TransportMode[]
  travelPace: TravelPace
  onInterestToggle: (value: InterestId) => void
  onPrimaryTransportModeChange: (value: TransportMode) => void
  onSecondaryTransportToggle: (value: TransportMode) => void
  onTravelPaceChange: (value: TravelPace) => void
}) {
  return (
    <div className="trip-info-fields">
      <OptionGrid label="여행 속도">
        {PACE_OPTIONS.map((option) => (
          <OptionCard
            description={option.description}
            isSelected={travelPace === option.id}
            key={option.id}
            label={option.label}
            onClick={() => onTravelPaceChange(option.id)}
          />
        ))}
      </OptionGrid>

      <div className="input-cluster">
        <div className="cluster-heading">
          <strong>관심사</strong>
          <span>{interests.length} / 5 선택</span>
        </div>
        <div className="chip-grid">
          {INTEREST_OPTIONS.map((option) => (
            <ToggleChip
              isSelected={interests.includes(option.id)}
              key={option.id}
              label={option.label}
              onClick={() => onInterestToggle(option.id)}
            />
          ))}
        </div>
      </div>

      <OptionGrid label="주 이동수단">
        {TRANSPORT_OPTIONS.map((option) => (
          <ToggleCard
            isSelected={primaryTransportMode === option.id}
            key={option.id}
            label={option.label}
            onClick={() => onPrimaryTransportModeChange(option.id)}
          />
        ))}
      </OptionGrid>

      <div className="input-cluster">
        <div className="cluster-heading">
          <strong>필요할 때 함께 사용할 수단</strong>
          <span>{secondaryTransportModes.length}개 선택</span>
        </div>
        <div className="chip-grid compact">
          {TRANSPORT_OPTIONS.filter((option) => option.id !== primaryTransportMode).map((option) => (
            <ToggleChip
              isSelected={secondaryTransportModes.includes(option.id)}
              key={option.id}
              label={option.label}
              onClick={() => onSecondaryTransportToggle(option.id)}
            />
          ))}
        </div>
      </div>
    </div>
  )
}

function AccommodationInfoPanel({
  accommodationArea,
  accommodationMode,
  accommodationQuery,
  accommodationResults,
  selectedAccommodation,
  accommodationSearchStatus,
  accommodationSearchError,
  isAccommodationComposing,
  checkInTime,
  checkOutTime,
  scheduleTimeMode,
  dailyStartTime,
  dailyEndTime,
  onAccommodationAreaChange,
  onAccommodationModeChange,
  onAccommodationQueryChange,
  onAccommodationSearch,
  onAccommodationSelect,
  onAccommodationCompositionStart,
  onAccommodationCompositionEnd,
  onCheckInTimeChange,
  onCheckOutTimeChange,
  onScheduleTimeModeChange,
  onDailyStartTimeChange,
  onDailyEndTimeChange,
}: {
  accommodationArea: AccommodationArea
  accommodationMode: AccommodationMode
  accommodationQuery: string
  accommodationResults: PlaceAutocompleteItem[]
  selectedAccommodation: PlaceAutocompleteItem | null
  accommodationSearchStatus: AsyncStatus
  accommodationSearchError: string
  isAccommodationComposing: boolean
  checkInTime: string
  checkOutTime: string
  scheduleTimeMode: ScheduleTimeMode
  dailyStartTime: string
  dailyEndTime: string
  onAccommodationAreaChange: (value: AccommodationArea) => void
  onAccommodationModeChange: (value: AccommodationMode) => void
  onAccommodationQueryChange: (value: string) => void
  onAccommodationSearch: () => void
  onAccommodationSelect: (value: PlaceAutocompleteItem) => void
  onAccommodationCompositionStart: () => void
  onAccommodationCompositionEnd: (event: CompositionEvent<HTMLInputElement>) => void
  onCheckInTimeChange: (value: string) => void
  onCheckOutTimeChange: (value: string) => void
  onScheduleTimeModeChange: (value: ScheduleTimeMode) => void
  onDailyStartTimeChange: (value: string) => void
  onDailyEndTimeChange: (value: string) => void
}) {
  const canShowAccommodationResults =
    accommodationMode === 'PLACE_SEARCH' && accommodationResults.length > 0 && !selectedAccommodation
  const accommodationQueryLength = accommodationQuery.trim().length
  const canSearchAccommodation = (
    accommodationMode === 'PLACE_SEARCH'
    && accommodationSearchStatus !== 'loading'
    && !isAccommodationComposing
    && accommodationQueryLength >= MIN_ACCOMMODATION_QUERY_LENGTH
  )
  const showAccommodationNoResults = (
    accommodationMode === 'PLACE_SEARCH'
    && accommodationSearchStatus === 'success'
    && accommodationResults.length === 0
    && accommodationQueryLength >= MIN_ACCOMMODATION_QUERY_LENGTH
    && !selectedAccommodation
  )
  const accommodationGuide = accommodationSearchError
    ? accommodationSearchError
    : accommodationSearchGuide({
        isComposing: isAccommodationComposing,
        queryLength: accommodationQueryLength,
        searchStatus: accommodationSearchStatus,
        selectedAccommodation,
      })

  return (
    <div className="trip-info-fields">
      <OptionGrid label="숙소 상태">
        {ACCOMMODATION_MODE_OPTIONS.map((option) => (
          <OptionCard
            description={option.description}
            isSelected={accommodationMode === option.id}
            key={option.id}
            label={option.label}
            onClick={() => onAccommodationModeChange(option.id)}
          />
        ))}
      </OptionGrid>

      {accommodationMode === 'UNDECIDED' && (
        <OptionGrid label="머물고 싶은 지역이 있나요?">
          {ACCOMMODATION_AREA_OPTIONS.map((option) => (
            <ToggleCard
              isSelected={accommodationArea === option.id}
              key={option.id}
              label={option.label}
              onClick={() => onAccommodationAreaChange(option.id)}
            />
          ))}
        </OptionGrid>
      )}

      {accommodationMode === 'PLACE_SEARCH' && (
        <div className="input-cluster">
          <div className="cluster-heading">
            <strong>숙소 검색</strong>
            <span>{selectedAccommodation ? '선택 완료' : 'Google Places'}</span>
          </div>
          <div className="destination-search-section accommodation-search-section">
            <label className="destination-search-label">
              <span className="trip-create-sr-only">숙소명 또는 주소 검색</span>
              <span className="destination-search-control">
                <input
                  type="text"
                  placeholder="숙소명 또는 주소를 검색해 주세요"
                  value={accommodationQuery}
                  maxLength={120}
                  onChange={(event) => onAccommodationQueryChange(event.target.value)}
                  onCompositionEnd={onAccommodationCompositionEnd}
                  onCompositionStart={onAccommodationCompositionStart}
                  onKeyDown={(event) => {
                    if (event.key === 'Enter') {
                      event.preventDefault()
                      onAccommodationSearch()
                    }
                  }}
                  autoComplete="off"
                  aria-busy={accommodationSearchStatus === 'loading'}
                  aria-describedby="accommodation-search-guide accommodation-search-status"
                />
                <button
                  className="destination-search-icon-button"
                  type="button"
                  disabled={!canSearchAccommodation}
                  onClick={onAccommodationSearch}
                  aria-label="숙소 검색"
                >
                  <span className="destination-search-icon" aria-hidden="true" />
                </button>
              </span>
            </label>
            <p
              className={`destination-search-guide ${accommodationSearchError ? 'error' : ''}`}
              id="accommodation-search-guide"
            >
              {accommodationGuide}
            </p>
          </div>

          <section className="destination-candidate-area accommodation-candidate-area" id="accommodation-search-status" aria-live="polite">
            {accommodationSearchStatus === 'loading' && (
              <p className="destination-search-state">숙소를 검색하고 있어요.</p>
            )}

            {showAccommodationNoResults && (
              <div className="destination-search-empty">
                <strong>숙소를 찾을 수 없어요.</strong>
                <span>숙소명, 지점명 또는 주소를 조금 더 구체적으로 입력해 주세요.</span>
              </div>
            )}

            {canShowAccommodationResults && (
              <ul className="destination-candidate-list" aria-label="숙소 검색 결과">
                {accommodationResults.map((candidate) => (
                  <li key={candidate.placeId}>
                    <PlaceCandidateCard
                      candidate={candidate}
                      isSelected={false}
                      selectedLabel="선택된 숙소입니다."
                      onSelect={() => onAccommodationSelect(candidate)}
                    />
                  </li>
                ))}
              </ul>
            )}

            {selectedAccommodation && (
              <PlaceCandidateCard
                candidate={selectedAccommodation}
                isSelected
                selectedLabel="선택된 숙소입니다."
                onSelect={() => undefined}
              />
            )}
          </section>
        </div>
      )}

      {accommodationMode === 'PLACE_SEARCH' && (
        <div className="trip-date-range-card time-range">
          <label>
            <span>체크인</span>
            <input type="time" value={checkInTime} onChange={(event) => onCheckInTimeChange(event.target.value)} />
          </label>
          <span className="date-range-connector" aria-hidden="true" />
          <label>
            <span>체크아웃</span>
            <input type="time" value={checkOutTime} onChange={(event) => onCheckOutTimeChange(event.target.value)} />
          </label>
        </div>
      )}

      <div className="input-cluster schedule-time-panel">
        <div className="cluster-heading">
          <strong>하루 일정 시간</strong>
          <span>
            {scheduleTimeMode === 'DEFAULT'
              ? `${DEFAULT_DAILY_START_TIME} ~ ${DEFAULT_DAILY_END_TIME}`
              : `${dailyStartTime} ~ ${dailyEndTime}`}
          </span>
        </div>
        <div className="option-card-grid compact">
          <ToggleCard
            isSelected={scheduleTimeMode === 'DEFAULT'}
            label="기본 시간 사용"
            onClick={() => onScheduleTimeModeChange('DEFAULT')}
          />
          <ToggleCard
            isSelected={scheduleTimeMode === 'CUSTOM'}
            label="직접 설정"
            onClick={() => onScheduleTimeModeChange('CUSTOM')}
          />
        </div>
        {scheduleTimeMode === 'DEFAULT' && (
          <p className="schedule-time-help">기본적으로 오전 8시부터 오후 8시까지 일정을 만들어요.</p>
        )}
        {scheduleTimeMode === 'CUSTOM' && (
          <div className="trip-date-range-card time-range">
            <label>
              <span>일정 시작</span>
              <input type="time" value={dailyStartTime} onChange={(event) => onDailyStartTimeChange(event.target.value)} />
            </label>
            <span className="date-range-connector" aria-hidden="true" />
            <label>
              <span>일정 종료</span>
              <input type="time" value={dailyEndTime} onChange={(event) => onDailyEndTimeChange(event.target.value)} />
            </label>
          </div>
        )}
      </div>
    </div>
  )
}

function PlaceCandidateCard({
  candidate,
  isSelected,
  selectedLabel,
  onSelect,
}: {
  candidate: PlaceAutocompleteItem
  isSelected: boolean
  selectedLabel: string
  onSelect: () => void
}) {
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
          {isSelected && <small className="candidate-status">{selectedLabel}</small>}
        </span>
      </button>
    </article>
  )
}

function accommodationSearchGuide({
  isComposing,
  queryLength,
  searchStatus,
  selectedAccommodation,
}: {
  isComposing: boolean
  queryLength: number
  searchStatus: AsyncStatus
  selectedAccommodation: PlaceAutocompleteItem | null
}) {
  if (selectedAccommodation) {
    return '선택한 숙소가 맞으면 다음 단계로 이동해 주세요. 검색어를 수정하면 선택이 해제됩니다.'
  }
  if (searchStatus === 'loading') {
    return '숙소를 검색하고 있어요.'
  }
  if (isComposing) {
    return '한글 입력이 끝나면 Enter 또는 검색 아이콘으로 검색해 주세요.'
  }
  if (queryLength > 0 && queryLength < MIN_ACCOMMODATION_QUERY_LENGTH) {
    return '2글자 이상 입력한 뒤 Enter 또는 검색 아이콘을 눌러 주세요.'
  }
  return 'Enter를 누르거나 검색 아이콘을 클릭하면 Google Places에서 숙소를 검색합니다.'
}

function mustVisitSearchGuide({
  isComposing,
  queryLength,
  searchStatus,
  selectedCount,
}: {
  isComposing: boolean
  queryLength: number
  searchStatus: AsyncStatus
  selectedCount: number
}) {
  if (selectedCount >= 5) {
    return '꼭 가고 싶은 곳은 최대 5개까지 선택할 수 있어요.'
  }
  if (searchStatus === 'loading') {
    return '장소를 검색하고 있어요.'
  }
  if (isComposing) {
    return '한글 입력이 끝나면 Enter 또는 검색 아이콘으로 검색해 주세요.'
  }
  if (queryLength > 0 && queryLength < MIN_MUST_VISIT_QUERY_LENGTH) {
    return '2글자 이상 입력한 뒤 Enter 또는 검색 아이콘을 눌러 주세요.'
  }
  return 'Enter를 누르거나 검색 아이콘을 클릭하면 목적지 주변 Google Places에서 장소를 검색합니다.'
}

function RequestsInfoPanel({
  avoidItems,
  freeRequest,
  mustVisitQuery,
  mustVisitResults,
  mustVisitPlaces,
  mustVisitSearchStatus,
  mustVisitSearchError,
  isMustVisitComposing,
  onAvoidItemToggle,
  onFreeRequestChange,
  onMustVisitQueryChange,
  onMustVisitSearch,
  onMustVisitSelect,
  onMustVisitRemove,
  onMustVisitCompositionStart,
  onMustVisitCompositionEnd,
}: {
  avoidItems: AvoidItem[]
  freeRequest: string
  mustVisitQuery: string
  mustVisitResults: PlaceAutocompleteItem[]
  mustVisitPlaces: PlaceAutocompleteItem[]
  mustVisitSearchStatus: AsyncStatus
  mustVisitSearchError: string
  isMustVisitComposing: boolean
  onAvoidItemToggle: (value: AvoidItem) => void
  onFreeRequestChange: (value: string) => void
  onMustVisitQueryChange: (value: string) => void
  onMustVisitSearch: () => void
  onMustVisitSelect: (value: PlaceAutocompleteItem) => void
  onMustVisitRemove: (value: string) => void
  onMustVisitCompositionStart: () => void
  onMustVisitCompositionEnd: (event: CompositionEvent<HTMLInputElement>) => void
}) {
  const mustVisitQueryLength = mustVisitQuery.trim().length
  const canSearchMustVisit = (
    mustVisitPlaces.length < 5
    && mustVisitSearchStatus !== 'loading'
    && !isMustVisitComposing
    && mustVisitQueryLength >= MIN_MUST_VISIT_QUERY_LENGTH
  )
  const showMustVisitNoResults = (
    mustVisitSearchStatus === 'success'
    && mustVisitResults.length === 0
    && mustVisitQueryLength >= MIN_MUST_VISIT_QUERY_LENGTH
  )
  const mustVisitGuide = mustVisitSearchError
    ? mustVisitSearchError
    : mustVisitSearchGuide({
        isComposing: isMustVisitComposing,
        queryLength: mustVisitQueryLength,
        searchStatus: mustVisitSearchStatus,
        selectedCount: mustVisitPlaces.length,
      })

  return (
    <div className="trip-info-fields">
      <div className="input-cluster">
        <div className="cluster-heading">
          <strong>꼭 가고 싶은 곳</strong>
          <span>{mustVisitPlaces.length} / 5 선택</span>
        </div>
        <div className="destination-search-section must-visit-search-section">
          <label className="destination-search-label">
            <span className="trip-create-sr-only">꼭 가고 싶은 장소 검색</span>
            <span className="destination-search-control">
              <input
                type="text"
                placeholder="장소명 또는 주소를 검색해 주세요"
                value={mustVisitQuery}
                maxLength={120}
                onChange={(event) => onMustVisitQueryChange(event.target.value)}
                onCompositionEnd={onMustVisitCompositionEnd}
                onCompositionStart={onMustVisitCompositionStart}
                onKeyDown={(event) => {
                  if (event.key === 'Enter') {
                    event.preventDefault()
                    onMustVisitSearch()
                  }
                }}
                autoComplete="off"
                aria-busy={mustVisitSearchStatus === 'loading'}
                aria-describedby="must-visit-search-guide must-visit-search-status"
              />
              <button
                className="destination-search-icon-button"
                type="button"
                disabled={!canSearchMustVisit}
                onClick={onMustVisitSearch}
                aria-label="꼭 가고 싶은 장소 검색"
              >
                <span className="destination-search-icon" aria-hidden="true" />
              </button>
            </span>
          </label>
          <p
            className={`destination-search-guide ${mustVisitSearchError ? 'error' : ''}`}
            id="must-visit-search-guide"
          >
            {mustVisitGuide}
          </p>
        </div>

        <section className="destination-candidate-area must-visit-candidate-area" id="must-visit-search-status" aria-live="polite">
          {mustVisitSearchStatus === 'loading' && (
            <p className="destination-search-state">장소를 검색하고 있어요.</p>
          )}

          {showMustVisitNoResults && (
            <div className="destination-search-empty">
              <strong>장소를 찾을 수 없어요.</strong>
              <span>장소명, 지역명 또는 주소를 조금 더 구체적으로 입력해 주세요.</span>
            </div>
          )}

          {mustVisitResults.length > 0 && (
            <ul className="destination-candidate-list" aria-label="꼭 가고 싶은 장소 검색 결과">
              {mustVisitResults.map((candidate) => {
                const isSelected = mustVisitPlaces.some((place) => place.placeId === candidate.placeId)
                return (
                  <li key={candidate.placeId}>
                    <PlaceCandidateCard
                      candidate={candidate}
                      isSelected={isSelected}
                      selectedLabel="꼭 가보고 싶은 곳입니다."
                      onSelect={() => onMustVisitSelect(candidate)}
                    />
                  </li>
                )
              })}
            </ul>
          )}

          <div className="selected-place-card-list" aria-label="선택한 꼭 가보고 싶은 곳">
            {mustVisitPlaces.length === 0 && <span>최대 5개까지 추가할 수 있어요.</span>}
            {mustVisitPlaces.map((place) => (
              <PlaceCandidateCard
                candidate={place}
                isSelected
                key={place.placeId}
                selectedLabel="꼭 가보고 싶은 곳입니다. 다시 누르면 제외됩니다."
                onSelect={() => onMustVisitRemove(place.placeId)}
              />
            ))}
          </div>
        </section>
      </div>

      <div className="input-cluster">
        <div className="cluster-heading">
          <strong>피하고 싶은 일정</strong>
          <span>{avoidItems.length}개 선택</span>
        </div>
        <div className="chip-grid">
          {AVOID_OPTIONS.map((option) => (
            <ToggleChip
              isSelected={avoidItems.includes(option.id)}
              key={option.id}
              label={option.label}
              onClick={() => onAvoidItemToggle(option.id)}
            />
          ))}
        </div>
      </div>

      <label className="trip-info-field">
        <span>그 밖에 원하는 내용</span>
        <textarea
          maxLength={800}
          placeholder="예: 둘째 날은 오전 10시 이후부터 시작하고, 현지인들이 많이 가는 식당을 포함해 주세요."
          value={freeRequest}
          onChange={(event) => onFreeRequestChange(event.target.value)}
        />
        <small>{freeRequest.length} / 800</small>
      </label>
    </div>
  )
}

function ReviewInfoPanel({
  summary,
  onEditStep,
}: {
  summary: TripInfoSummaryData
  onEditStep: (stepId: Exclude<TripInfoStepId, 'GENERATING'>) => void
}) {
  const warnings = getReviewWarnings(summary)

  return (
    <div className="review-card-grid">
      <ReviewCard title="목적지·기본 정보" onEdit={() => onEditStep('BASIC')}>
        <strong>{summary.destination?.displayText ?? '목적지 선택 전'}</strong>
        <span>{summary.title || '여행방 이름 입력 전'}</span>
        <span>{summary.startDate && summary.endDate ? `${summary.startDate} ~ ${summary.endDate}` : '날짜 입력 전'}</span>
        <span>{summary.tripDuration ? tripDurationLabel(summary.tripDuration) : '기간 계산 전'}</span>
      </ReviewCard>

      <ReviewCard title="동행 정보" onEdit={() => onEditStep('COMPANION')}>
        <strong>{companionTypeLabel(summary.companionType)} 여행 · 총 {summary.companionCount}명</strong>
        <span>어린이 {summary.hasChildren ? `${summary.childCount}명` : '없음'} · 시니어 {summary.hasSeniors ? `${summary.seniorCount}명` : '없음'}</span>
      </ReviewCard>

      <ReviewCard title="예산" onEdit={() => onEditStep('BUDGET')}>
        <strong>{summary.budgetNumericAmount > 0 ? formatCurrencyAmount(summary.budgetNumericAmount, summary.currencyCode) : budgetLevelLabel(summary.budgetLevel)}</strong>
        <span>{summary.includedBudgetItems.map(budgetItemLabel).join(' · ') || '포함 항목 없음'}</span>
      </ReviewCard>

      <ReviewCard title="여행 성향" onEdit={() => onEditStep('PREFERENCE')}>
        <strong>{travelPaceLabel(summary.travelPace)}</strong>
        <span>{summary.interests.map(interestLabel).join(' · ') || '관심사 선택 전'}</span>
        <span>{transportModeLabel(summary.primaryTransportMode)} 중심</span>
      </ReviewCard>

      <ReviewCard title="숙소" onEdit={() => onEditStep('ACCOMMODATION')}>
        <strong>{accommodationModeLabel(summary.accommodationMode)}</strong>
        <span>
          {summary.accommodationMode === 'PLACE_SEARCH'
            ? summary.selectedAccommodation?.mainText ?? '숙소 선택 전'
            : accommodationAreaLabel(summary.accommodationArea)}
        </span>
        {summary.accommodationMode === 'PLACE_SEARCH' && summary.selectedAccommodation?.secondaryText && (
          <span>{summary.selectedAccommodation.secondaryText}</span>
        )}
      </ReviewCard>

      <ReviewCard title="하루 일정 시간" onEdit={() => onEditStep('ACCOMMODATION')}>
        <strong>{summary.appliedDailyStartTime} ~ {summary.appliedDailyEndTime}</strong>
        <span>일정을 배치할 수 있는 하루 기준 시간입니다.</span>
      </ReviewCard>

      <ReviewCard title="추가 요청" onEdit={() => onEditStep('REQUESTS')}>
        <strong>꼭 가고 싶은 곳 {summary.mustVisitPlaces.length}개</strong>
        <span>{summary.mustVisitPlaces.map((place) => place.mainText).join(' · ') || '없음'}</span>
        <span>{summary.avoidItems.map(avoidItemLabel).join(' · ') || '피하고 싶은 일정 없음'}</span>
        {summary.freeRequest && <span>{summary.freeRequest}</span>}
      </ReviewCard>

      {warnings.length > 0 && (
        <div className="review-warning-card">
          <strong>생성 전 참고</strong>
          {warnings.map((warning) => <span key={warning}>{warning}</span>)}
        </div>
      )}
    </div>
  )
}

function ReviewCard({
  children,
  title,
  onEdit,
}: {
  children: ReactNode
  title: string
  onEdit: () => void
}) {
  return (
    <article className="review-card">
      <div>
        <h3>{title}</h3>
        <button type="button" onClick={onEdit}>수정</button>
      </div>
      <div>{children}</div>
    </article>
  )
}

function GeneratingTripPanel({
  aiRequestJson,
  companionType,
  createdTripId,
  destination,
  generation,
  manualMessage,
  manualPrompt,
  manualValidationReport,
  manualResponseJson,
  manualStatus,
  onAiRequestLoad,
  onCopyText,
  onOpenCreatedTrip,
  onManualPromptLoad,
  onManualResponseChange,
  onManualResponseSubmit,
  onManualResponseValidate,
  submitError,
  submitStatus,
  title,
}: {
  aiRequestJson: string
  companionType: CompanionType
  createdTripId: string
  destination: PlaceAutocompleteItem | null
  generation: TrackedItineraryGeneration | null
  manualMessage: string
  manualPrompt: string
  manualValidationReport: AiItineraryValidationReport | null
  manualResponseJson: string
  manualStatus: AsyncStatus
  onAiRequestLoad: () => void
  onCopyText: (value: string) => void
  onOpenCreatedTrip: (tripId: string) => void
  onManualPromptLoad: () => void
  onManualResponseChange: (value: string) => void
  onManualResponseSubmit: () => void
  onManualResponseValidate: () => void
  submitError: string
  submitStatus: AsyncStatus
  title: string
}) {
  const destinationName = destination?.mainText ?? '여행지'
  const mascotImage = MASCOT_IMAGE_BY_COMPANION[companionType]
  const isManualHandoffReady = isGenerationReadyForManualHandoff(generation)
  const steps = [
    '여행 정보 확인',
    '목적지 주변 장소 탐색',
    '관심사에 맞는 후보지 선별',
    '날짜별 이동 동선 구성',
    '최종 일정 검토',
  ]

  return (
    <section className="trip-generating-card" aria-live="polite">
      <div className="generating-globe-loader" aria-hidden="true">
        <span className="loader-globe" />
        <span className="loader-route" />
        <img className="loader-mascot-image" src={mascotImage} alt="" />
        <span className="loader-marker marker-one" />
        <span className="loader-marker marker-two" />
        <span className="loader-marker marker-three" />
      </div>
      <span>{submitStatus === 'success' ? '일정 준비 완료' : 'AI 일정 준비 중'}</span>
      <h1>{title || destinationName} 여행을 만들고 있어요.</h1>
      <p>입력한 조건을 기준으로 장소 후보와 하루 단위 동선을 정리합니다.</p>
      <ol>
        {steps.map((step, index) => (
          <li className={index < 2 ? 'done' : index === 2 ? 'active' : ''} key={step}>
            <span>{index < 2 ? '✓' : index === 2 ? '●' : '○'}</span>
            {step}
          </li>
        ))}
      </ol>
      {MANUAL_HANDOFF_ENABLED && generation && (
        <section className="manual-handoff-panel" aria-label="Manual handoff">
          <div className="manual-handoff-heading">
            <span>Manual handoff</span>
            <strong>{generation.status}</strong>
            <p>{generationCandidateMessage(generation)}</p>
          </div>
          <div className="manual-handoff-actions">
            <button type="button" onClick={onManualPromptLoad} disabled={manualStatus === 'loading' || !isManualHandoffReady}>
              프롬프트 조회
            </button>
            <button type="button" onClick={onAiRequestLoad} disabled={manualStatus === 'loading' || !isManualHandoffReady}>
              AI request JSON 조회
            </button>
            <button type="button" onClick={() => onOpenCreatedTrip(createdTripId)} disabled={!createdTripId}>
              상세 화면 열기
            </button>
          </div>
          {manualMessage && <p className={`manual-handoff-message ${manualStatus}`}>{manualMessage}</p>}
          {manualPrompt && (
            <div className="manual-handoff-output">
              <div>
                <strong>Prompt</strong>
                <button type="button" onClick={() => onCopyText(manualPrompt)}>복사</button>
              </div>
              <textarea readOnly value={manualPrompt} />
            </div>
          )}
          {aiRequestJson && (
            <div className="manual-handoff-output">
              <div>
                <strong>AI request JSON</strong>
                <button type="button" onClick={() => onCopyText(aiRequestJson)}>복사</button>
              </div>
              <textarea readOnly value={aiRequestJson} />
            </div>
          )}
          <div className="manual-handoff-output">
            <div>
              <strong>ChatGPT response JSON</strong>
              <button
                type="button"
                onClick={onManualResponseValidate}
                disabled={manualStatus === 'loading' || !isManualHandoffReady || !manualResponseJson.trim()}
              >
                Validate response
              </button>
              <button
                type="button"
                onClick={onManualResponseSubmit}
                disabled={manualStatus === 'loading' || !manualResponseJson.trim()}
              >
                응답 제출
              </button>
            </div>
            <textarea
              placeholder="ChatGPT가 반환한 JSON을 붙여넣으세요."
              value={manualResponseJson}
              onChange={(event) => onManualResponseChange(event.target.value)}
            />
            <AiItineraryValidationReportPanel report={manualValidationReport} />
          </div>
        </section>
      )}
      {submitError && <p className="trip-create-submit-error" role="alert">{submitError}</p>}
    </section>
  )
}

function TripInfoSummary({ summary }: { summary: TripInfoSummaryData }) {
  return (
    <aside className="trip-info-summary" aria-label="현재까지 선택한 내용">
      <span>현재 선택</span>
      <strong>{summary.destination?.mainText ?? '목적지'} · {summary.tripDuration ? tripDurationLabel(summary.tripDuration) : '기간 미정'}</strong>
      <p>
        {companionTypeLabel(summary.companionType)} {summary.companionCount}명 · {travelPaceLabel(summary.travelPace)} · {summary.interests.slice(0, 3).map(interestLabel).join(' · ') || '관심사 선택 전'}
      </p>
    </aside>
  )
}

function OptionGrid({ children, label }: { children: ReactNode; label: string }) {
  return (
    <div className="input-cluster">
      <div className="cluster-heading">
        <strong>{label}</strong>
      </div>
      <div className="option-card-grid">{children}</div>
    </div>
  )
}

function OptionCard({
  description,
  imageSrc,
  isSelected,
  label,
  onClick,
}: {
  description: string
  imageSrc?: string
  isSelected: boolean
  label: string
  onClick: () => void
}) {
  return (
    <button className={`option-card ${imageSrc ? 'has-image' : ''} ${isSelected ? 'selected' : ''}`} type="button" aria-pressed={isSelected} onClick={onClick}>
      {imageSrc && <img className="option-card-image" src={imageSrc} alt="" aria-hidden="true" />}
      <span className="option-card-copy">
        <strong>{label}</strong>
        <span>{description}</span>
      </span>
    </button>
  )
}

function ToggleCard({
  isSelected,
  label,
  onClick,
}: {
  isSelected: boolean
  label: string
  onClick: () => void
}) {
  return (
    <button className={`toggle-card ${isSelected ? 'selected' : ''}`} type="button" aria-pressed={isSelected} onClick={onClick}>
      <span>{isSelected ? '✓' : ''}</span>
      {label}
    </button>
  )
}

function ToggleChip({
  isSelected,
  label,
  onClick,
}: {
  isSelected: boolean
  label: string
  onClick: () => void
}) {
  return (
    <button className={`toggle-chip ${isSelected ? 'selected' : ''}`} type="button" aria-pressed={isSelected} onClick={onClick}>
      {label}
    </button>
  )
}

function CounterControl({
  label,
  max,
  min,
  value,
  onChange,
}: {
  label: string
  max: number
  min: number
  value: number
  onChange: (value: number) => void
}) {
  return (
    <div className="counter-control">
      <span>{label}</span>
      <div>
        <button type="button" disabled={value <= min} onClick={() => onChange(value - 1)} aria-label={`${label} 줄이기`}>-</button>
        <strong>{value}명</strong>
        <button type="button" disabled={value >= max} onClick={() => onChange(value + 1)} aria-label={`${label} 늘리기`}>+</button>
      </div>
    </div>
  )
}

function CareOptionCard({
  children,
  count,
  isActive,
  label,
  onCountChange,
  onToggle,
}: {
  children?: ReactNode
  count: number
  isActive: boolean
  label: string
  onCountChange: (value: number) => void
  onToggle: (value: boolean) => void
}) {
  return (
    <section className={`care-option-card ${isActive ? 'selected' : ''}`}>
      <div>
        <strong>{label}</strong>
        <div className="binary-toggle" role="group" aria-label={label}>
          <button className={!isActive ? 'active' : ''} type="button" onClick={() => onToggle(false)}>아니요</button>
          <button className={isActive ? 'active' : ''} type="button" onClick={() => onToggle(true)}>네</button>
        </div>
      </div>
      {isActive && (
        <>
          <CounterControl label="인원" max={12} min={1} value={count} onChange={onCountChange} />
          {children}
        </>
      )}
    </section>
  )
}

function getTripInfoQuestion(step: TripInfoStepId) {
  switch (step) {
    case 'BASIC':
      return '언제, 얼마나 여행할까요?'
    case 'COMPANION':
      return '누구와 함께 떠나나요?'
    case 'BUDGET':
      return '여행 예산은 어느 정도인가요?'
    case 'PREFERENCE':
      return '어떤 여행을 좋아하나요?'
    case 'ACCOMMODATION':
      return '어디에서 머물 예정인가요?'
    case 'REQUESTS':
      return '꼭 반영했으면 하는 내용이 있나요?'
    case 'REVIEW':
      return '이 조건으로 여행을 만들어볼까요?'
    case 'GENERATING':
      return '여행을 만들고 있어요.'
    default:
      return '여행 정보를 알려주세요.'
  }
}

function getTripInfoDescription(step: TripInfoStepId) {
  switch (step) {
    case 'BASIC':
      return '여행 기간에 맞춰 방문할 장소 수와 하루 일정을 조절할게요.'
    case 'COMPANION':
      return '동행자에 맞춰 이동 속도와 장소 선택을 조절할게요.'
    case 'BUDGET':
      return '예산 안에서 숙소, 식사, 이동과 방문지를 균형 있게 구성할게요.'
    case 'PREFERENCE':
      return '여행 속도와 관심사에 맞춰 하루의 밀도와 장소 구성을 결정할게요.'
    case 'ACCOMMODATION':
      return '숙소를 기준으로 매일의 출발지와 복귀 동선을 계산할게요.'
    case 'REQUESTS':
      return '가고 싶은 장소와 피하고 싶은 일정을 마지막으로 알려주세요.'
    case 'REVIEW':
      return '입력한 내용을 확인한 뒤 AI가 전체 일정을 구성합니다.'
    case 'GENERATING':
      return '입력한 조건을 기준으로 장소 후보를 찾고 전체 일정을 구성합니다.'
    default:
      return ''
  }
}

function getTripInfoNextLabel(step: TripInfoStepId) {
  switch (step) {
    case 'BASIC':
      return '누구와 함께 가는지 알려주기'
    case 'COMPANION':
      return '여행 예산 정하기'
    case 'BUDGET':
      return '여행 취향 선택하기'
    case 'PREFERENCE':
      return '숙소 정보 입력하기'
    case 'ACCOMMODATION':
      return '마지막 요청 알려주기'
    case 'REQUESTS':
      return '입력 내용 확인하기'
    default:
      return '다음'
  }
}

function getTripInfoStepError(
  step: TripInfoStepId,
  values: {
    title: string
    startDate: string
    endDate: string
    dateRangeValid: boolean
    companionCount: number
    childCount: number
    seniorCount: number
    budgetAmount: number
    budgetLevel: BudgetLevel
    interests: InterestId[]
    accommodationMode: AccommodationMode
    selectedAccommodation: PlaceAutocompleteItem | null
    dailyScheduleRangeValid: boolean
  },
) {
  const shouldValidateBasic = step === 'BASIC' || step === 'REVIEW'
  const shouldValidateCompanion = step === 'COMPANION' || step === 'REVIEW'
  const shouldValidateBudget = step === 'BUDGET' || step === 'REVIEW'
  const shouldValidatePreference = step === 'PREFERENCE' || step === 'REVIEW'
  const shouldValidateAccommodation = step === 'ACCOMMODATION' || step === 'REVIEW'

  if (shouldValidateBasic) {
    if (!values.title) {
      return '여행방 이름을 입력해 주세요.'
    }
    if (!values.startDate || !values.endDate) {
      return '출발일과 돌아오는 날을 선택해 주세요.'
    }
    if (!values.dateRangeValid) {
      return '돌아오는 날은 출발일과 같거나 이후여야 합니다.'
    }
  }

  if (shouldValidateCompanion) {
    if (values.companionCount < 1) {
      return '여행 인원은 1명 이상이어야 합니다.'
    }
    if (!childAndSeniorCountValid(values.companionCount, values.childCount, values.seniorCount)) {
      return '어린이와 시니어 인원 합계가 전체 인원을 넘을 수 없습니다.'
    }
  }

  if (shouldValidateBudget && values.budgetAmount < 0 && !values.budgetLevel) {
    return '총예산 또는 예산 수준 중 하나를 선택해 주세요.'
  }

  if (shouldValidatePreference && values.interests.length < 1) {
    return '관심사를 최소 1개 선택해 주세요.'
  }

  if (shouldValidateAccommodation && values.accommodationMode === 'PLACE_SEARCH' && !values.selectedAccommodation) {
    return '검색 결과에서 숙소를 선택해 주세요.'
  }

  if (shouldValidateAccommodation && !values.dailyScheduleRangeValid) {
    return '하루 일정 시작 시간은 종료 시간보다 빨라야 합니다.'
  }

  return ''
}

function childAndSeniorCountValid(companionCount: number, childCount: number, seniorCount: number) {
  return childCount + seniorCount <= companionCount
}

function getReviewWarnings(summary: TripInfoSummaryData) {
  const warnings: string[] = []
  if (summary.mustVisitPlaces.length >= 5) {
    warnings.push('꼭 가고 싶은 장소가 많아 일정이 다소 빡빡해질 수 있어요.')
  }
  if (summary.includedBudgetItems.includes('FLIGHT')) {
    warnings.push('항공권 포함 예산은 실제 항공권 가격 연동 전까지 참고값으로만 사용됩니다.')
  }
  return warnings
}

function getTitleSuggestions(destinationName: string, tripDuration: TripDuration | null) {
  const durationText = tripDuration ? tripDurationLabel(tripDuration) : '여행'
  return [
    `${destinationName} ${durationText}`,
    `${destinationName} 먹고 쉬는 여행`,
    `${destinationName} 여유로운 주말`,
  ]
}

function tripDurationLabel(duration: TripDuration) {
  return duration.days === 1 ? '당일치기' : `${duration.days}일 여행`
}

function tripDurationSentence(duration: TripDuration) {
  return duration.days === 1 ? '당일치기로 준비해요.' : `총 ${duration.days}일 동안 여행해요.`
}

function getDateRangeError(startDate: string, endDate: string) {
  if (!startDate || !endDate || startDate <= endDate) {
    return ''
  }
  return '돌아오는 날은 출발일과 같거나 이후여야 합니다.'
}

function getTripDuration(startDate: string, endDate: string): TripDuration | null {
  if (!startDate || !endDate || startDate > endDate) {
    return null
  }

  const start = new Date(`${startDate}T00:00:00`)
  const end = new Date(`${endDate}T00:00:00`)
  const days = Math.floor((end.getTime() - start.getTime()) / 86400000) + 1
  const dateLabels = Array.from({ length: Math.min(days, 7) }).map((_, index) => {
    const date = new Date(start)
    date.setDate(start.getDate() + index)
    return formatMonthDay(date)
  })

  return {
    days,
    nights: Math.max(days - 1, 0),
    dateLabels,
  }
}

function formatMonthDay(date: Date) {
  const month = String(date.getMonth() + 1).padStart(2, '0')
  const day = String(date.getDate()).padStart(2, '0')
  return `${month}/${day}`
}

function formatCurrencyInput(value: string) {
  const numberText = value.replace(/[^\d]/g, '')
  if (!numberText) {
    return ''
  }
  return Number(numberText).toLocaleString('ko-KR')
}

function parseCurrencyAmount(value: string) {
  const numberText = value.replace(/[^\d]/g, '')
  return numberText ? Number(numberText) : 0
}

function formatCurrencyAmount(value: number, currencyCode: CurrencyCode) {
  return new Intl.NumberFormat('ko-KR', {
    currency: currencyCode,
    maximumFractionDigits: 0,
    style: 'currency',
  }).format(value)
}

function clamp(value: number, min: number, max: number) {
  return Math.min(Math.max(value, min), max)
}

function toggleArrayValue<T>(values: T[], value: T) {
  return values.includes(value)
    ? values.filter((item) => item !== value)
    : [...values, value]
}

function findLabel<T extends string>(options: Array<{ id: T; label: string }>, value: T) {
  return options.find((option) => option.id === value)?.label ?? value
}

function companionTypeLabel(value: CompanionType) {
  return findLabel(COMPANION_OPTIONS, value)
}

function budgetLevelLabel(value: BudgetLevel) {
  return findLabel(BUDGET_LEVEL_OPTIONS, value)
}

function budgetItemLabel(value: BudgetItem) {
  return findLabel(BUDGET_ITEMS, value)
}

function travelPaceLabel(value: TravelPace) {
  return findLabel(PACE_OPTIONS, value)
}

function interestLabel(value: InterestId) {
  return findLabel(INTEREST_OPTIONS, value)
}

function transportModeLabel(value: TransportMode) {
  return findLabel(TRANSPORT_OPTIONS, value)
}

function accommodationModeLabel(value: AccommodationMode) {
  return findLabel(ACCOMMODATION_MODE_OPTIONS, value)
}

function accommodationAreaLabel(value: AccommodationArea) {
  return findLabel(ACCOMMODATION_AREA_OPTIONS, value)
}

function avoidItemLabel(value: AvoidItem) {
  return findLabel(AVOID_OPTIONS, value)
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
  if (searchScope === 'CITY') {
    return '도시'
  }
  if (searchScope === 'REGION') {
    return '지역'
  }
  if (searchScope === 'PLACE') {
    return '장소'
  }
  return '숙소'
}

function toSearchUserMessage(error: unknown) {
  if (error instanceof ApiError && error.status === 401) {
    return '로그인이 만료되었습니다. 다시 로그인하세요.'
  }
  return '장소 검색 서비스를 일시적으로 사용할 수 없어요. 잠시 후 다시 시도해 주세요.'
}

function validationReportMessage(report: AiItineraryValidationReport) {
  if (report.errors.length > 0) {
    return `Validation found ${report.errors.length} error(s).`
  }
  if (report.warnings.length > 0 || report.unverifiedConditions.length > 0) {
    return `Validation passed with ${report.warnings.length} warning(s) and ${report.unverifiedConditions.length} unverified condition(s).`
  }
  return 'Validation passed.'
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
