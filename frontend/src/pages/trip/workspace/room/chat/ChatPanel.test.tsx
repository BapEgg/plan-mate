import { describe, expect, it, vi } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { ChatPanel } from './ChatPanel'
import type { AuthUser } from '../../../../../api/auth'
import type { TripMember } from '../../../../../api/trips'

const listChatHistory = vi.fn()
const sendChatMessage = vi.fn()

vi.mock('../../../../../api/chat', () => ({
  listChatHistory: (...args: unknown[]) => listChatHistory(...args),
  sendChatMessage: (...args: unknown[]) => sendChatMessage(...args),
}))

const members: TripMember[] = [
  { userId: 1, nickname: '민준', profileImageUrl: null, role: 'OWNER' },
  { userId: 2, nickname: '서윤', profileImageUrl: null, role: 'MEMBER' },
]

const currentUser: AuthUser = { id: 1, loginId: 'local1', email: 'local1@planmate.local', nickname: '민준', role: 'USER' }

describe('ChatPanel', () => {
  it('renders fetched history, oldest first', async () => {
    listChatHistory.mockResolvedValueOnce({
      messages: [
        { id: 2, tripId: '1530', authorUserId: 2, type: 'USER_TEXT', body: '두 번째', clientMessageId: 'c2', sentAt: '2026-08-31T00:01:00Z' },
        { id: 1, tripId: '1530', authorUserId: 1, type: 'USER_TEXT', body: '첫 번째', clientMessageId: 'c1', sentAt: '2026-08-31T00:00:00Z' },
      ],
      nextCursor: null,
    })

    render(<ChatPanel accessToken="token" tripId="1530" members={members} currentUser={currentUser} latestChatMessage={null} />)

    await waitFor(() => expect(screen.getByText('첫 번째')).toBeInTheDocument())
    const bodies = screen.getAllByText(/번째/).map((node) => node.textContent)
    expect(bodies).toEqual(['첫 번째', '두 번째'])
  })

  it('sends a message optimistically and reconciles with the server response', async () => {
    listChatHistory.mockResolvedValueOnce({ messages: [], nextCursor: null })
    sendChatMessage.mockResolvedValueOnce({
      id: 5, tripId: '1530', authorUserId: 1, type: 'USER_TEXT', body: '안녕하세요', clientMessageId: 'generated', sentAt: '2026-08-31T00:02:00Z',
    })

    render(<ChatPanel accessToken="token" tripId="1530" members={members} currentUser={currentUser} latestChatMessage={null} />)
    await waitFor(() => expect(screen.getByPlaceholderText('메시지를 입력하세요…')).toBeInTheDocument())

    const user = userEvent.setup()
    await user.type(screen.getByPlaceholderText('메시지를 입력하세요…'), '안녕하세요')
    await user.click(screen.getByRole('button', { name: '메시지 보내기' }))

    expect(screen.getByText('안녕하세요')).toBeInTheDocument()
    await waitFor(() => expect(sendChatMessage).toHaveBeenCalledWith(
      'token',
      '1530',
      expect.objectContaining({ body: '안녕하세요', clientMessageId: expect.any(String) }),
    ))
    expect(screen.getAllByText('안녕하세요')).toHaveLength(1)
  })

  it('does not duplicate a message when its own broadcast echoes back over realtime', async () => {
    listChatHistory.mockResolvedValueOnce({
      messages: [{ id: 9, tripId: '1530', authorUserId: 1, type: 'USER_TEXT', body: '실시간 테스트', clientMessageId: 'echo-1', sentAt: '2026-08-31T00:00:00Z' }],
      nextCursor: null,
    })

    const { rerender } = render(
      <ChatPanel accessToken="token" tripId="1530" members={members} currentUser={currentUser} latestChatMessage={null} />,
    )
    await waitFor(() => expect(screen.getByText('실시간 테스트')).toBeInTheDocument())

    rerender(
      <ChatPanel
        accessToken="token"
        tripId="1530"
        members={members}
        currentUser={currentUser}
        latestChatMessage={{ messageId: 9, clientMessageId: 'echo-1', authorUserId: 1, type: 'USER_TEXT', body: '실시간 테스트', sentAt: '2026-08-31T00:00:00Z' }}
      />,
    )

    expect(screen.getAllByText('실시간 테스트')).toHaveLength(1)
  })
})
