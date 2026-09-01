import type { TripPlanningProfile } from '../../../api/trips'

export function buildTripDayNumbers(startDateValue: string, endDateValue: string) {
  const startDate = new Date(`${startDateValue}T00:00:00`)
  const endDate = new Date(`${endDateValue}T00:00:00`)
  if (Number.isNaN(startDate.getTime()) || Number.isNaN(endDate.getTime()) || endDate < startDate) return [1]
  const dayCount = Math.max(1, Math.round((endDate.getTime() - startDate.getTime()) / 86_400_000) + 1)
  return Array.from({ length: dayCount }, (_, index) => index + 1)
}

export function dateForTripDay(startDateValue: string, day: number) {
  const date = new Date(`${startDateValue}T00:00:00`)
  date.setDate(date.getDate() + day - 1)
  return toDateKey(date)
}

export function shiftDate(value: string, dayOffset: number) {
  const date = new Date(`${value}T00:00:00`)
  date.setDate(date.getDate() + dayOffset)
  return toDateKey(date)
}

function toDateKey(date: Date) {
  const year = date.getFullYear()
  const month = `${date.getMonth() + 1}`.padStart(2, '0')
  const dateOfMonth = `${date.getDate()}`.padStart(2, '0')
  return `${year}-${month}-${dateOfMonth}`
}

export function resolveBackendAssetUrl(apiBaseUrl: string, value: string) {
  if (/^https?:\/\//i.test(value) || value.startsWith('data:')) {
    return value
  }
  return `${apiBaseUrl}${value.startsWith('/') ? value : `/${value}`}`
}

export function formatDateRange(start: string, end: string) {
  return `${formatShortDate(start)} – ${formatShortDate(end)}`
}

export function formatShortDate(value: string) {
  return new Intl.DateTimeFormat('ko-KR', { month: 'short', day: 'numeric' }).format(new Date(`${value}T00:00:00`))
}

export function formatFullDate(value: string) {
  return new Intl.DateTimeFormat('ko-KR', { year: 'numeric', month: 'long', day: 'numeric', weekday: 'short' }).format(new Date(`${value}T00:00:00`))
}

export function formatDayTabDate(value: string) {
  return new Intl.DateTimeFormat('ko-KR', { month: 'numeric', day: 'numeric' }).format(new Date(`${value}T00:00:00`))
}

export function tripCountdownLabel(startDateValue: string, endDateValue: string) {
  const today = new Date()
  today.setHours(0, 0, 0, 0)
  const start = new Date(`${startDateValue}T00:00:00`)
  const end = new Date(`${endDateValue}T23:59:59`)
  const daysUntilStart = Math.ceil((start.getTime() - today.getTime()) / 86_400_000)
  if (daysUntilStart > 0) return `출발 D-${daysUntilStart}`
  if (end >= today) return '여행 중'
  return '지난 여행'
}

export function formatTime(value: string) {
  return value.slice(0, 5)
}

export function accommodationAreaLabel(value: TripPlanningProfile['accommodationArea']) {
  switch (value) {
    case 'TOURIST_CENTER': return '중심 관광지 근처'
    case 'TRANSIT': return '대중교통이 편한 곳'
    case 'QUIET': return '조용한 지역'
    case 'ANYWHERE': return '지역 상관없음'
    default: return '선호 숙소 지역 없음'
  }
}

export function companionLabel(value: TripPlanningProfile['companionType']) {
  return { SOLO: '혼자', COUPLE: '연인', FRIENDS: '친구', FAMILY: '가족', PARENTS: '부모님', COWORKERS: '동료', OTHER: '기타' }[value]
}

export function travelPaceLabel(value: TripPlanningProfile['travelPace']) {
  return { RELAXED: '여유로운', BALANCED: '균형 잡힌', PACKED: '알찬' }[value]
}

export function transportLabel(value: TripPlanningProfile['primaryTransportMode']) {
  return { WALK: '도보', PUBLIC_TRANSIT: '대중교통', RENTAL_CAR: '렌터카', TAXI: '택시', BIKE: '자전거', TOUR: '투어 이동' }[value]
}

export function interestLabel(value: TripPlanningProfile['interests'][number]) {
  return { FOOD: '맛집', SIGHTSEEING: '관광', CAFE: '카페', CULTURE: '문화', NATURE: '자연', SHOPPING: '쇼핑', PHOTO: '사진', NIGHT_VIEW: '야경', ACTIVITY: '액티비티', REST: '휴식', ART: '예술', THEME_PARK: '테마파크', LOCAL: '로컬 경험' }[value]
}
