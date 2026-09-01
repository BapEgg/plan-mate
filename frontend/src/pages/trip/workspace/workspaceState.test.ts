import { describe, expect, it } from 'vitest'
import { isCompactWidth, resolveWorkspaceState } from './workspaceState'
import type { WorkspaceStateInput } from './workspaceState'

function baseInput(overrides: Partial<WorkspaceStateInput> = {}): WorkspaceStateInput {
  return {
    tripLoadStatus: 'success',
    isManualRefreshInFlight: false,
    hasItinerary: true,
    generationInProgress: false,
    generationFetchFailed: false,
    sessionExpired: false,
    membershipLost: false,
    readOnly: false,
    ...overrides,
  }
}

describe('resolveWorkspaceState', () => {
  it('returns READY when everything is settled and an itinerary exists', () => {
    expect(resolveWorkspaceState(baseInput())).toBe('READY')
  })

  it('returns LOADING while trip data has not loaded yet', () => {
    expect(resolveWorkspaceState(baseInput({ tripLoadStatus: 'loading' }))).toBe('LOADING')
  })

  it('returns EMPTY_ITINERARY when there is no itinerary and no generation in flight', () => {
    expect(resolveWorkspaceState(baseInput({ hasItinerary: false }))).toBe('EMPTY_ITINERARY')
  })

  it('distinguishes GENERATING_WITHOUT_CURRENT from GENERATING_WITH_CURRENT by itinerary presence', () => {
    expect(resolveWorkspaceState(baseInput({ hasItinerary: false, generationInProgress: true })))
      .toBe('GENERATING_WITHOUT_CURRENT')
    expect(resolveWorkspaceState(baseInput({ hasItinerary: true, generationInProgress: true })))
      .toBe('GENERATING_WITH_CURRENT')
  })

  it('surfaces a failed generation lookup as PARTIAL_ERROR without hiding an existing itinerary', () => {
    expect(resolveWorkspaceState(baseInput({ generationFetchFailed: true }))).toBe('PARTIAL_ERROR')
  })

  it('prioritizes session expiry over every other condition', () => {
    expect(resolveWorkspaceState(baseInput({
      sessionExpired: true,
      membershipLost: true,
      readOnly: true,
      generationFetchFailed: true,
    }))).toBe('SESSION_EXPIRED')
  })

  it('prioritizes membership loss over read-only lifecycle and connection state', () => {
    expect(resolveWorkspaceState(baseInput({
      membershipLost: true,
      readOnly: true,
      isManualRefreshInFlight: true,
    }))).toBe('MEMBERSHIP_LOST')
  })

  it('prioritizes read-only lifecycle over an in-flight manual refresh', () => {
    expect(resolveWorkspaceState(baseInput({ readOnly: true, isManualRefreshInFlight: true })))
      .toBe('READ_ONLY')
  })

  it('reports REFRESHING while a manual refresh is in flight', () => {
    expect(resolveWorkspaceState(baseInput({ isManualRefreshInFlight: true }))).toBe('REFRESHING')
  })
})

describe('isCompactWidth', () => {
  it('matches TripWorkspacePortfolio.css: the switcher becomes live below 1180px', () => {
    expect(isCompactWidth(1179)).toBe(true)
    expect(isCompactWidth(320)).toBe(true)
  })

  it('is not compact at or above 1180px, where the 3-column layout has no switcher', () => {
    expect(isCompactWidth(1180)).toBe(false)
    expect(isCompactWidth(1400)).toBe(false)
  })
})
