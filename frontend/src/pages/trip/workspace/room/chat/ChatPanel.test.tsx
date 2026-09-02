import { describe, expect, it, vi } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { ChatPanel } from './ChatPanel'
import { ApiError } from '../../../../../api/client'
import type { AuthUser } from '../../../../../api/auth'
import type { TripMember } from '../../../../../api/trips'

const listChatHistory = vi.fn()
const listChatSince = vi.fn()
const sendChatMessage = vi.fn()
const getChatMessageByClientId = vi.fn()
const getChatMessage = vi.fn()
const deleteChatMessage = vi.fn()
const setChatReaction = vi.fn()
const removeChatReaction = vi.fn()
const searchChatMessages = vi.fn()
const getChatMessageContext = vi.fn()
const markChatRead = vi.fn().mockResolvedValue(undefined)

vi.mock('../../../../../api/chat', () => ({
  listChatHistory: (...args: unknown[]) => listChatHistory(...args),
  listChatSince: (...args: unknown[]) => listChatSince(...args),
  sendChatMessage: (...args: unknown[]) => sendChatMessage(...args),
  getChatMessageByClientId: (...args: unknown[]) => getChatMessageByClientId(...args),
  getChatMessage: (...args: unknown[]) => getChatMessage(...args),
  deleteChatMessage: (...args: unknown[]) => deleteChatMessage(...args),
  setChatReaction: (...args: unknown[]) => setChatReaction(...args),
  removeChatReaction: (...args: unknown[]) => removeChatReaction(...args),
  searchChatMessages: (...args: unknown[]) => searchChatMessages(...args),
  getChatMessageContext: (...args: unknown[]) => getChatMessageContext(...args),
  markChatRead: (...args: unknown[]) => markChatRead(...args),
}))

const members: TripMember[] = [
  { userId: 1, nickname: '민준', profileImageUrl: null, role: 'OWNER' },
  { userId: 2, nickname: '서윤', profileImageUrl: null, role: 'MEMBER' },
]

const currentUser: AuthUser = { id: 1, loginId: 'local1', email: 'local1@planmate.local', nickname: '민준', role: 'USER' }

function renderChatPanel(overrides: Partial<React.ComponentProps<typeof ChatPanel>> = {}) {
  return render(
    <ChatPanel
      accessToken="token"
      tripId="1530"
      members={members}
      currentUser={currentUser}
      latestChatMessage={null}
      latestChatChange={null}
      chatConnected={true}
      chatReconnectedAt={0}
      onChatRead={() => {}}
      {...overrides}
    />,
  )
}

describe('ChatPanel', () => {
  it('renders fetched history, oldest first', async () => {
    listChatHistory.mockResolvedValueOnce({
      messages: [
        { id: 2, tripId: '1530', authorUserId: 2, type: 'USER_TEXT', body: '두 번째', clientMessageId: 'c2', sentAt: '2026-08-31T00:01:00Z' },
        { id: 1, tripId: '1530', authorUserId: 1, type: 'USER_TEXT', body: '첫 번째', clientMessageId: 'c1', sentAt: '2026-08-31T00:00:00Z' },
      ],
      nextCursor: null,
    })

    renderChatPanel()

    await waitFor(() => expect(screen.getByText('첫 번째')).toBeInTheDocument())
    const bodies = screen.getAllByText(/번째/).map((node) => node.textContent)
    expect(bodies).toEqual(['첫 번째', '두 번째'])
  })

  it('sends a message optimistically and reconciles with the server response', async () => {
    listChatHistory.mockResolvedValueOnce({ messages: [], nextCursor: null })
    sendChatMessage.mockResolvedValueOnce({
      id: 5, tripId: '1530', authorUserId: 1, type: 'USER_TEXT', body: '안녕하세요', clientMessageId: 'generated', sentAt: '2026-08-31T00:02:00Z',
    })

    renderChatPanel()
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

    const { rerender } = renderChatPanel()
    await waitFor(() => expect(screen.getByText('실시간 테스트')).toBeInTheDocument())

    rerender(
      <ChatPanel
        accessToken="token"
        tripId="1530"
        members={members}
        currentUser={currentUser}
        chatConnected={true}
        chatReconnectedAt={0}
        latestChatMessage={{
          messageId: 9,
          clientMessageId: 'echo-1',
          authorUserId: 1,
          type: 'USER_TEXT',
          body: '실시간 테스트',
          sentAt: '2026-08-31T00:00:00Z',
          replyToMessageId: null,
          replyAuthorUserId: null,
          replyBody: null,
          replyDeleted: false,
        }}
        latestChatChange={null}
        onChatRead={() => {}}
      />,
    )

    expect(screen.getAllByText('실시간 테스트')).toHaveLength(1)
  })

  it('disables the composer while disconnected, keeping history readable', async () => {
    listChatHistory.mockResolvedValueOnce({
      messages: [{ id: 1, tripId: '1530', authorUserId: 2, type: 'USER_TEXT', body: '읽기는 계속 됨', clientMessageId: 'c1', sentAt: '2026-08-31T00:00:00Z' }],
      nextCursor: null,
    })

    renderChatPanel({ chatConnected: false })

    await waitFor(() => expect(screen.getByText('읽기는 계속 됨')).toBeInTheDocument())
    expect(screen.getByPlaceholderText('메시지를 입력하세요…')).toBeDisabled()
    expect(screen.getByRole('button', { name: '메시지 보내기' })).toBeDisabled()
    expect(screen.getByRole('status')).toHaveTextContent('연결을 다시 확인하고 있습니다')
  })

  it('fills the REST gap on reconnect without duplicating already-rendered messages', async () => {
    listChatHistory.mockResolvedValueOnce({
      messages: [{ id: 1, tripId: '1530', authorUserId: 1, type: 'USER_TEXT', body: '재연결 전', clientMessageId: 'c1', sentAt: '2026-08-31T00:00:00Z' }],
      nextCursor: null,
    })
    listChatSince.mockResolvedValueOnce({
      messages: [{ id: 2, tripId: '1530', authorUserId: 2, type: 'USER_TEXT', body: '연결 끊긴 동안 온 메시지', clientMessageId: 'c2', sentAt: '2026-08-31T00:05:00Z' }],
      nextCursor: null,
    })

    const { rerender } = renderChatPanel()
    await waitFor(() => expect(screen.getByText('재연결 전')).toBeInTheDocument())

    rerender(
      <ChatPanel
        accessToken="token"
        tripId="1530"
        members={members}
        currentUser={currentUser}
        latestChatMessage={null}
        latestChatChange={null}
        chatConnected={true}
        chatReconnectedAt={12345}
        onChatRead={() => {}}
      />,
    )

    await waitFor(() => expect(listChatSince).toHaveBeenCalledWith('token', '1530', 1))
    await waitFor(() => expect(screen.getByText('연결 끊긴 동안 온 메시지')).toBeInTheDocument())
    expect(screen.getAllByText('연결 끊긴 동안 온 메시지')).toHaveLength(1)
  })

  it('offers a manual resend for a message confirmed unsaved after a failed send', async () => {
    listChatHistory.mockResolvedValueOnce({ messages: [], nextCursor: null })
    sendChatMessage.mockRejectedValueOnce(new Error('network down'))
    getChatMessageByClientId.mockRejectedValueOnce(new ApiError(404, 'not found', 'MESSAGE_NOT_FOUND'))
    sendChatMessage.mockResolvedValueOnce({
      id: 7, tripId: '1530', authorUserId: 1, type: 'USER_TEXT', body: '재시도', clientMessageId: 'retry-1', sentAt: '2026-08-31T00:10:00Z',
    })

    renderChatPanel()
    await waitFor(() => expect(screen.getByPlaceholderText('메시지를 입력하세요…')).toBeInTheDocument())

    const user = userEvent.setup()
    await user.type(screen.getByPlaceholderText('메시지를 입력하세요…'), '재시도')
    await user.click(screen.getByRole('button', { name: '메시지 보내기' }))

    await waitFor(() => expect(screen.getByText('전송 실패')).toBeInTheDocument())
    await user.click(screen.getByRole('button', { name: '다시 보내기' }))

    await waitFor(() => expect(sendChatMessage).toHaveBeenCalledTimes(2))
    await waitFor(() => expect(screen.queryByText('전송 실패')).not.toBeInTheDocument())
  })

  it('sends a one-level reply with the selected message id', async () => {
    listChatHistory.mockResolvedValueOnce({
      messages: [{ id: 12, tripId: '1530', authorUserId: 2, type: 'USER_TEXT', body: '카페는 어디로 갈까요?', clientMessageId: 'reply-origin', sentAt: new Date().toISOString() }],
      nextCursor: null,
    })
    sendChatMessage.mockResolvedValueOnce({
      id: 13,
      tripId: '1530',
      authorUserId: 1,
      type: 'USER_TEXT',
      body: '바다 보이는 곳이 좋아요.',
      clientMessageId: 'reply-sent',
      sentAt: new Date().toISOString(),
      replyTo: { messageId: 12, authorUserId: 2, body: '카페는 어디로 갈까요?', deleted: false },
      deleted: false,
      deletedAt: null,
      deletableUntil: new Date(Date.now() + 300_000).toISOString(),
      reactions: [],
    })

    renderChatPanel()
    const user = userEvent.setup()
    await waitFor(() => expect(screen.getByText('카페는 어디로 갈까요?')).toBeInTheDocument())
    await user.click(screen.getByRole('button', { name: '답장' }))
    expect(screen.getByRole('status')).toHaveTextContent('서윤에게 답장')
    await user.type(screen.getByPlaceholderText('메시지를 입력하세요…'), '바다 보이는 곳이 좋아요.')
    await user.click(screen.getByRole('button', { name: '메시지 보내기' }))

    await waitFor(() => expect(sendChatMessage).toHaveBeenCalledWith(
      'token',
      '1530',
      expect.objectContaining({ replyToMessageId: 12, body: '바다 보이는 곳이 좋아요.' }),
    ))
  })

  it('replaces the current user reaction instead of stacking another one', async () => {
    const sentAt = new Date().toISOString()
    listChatHistory.mockResolvedValueOnce({
      messages: [{ id: 21, tripId: '1530', authorUserId: 2, type: 'USER_TEXT', body: '매미성 먼저 가요', clientMessageId: 'reaction-origin', sentAt }],
      nextCursor: null,
    })
    setChatReaction.mockResolvedValueOnce({
      id: 21,
      tripId: '1530',
      authorUserId: 2,
      type: 'USER_TEXT',
      body: '매미성 먼저 가요',
      clientMessageId: 'reaction-origin',
      sentAt,
      replyTo: null,
      deleted: false,
      deletedAt: null,
      deletableUntil: new Date(Date.now() + 300_000).toISOString(),
      reactions: [{ reaction: 'ACKNOWLEDGED', count: 1, memberNames: ['민준'], reactedByMe: true }],
    })

    renderChatPanel()
    const user = userEvent.setup()
    await waitFor(() => expect(screen.getByText('매미성 먼저 가요')).toBeInTheDocument())
    await user.click(screen.getByRole('button', { name: '확인했어요 반응' }))

    await waitFor(() => expect(setChatReaction).toHaveBeenCalledWith('token', '1530', 21, 'ACKNOWLEDGED'))
    expect(screen.getByRole('button', { pressed: true })).toHaveTextContent('1')
  })

  it('turns an own message into a tombstone after inline confirmation', async () => {
    const sentAt = new Date().toISOString()
    listChatHistory.mockResolvedValueOnce({
      messages: [{ id: 31, tripId: '1530', authorUserId: 1, type: 'USER_TEXT', body: '지울 메시지', clientMessageId: 'delete-origin', sentAt }],
      nextCursor: null,
    })
    deleteChatMessage.mockResolvedValueOnce({
      id: 31,
      tripId: '1530',
      authorUserId: 1,
      type: 'USER_TEXT',
      body: '삭제된 메시지입니다.',
      clientMessageId: 'delete-origin',
      sentAt,
      replyTo: null,
      deleted: true,
      deletedAt: new Date().toISOString(),
      deletableUntil: new Date(Date.now() + 300_000).toISOString(),
      reactions: [],
    })

    renderChatPanel()
    const user = userEvent.setup()
    await waitFor(() => expect(screen.getByText('지울 메시지')).toBeInTheDocument())
    await user.click(screen.getByRole('button', { name: '삭제' }))
    expect(screen.getByText('이 메시지를 삭제할까요?')).toBeInTheDocument()
    await user.click(screen.getAllByRole('button', { name: '삭제' })[1])

    await waitFor(() => expect(deleteChatMessage).toHaveBeenCalledWith('token', '1530', 31))
    expect(screen.getByText('삭제된 메시지입니다.')).toBeInTheDocument()
  })

  it('refreshes one authoritative message snapshot after a realtime change signal', async () => {
    const sentAt = new Date().toISOString()
    listChatHistory.mockResolvedValueOnce({
      messages: [{ id: 41, tripId: '1530', authorUserId: 2, type: 'USER_TEXT', body: '변경 전', clientMessageId: 'changed-message', sentAt }],
      nextCursor: null,
    })
    getChatMessage.mockResolvedValueOnce({
      id: 41,
      tripId: '1530',
      authorUserId: 2,
      type: 'USER_TEXT',
      body: '삭제된 메시지입니다.',
      clientMessageId: 'changed-message',
      sentAt,
      replyTo: null,
      deleted: true,
      deletedAt: new Date().toISOString(),
      deletableUntil: new Date(Date.now() + 300_000).toISOString(),
      reactions: [],
    })

    const { rerender } = renderChatPanel()
    await waitFor(() => expect(screen.getByText('변경 전')).toBeInTheDocument())
    rerender(
      <ChatPanel
        accessToken="token"
        tripId="1530"
        members={members}
        currentUser={currentUser}
        latestChatMessage={null}
        latestChatChange={{ messageId: 41, deletedAt: new Date().toISOString() }}
        chatConnected={true}
        chatReconnectedAt={0}
        onChatRead={() => {}}
      />,
    )

    await waitFor(() => expect(getChatMessage).toHaveBeenCalledWith('token', '1530', 41))
    expect(await screen.findByText('삭제된 메시지입니다.')).toBeInTheDocument()
  })

  it('shows at most the confirmed remote typing member name', async () => {
    listChatHistory.mockResolvedValueOnce({ messages: [], nextCursor: null })
    renderChatPanel({
      latestChatTyping: {
        memberId: 2,
        active: true,
        expiresAtUtc: new Date(Date.now() + 5000).toISOString(),
        eventSequence: 1,
      },
    })

    expect(await screen.findByRole('status', { name: '서윤님이 입력 중' })).toHaveTextContent('서윤님이 입력 중')
  })

  it('selects an active member mention and sends code-point ranges', async () => {
    listChatHistory.mockResolvedValueOnce({ messages: [], nextCursor: null })
    sendChatMessage.mockResolvedValueOnce({
      id: 30,
      tripId: '1530',
      authorUserId: 1,
      type: 'USER_TEXT',
      body: '@서윤 거제 카페 어때요?',
      clientMessageId: 'mention-1',
      sentAt: '2026-09-02T00:00:00Z',
      mentions: [{ memberId: 2, displayNameSnapshot: '서윤', startCodePoint: 0, endCodePoint: 3 }],
    })
    renderChatPanel({ sendChatTyping: vi.fn(() => true) })
    const user = userEvent.setup()
    const composer = await screen.findByPlaceholderText('메시지를 입력하세요…')
    await user.type(composer, '@서')
    await user.click(screen.getByRole('option', { name: /서윤/ }))
    await user.type(composer, '거제 카페 어때요?')
    await user.click(screen.getByRole('button', { name: '메시지 보내기' }))

    await waitFor(() => expect(sendChatMessage).toHaveBeenCalledWith(
      'token',
      '1530',
      expect.objectContaining({
        body: '@서윤 거제 카페 어때요?',
        mentions: [{ memberId: 2, startCodePoint: 0, endCodePoint: 3 }],
      }),
    ))
  })

  it('searches messages and moves to a server context result', async () => {
    listChatHistory.mockResolvedValueOnce({ messages: [], nextCursor: null })
    searchChatMessages.mockResolvedValueOnce({
      query: '거제',
      results: [{
        messageId: 40,
        sequence: 40,
        senderSnapshot: '서윤',
        createdAtUtc: '2026-09-02T00:00:00Z',
        snippet: '거제 카페에서 만나요',
        matchedRanges: [{ startCodePoint: 0, endCodePoint: 2 }],
      }],
      nextCursor: null,
      hasMore: false,
      searchSnapshotSequence: 40,
    })
    getChatMessageContext.mockResolvedValueOnce({
      messages: [{
        id: 40,
        tripId: '1530',
        authorUserId: 2,
        type: 'USER_TEXT',
        body: '거제 카페에서 만나요',
        clientMessageId: 'search-40',
        sentAt: '2026-09-02T00:00:00Z',
        mentions: [],
      }],
      nextCursor: null,
    })

    renderChatPanel()
    const user = userEvent.setup()
    await user.click(screen.getByRole('button', { name: '대화 검색' }))
    await user.type(screen.getByLabelText('대화에서 찾기'), '거제')
    await user.click(await screen.findByRole('button', { name: /서윤.*거제 카페에서 만나요/ }))

    expect(await screen.findByText('거제 카페에서 만나요')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: '검색 결과로 돌아가기' })).toBeInTheDocument()
  })
})
