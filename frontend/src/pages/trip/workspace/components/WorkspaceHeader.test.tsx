import { describe, expect, it, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import { WorkspaceHeader } from './WorkspaceHeader'
import type { TripDetail } from '../../../../api/trips'

function tripFixture(overrides: Partial<TripDetail> = {}): TripDetail {
  return {
    id: '1530',
    title: '거제 가족여행',
    destination: '거제시',
    destinationPlaceId: 'place-geoje',
    startDate: '2026-09-21',
    endDate: '2026-09-24',
    status: 'UPCOMING',
    memberCount: 3,
    createdAt: '2026-08-01T00:00:00Z',
    members: [
      { userId: 2588, nickname: 'test', profileImageUrl: null, role: 'OWNER' },
      { userId: 2623, nickname: 'local1', profileImageUrl: null, role: 'MEMBER' },
      { userId: 2624, nickname: 'local2', profileImageUrl: null, role: 'MEMBER' },
    ],
    destinationInfo: {
      placeId: 'place-geoje',
      displayName: '거제시',
      formattedAddress: null,
      latitude: null,
      longitude: null,
      viewportLowLatitude: null,
      viewportLowLongitude: null,
      viewportHighLatitude: null,
      viewportHighLongitude: null,
      types: [],
      primaryType: null,
    },
    planningProfile: null,
    itineraries: [],
    ...overrides,
  }
}

describe('WorkspaceHeader', () => {
  it('renders the trip title and the full member roster', () => {
    render(
      <WorkspaceHeader
        accessToken="test-token"
        currentUser={null}
        trip={tripFixture()}
        dayCount={4}
        placeCount={23}
        onBackToMain={vi.fn()}
        onLeftTrip={vi.fn()}
        onLogout={vi.fn()}
        onMembershipChanged={vi.fn()}
        onRefresh={vi.fn()}
      />,
    )

    expect(screen.getByRole('heading', { name: '거제 가족여행' })).toBeInTheDocument()
    expect(screen.getAllByText('3명').length).toBeGreaterThan(0)
    expect(screen.getByText('local1')).toBeInTheDocument()
  })

  it('does not render a trip condition disclosure when no planning profile is present', () => {
    render(
      <WorkspaceHeader
        accessToken="test-token"
        currentUser={null}
        trip={tripFixture({ planningProfile: null })}
        dayCount={4}
        placeCount={23}
        onBackToMain={vi.fn()}
        onLeftTrip={vi.fn()}
        onLogout={vi.fn()}
        onMembershipChanged={vi.fn()}
        onRefresh={vi.fn()}
      />,
    )

    expect(screen.queryByText('여행 조건')).not.toBeInTheDocument()
  })
})
