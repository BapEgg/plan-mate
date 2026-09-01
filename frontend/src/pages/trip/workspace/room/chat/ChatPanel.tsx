import { useCallback, useEffect, useRef, useState } from 'react'
import { ApiError } from '../../../../../api/client'
import { getChatMessageByClientId, listChatHistory, listChatSince, sendChatMessage } from '../../../../../api/chat'
import type { ChatMessage } from '../../../../../api/chat'
import type { ChatMessageSentPayload } from '../../../../../api/realtime'
import type { AuthUser } from '../../../../../api/auth'
import type { TripMember } from '../../../../../api/trips'

type ChatPanelProps = {
  accessToken: string
  tripId: string
  members: TripMember[]
  currentUser: AuthUser | null
  latestChatMessage: ChatMessageSentPayload | null
  chatConnected: boolean
  chatReconnectedAt: number
}

type MessageStatus = 'sent' | 'sending' | 'unknown' | 'failed'
type DisplayMessage = ChatMessage & { status: MessageStatus }

function nicknameFor(members: TripMember[], userId: number | null) {
  if (userId === null) return '시스템'
  return members.find((member) => member.userId === userId)?.nickname ?? '알 수 없음'
}

function formatTime(iso: string) {
  return new Date(iso).toLocaleTimeString('ko-KR', { hour: 'numeric', minute: '2-digit' })
}

/**
 * WP-D phase 1+2: 저장/history/send + 실시간 broadcast + reconnect gap 복구. spec §4 "연결 상태":
 * 자동 재전송은 하지 않는다 — 전송 실패(또는 확인 불가) 뒤 사용자가 message별로 직접 다시 보낸다.
 * unread·삭제/답장/반응·typing/search/notification은 이후 phase.
 */
export function ChatPanel({ accessToken, tripId, members, currentUser, latestChatMessage, chatConnected, chatReconnectedAt }: ChatPanelProps) {
  const [messages, setMessages] = useState<DisplayMessage[]>([])
  const [status, setStatus] = useState<'loading' | 'success' | 'error'>('loading')
  const [draft, setDraft] = useState('')
  const lastHandledEventId = useRef<number | null>(null)
  const messagesRef = useRef<DisplayMessage[]>([])
  const scrollContainerRef = useRef<HTMLDivElement | null>(null)

  const scrollToBottom = useCallback(() => {
    const container = scrollContainerRef.current
    if (container) {
      container.scrollTop = container.scrollHeight
    }
  }, [])

  useEffect(() => {
    messagesRef.current = messages
    scrollToBottom()
  }, [messages, scrollToBottom])

  // The container can still be at its pre-layout zero size when the effect above
  // first runs (e.g. sibling panels like the map haven't settled the surrounding
  // grid's height yet) — a ResizeObserver re-applies the same scroll once this
  // container's real size lands, which a `[messages]`-only effect can't catch
  // since adding a message only grows scrollHeight, not this flex item's own box.
  useEffect(() => {
    const container = scrollContainerRef.current
    if (!container) return undefined
    const observer = new ResizeObserver(() => scrollToBottom())
    observer.observe(container)
    return () => observer.disconnect()
  }, [scrollToBottom])

  useEffect(() => {
    let ignore = false
    listChatHistory(accessToken, tripId)
      .then((page) => {
        if (ignore) return
        setMessages([...page.messages].reverse().map((message) => ({ ...message, status: 'sent' as const })))
        setStatus('success')
      })
      .catch(() => {
        if (ignore) return
        setStatus('error')
      })
    return () => {
      ignore = true
    }
  }, [accessToken, tripId])

  useEffect(() => {
    if (!latestChatMessage || latestChatMessage.messageId === lastHandledEventId.current) {
      return
    }
    lastHandledEventId.current = latestChatMessage.messageId
    setMessages((current) => {
      if (current.some((message) => message.clientMessageId === latestChatMessage.clientMessageId)) {
        return current
      }
      return [
        ...current,
        {
          id: latestChatMessage.messageId,
          tripId,
          authorUserId: latestChatMessage.authorUserId,
          type: latestChatMessage.type,
          body: latestChatMessage.body,
          clientMessageId: latestChatMessage.clientMessageId,
          sentAt: latestChatMessage.sentAt,
          status: 'sent',
        },
      ]
    })
  }, [latestChatMessage, tripId])

  // Reconnect recovery (spec §4 "연결 상태"): fill the REST gap for whatever the trip topic
  // missed while disconnected, and resolve any message whose send outcome was left UNKNOWN
  // by a disconnect racing its response.
  useEffect(() => {
    if (!chatReconnectedAt) return
    let cancelled = false
    const current = messagesRef.current

    const sinceId = current.reduce((max, message) => (message.status === 'sent' && message.id > max ? message.id : max), 0)
    listChatSince(accessToken, tripId, sinceId)
      .then((page) => {
        if (cancelled || page.messages.length === 0) return
        setMessages((latest) => {
          const knownIds = new Set(latest.filter((message) => message.status === 'sent').map((message) => message.id))
          const toAdd = page.messages.filter((message) => !knownIds.has(message.id)).map((message) => ({ ...message, status: 'sent' as const }))
          return toAdd.length ? [...latest, ...toAdd] : latest
        })
      })
      .catch(() => {})

    current.filter((message) => message.status === 'unknown').forEach((unknownMessage) => {
      getChatMessageByClientId(accessToken, tripId, unknownMessage.clientMessageId)
        .then((found) => {
          if (cancelled) return
          setMessages((latest) => latest.map((message) =>
            message.clientMessageId === unknownMessage.clientMessageId ? { ...found, status: 'sent' } : message))
        })
        .catch((error: unknown) => {
          if (cancelled || !(error instanceof ApiError) || error.status !== 404) return
          setMessages((latest) => latest.map((message) =>
            message.clientMessageId === unknownMessage.clientMessageId ? { ...message, status: 'failed' } : message))
        })
    })

    return () => {
      cancelled = true
    }
  }, [chatReconnectedAt, accessToken, tripId])

  async function attemptSend(clientMessageId: string, body: string) {
    setMessages((current) => current.map((message) =>
      message.clientMessageId === clientMessageId ? { ...message, status: 'sending' } : message))
    try {
      const sent = await sendChatMessage(accessToken, tripId, { clientMessageId, body })
      setMessages((current) => current.map((message) =>
        message.clientMessageId === clientMessageId ? { ...sent, status: 'sent' } : message))
    } catch {
      // Ambiguous failure (network drop mid-request) — verify with the server rather than
      // assuming it never landed, per spec: only a confirmed-unsaved message gets a resend offer.
      try {
        const found = await getChatMessageByClientId(accessToken, tripId, clientMessageId)
        setMessages((current) => current.map((message) =>
          message.clientMessageId === clientMessageId ? { ...found, status: 'sent' } : message))
      } catch (verifyError: unknown) {
        const resolved: MessageStatus = verifyError instanceof ApiError && verifyError.status === 404 ? 'failed' : 'unknown'
        setMessages((current) => current.map((message) =>
          message.clientMessageId === clientMessageId ? { ...message, status: resolved } : message))
      }
    }
  }

  async function handleSend(event: React.FormEvent) {
    event.preventDefault()
    const body = draft.trim()
    if (!body || !chatConnected) return
    const clientMessageId = crypto.randomUUID()
    const optimisticMessage: DisplayMessage = {
      id: -Date.now(),
      tripId,
      authorUserId: currentUser?.id ?? null,
      type: 'USER_TEXT',
      body,
      clientMessageId,
      sentAt: new Date().toISOString(),
      status: 'sending',
    }
    setMessages((current) => [...current, optimisticMessage])
    setDraft('')
    void attemptSend(clientMessageId, body)
  }

  return (
    <>
      <div className="trip-chat-preview" aria-label="여행방 대화" aria-live="polite" ref={scrollContainerRef}>
        {status === 'loading' && <p className="trip-chat-empty">대화를 불러오는 중입니다…</p>}
        {status === 'error' && <p className="trip-chat-empty" role="alert">대화를 불러오지 못했습니다.</p>}
        {status === 'success' && messages.length === 0 && (
          <p className="trip-chat-empty">아직 대화가 없습니다. 첫 메시지를 보내보세요.</p>
        )}
        {status === 'success' && messages.map((message) => {
          const mine = message.authorUserId !== null && message.authorUserId === currentUser?.id
          return (
            <article className={`chat-message-row${mine ? ' mine' : ''}`} key={message.clientMessageId}>
              {!mine && <span className="preview-avatar blue" aria-hidden="true">{nicknameFor(members, message.authorUserId).slice(0, 1)}</span>}
              <div>
                {!mine && <strong>{nicknameFor(members, message.authorUserId)}</strong>}
                <p>{message.body}</p>
                {message.status === 'sent' && <time>{formatTime(message.sentAt)}</time>}
                {message.status === 'sending' && <time className="chat-message-status">보내는 중…</time>}
                {message.status === 'unknown' && <time className="chat-message-status">전송 확인 중…</time>}
                {message.status === 'failed' && (
                  <span className="chat-message-status failed">
                    전송 실패
                    <button type="button" onClick={() => void attemptSend(message.clientMessageId, message.body)}>다시 보내기</button>
                  </span>
                )}
              </div>
            </article>
          )
        })}
      </div>
      {!chatConnected && (
        <p className="trip-chat-connection-notice" role="status">
          실시간 연결이 끊겼습니다. 대화 기록은 계속 볼 수 있지만, 연결이 복구될 때까지 새 메시지를 보낼 수 없습니다.
        </p>
      )}
      <form className="trip-chat-composer" aria-label="메시지 보내기" onSubmit={(event) => void handleSend(event)}>
        <textarea
          rows={1}
          aria-label="메시지 입력"
          placeholder="메시지를 입력하세요…"
          value={draft}
          disabled={!chatConnected}
          onChange={(event) => setDraft(event.target.value)}
          onKeyDown={(event) => {
            if (event.key === 'Enter' && !event.shiftKey) {
              event.preventDefault()
              void handleSend(event)
            }
          }}
        />
        <button className={draft.trim() ? 'ready' : ''} type="submit" disabled={!draft.trim() || !chatConnected} aria-label="메시지 보내기">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.4" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
            <line x1="12" y1="19" x2="12" y2="6" />
            <polyline points="6 12 12 6 18 12" />
          </svg>
        </button>
      </form>
    </>
  )
}
