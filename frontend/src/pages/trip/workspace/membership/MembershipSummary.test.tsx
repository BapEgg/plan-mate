import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import type { TripMember } from '../../../../api/trips'
import { MembershipSummary } from './MembershipSummary'

const members: TripMember[] = [
  { userId: 1, nickname: '바다', profileImageUrl: null, role: 'OWNER' },
  { userId: 2, nickname: '구름', profileImageUrl: null, role: 'MEMBER' },
]

describe('MembershipSummary', () => {
  it('returns focus to the management trigger after the drawer closes', async () => {
    const user = userEvent.setup()
    vi.spyOn(window, 'requestAnimationFrame').mockImplementation((callback) => {
      callback(0)
      return 0
    })

    render(
      <MembershipSummary
        accessToken="token"
        currentUserId={1}
        members={members}
        onLeftTrip={vi.fn()}
        onMembershipChanged={vi.fn()}
        presenceByMember={null}
        tripId="1530"
      />,
    )

    await user.click(screen.getByLabelText('참여자 2명 보기'))
    const manageButton = screen.getByRole('button', { name: '여행방 관리' })
    await user.click(manageButton)
    expect(screen.getByRole('dialog', { name: '여행방 관리' })).toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: '관리 창 닫기' }))

    await waitFor(() => expect(manageButton).toHaveFocus())
  })
})
