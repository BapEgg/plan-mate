import { useEffect, useRef, useState } from 'react'
import { ApiError } from '../../../../../api/client'
import { listChatHistory, sendChatMessage } from '../../../../../api/chat'
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
}

function nicknameFor(members: TripMember[], userId: number | null) {
  if (userId === null) return '시스템'
  return members.find((member) => member.userId === userId)?.nickname ?? '알 수 없음'
}

function formatTime(iso: string) {
  return new Date(iso).toLocaleTimeString('ko-KR', { hour: 'numeric', minute: '2-digit' })
}

/** WP-D phase 1: 저장/history/send + 실시간 broadcast. reconnect gap 복구·unread·삭제/답장/반응은 이후 phase. */
export function ChatPanel({ accessToken, tripId, members, currentUser, latestChatMessage }: ChatPanelProps) {
  const [messages, setMessages] = useState<ChatMessage[]>([])
  const [status, setStatus] = useState<'loading' | 'success' | 'error'>('loading')
  const [draft, setDraft] = useState('')
  const [sending, setSending] = useState(false)
  const [errorMessage, setErrorMessage] = useState('')
  const lastHandledEventId = useRef<number | null>(null)

  useEffect(() => {
    let ignore = false
    listChatHistory(accessToken, tripId)
      .then((page) => {
        if (ignore) return
        setMessages([...page.messages].reverse())
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
        },
      ]
    })
  }, [latestChatMessage, tripId])

  async function handleSend(event: React.FormEvent) {
    event.preventDefault()
    const body = draft.trim()
    if (!body || sending) return
    const clientMessageId = crypto.randomUUID()
    const optimisticMessage: ChatMessage = {
      id: -Date.now(),
      tripId,
      authorUserId: currentUser?.id ?? null,
      type: 'USER_TEXT',
      body,
      clientMessageId,
      sentAt: new Date().toISOString(),
    }
    setMessages((current) => [...current, optimisticMessage])
    setDraft('')
    setSending(true)
    setErrorMessage('')
    try {
      const sent = await sendChatMessage(accessToken, tripId, { clientMessageId, body })
      setMessages((current) => current.map((message) => (message.clientMessageId === clientMessageId ? sent : message)))
    } catch (error: unknown) {
      setMessages((current) => current.filter((message) => message.clientMessageId !== clientMessageId))
      setDraft(body)
      setErrorMessage(error instanceof ApiError ? error.message : '메시지를 보내지 못했습니다.')
    } finally {
      setSending(false)
    }
  }

  return (
    <>
      <div className="trip-chat-preview" aria-label="여행방 대화" aria-live="polite">
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
                <time>{formatTime(message.sentAt)}</time>
              </div>
            </article>
          )
        })}
      </div>
      <form className="trip-chat-composer" aria-label="메시지 보내기" onSubmit={(event) => void handleSend(event)}>
        <textarea
          rows={1}
          aria-label="메시지 입력"
          placeholder="메시지를 입력하세요…"
          value={draft}
          onChange={(event) => setDraft(event.target.value)}
          onKeyDown={(event) => {
            if (event.key === 'Enter' && !event.shiftKey) {
              event.preventDefault()
              void handleSend(event)
            }
          }}
        />
        <button className={draft.trim() ? 'ready' : ''} type="submit" disabled={!draft.trim() || sending} aria-label="메시지 보내기">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.4" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
            <line x1="12" y1="19" x2="12" y2="6" />
            <polyline points="6 12 12 6 18 12" />
          </svg>
        </button>
      </form>
      {errorMessage && <p className="trip-chat-error" role="alert">{errorMessage}</p>}
    </>
  )
}
