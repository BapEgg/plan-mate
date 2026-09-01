import { useEffect, useState } from 'react'
import type { AsyncStatus } from './workspaceTypes'

/**
 * Root workspace state required by spec §7. `TripWorkspacePage` computes this
 * once trip data itself has loaded successfully (an outright trip-load
 * failure is still handled by the existing full-page error card, outside
 * this state machine). `sessionExpired`/`membershipLost`/`readOnly` are real,
 * reachable states in the type system, but WP-A has no membership-removal or
 * lifecycle-lock command yet, so their producers always pass `false` today —
 * WP-B/E wire real signals into the same input shape without redesigning it.
 */
export type WorkspaceRootState =
  | 'LOADING'
  | 'READY'
  | 'EMPTY_ITINERARY'
  | 'GENERATING_WITHOUT_CURRENT'
  | 'GENERATING_WITH_CURRENT'
  | 'REFRESHING'
  | 'READ_ONLY'
  | 'MEMBERSHIP_LOST'
  | 'SESSION_EXPIRED'
  | 'PARTIAL_ERROR'

export type WorkspaceStateInput = {
  tripLoadStatus: AsyncStatus
  isManualRefreshInFlight: boolean
  hasItinerary: boolean
  generationInProgress: boolean
  generationFetchFailed: boolean
  sessionExpired: boolean
  membershipLost: boolean
  readOnly: boolean
}

/**
 * Priority order matches spec §7: auth/session > membership loss > trip
 * lifecycle read-only > connection/refresh > panel-level partial error >
 * content state. A higher-priority condition always wins even if a
 * lower-priority one is also true.
 */
export function resolveWorkspaceState(input: WorkspaceStateInput): WorkspaceRootState {
  if (input.sessionExpired) return 'SESSION_EXPIRED'
  if (input.membershipLost) return 'MEMBERSHIP_LOST'
  if (input.tripLoadStatus !== 'success') return 'LOADING'
  if (input.readOnly) return 'READ_ONLY'
  if (input.isManualRefreshInFlight) return 'REFRESHING'
  if (input.generationFetchFailed) return 'PARTIAL_ERROR'
  if (!input.hasItinerary) {
    return input.generationInProgress ? 'GENERATING_WITHOUT_CURRENT' : 'EMPTY_ITINERARY'
  }
  return input.generationInProgress ? 'GENERATING_WITH_CURRENT' : 'READY'
}

// Matches TripWorkspacePortfolio.css's own breakpoint: below this width the
// bottom pane switcher becomes the real navigation for schedule/map/room
// (MEDIUM keeps schedule+map as a 2-col grid with room as the switched-to
// pane; NARROW switches all three individually) — both bands share the same
// `.planning-mobile-switcher` + `.mobile-active` mechanism, so no separate
// MEDIUM/NARROW distinction is needed here, only "is the switcher live".
const COMPACT_MAX_WIDTH = 1180

export function isCompactWidth(width: number): boolean {
  return width < COMPACT_MAX_WIDTH
}

/** True below the breakpoint where the bottom pane switcher becomes the workspace's real navigation. */
export function useIsCompactWorkspace(): boolean {
  const [compact, setCompact] = useState<boolean>(() => (
    typeof window === 'undefined' ? false : isCompactWidth(window.innerWidth)
  ))

  useEffect(() => {
    const query = window.matchMedia(`(max-width: ${COMPACT_MAX_WIDTH - 1}px)`)
    const update = () => setCompact(isCompactWidth(window.innerWidth))
    update()
    query.addEventListener('change', update)
    return () => query.removeEventListener('change', update)
  }, [])

  return compact
}
