import type { ItineraryPlaceView } from '../../../api/trips'

export type AsyncStatus = 'idle' | 'loading' | 'success' | 'error'
export type MobileWorkspacePane = 'SCHEDULE' | 'MAP' | 'ROOM'
export type CollaborationView = 'CHAT' | 'VOTE'

export type ItineraryPlace = {
  id: string
  day: number
  order: number
  title: string
  startTime: string
  duration: string
  durationMinutes: number
  latitude: number | null
  longitude: number | null
  locationLabel: string | null
  googleMapsUri: string | null
  placeId: string | null
  resolved: boolean
  source: ItineraryPlaceView['createdSource']
}

export function toItineraryPlace(item: ItineraryPlaceView): ItineraryPlace {
  return {
    id: item.itemId.toString(),
    day: item.dayNo,
    order: item.sequence,
    title: item.display.displayName ?? item.display.fallbackMessage ?? '장소 정보를 불러오지 못했습니다',
    startTime: item.startTime.slice(0, 5),
    duration: formatDuration(item.durationMinutes),
    durationMinutes: item.durationMinutes,
    latitude: item.display.location?.latitude ?? null,
    longitude: item.display.location?.longitude ?? null,
    locationLabel: item.display.location ? `${item.display.location.latitude.toFixed(5)}, ${item.display.location.longitude.toFixed(5)}` : null,
    googleMapsUri: item.display.googleMapsUri,
    placeId: item.placeId,
    resolved: item.display.resolved,
    source: item.createdSource,
  }
}

export function formatDuration(minutes: number) {
  if (minutes < 60) return `${minutes}분`
  const hours = Math.floor(minutes / 60)
  const rest = minutes % 60
  return rest ? `${hours}시간 ${rest}분` : `${hours}시간`
}
