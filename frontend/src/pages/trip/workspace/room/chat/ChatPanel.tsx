import { useCallback, useEffect, useRef, useState } from 'react'
import type { AuthUser } from '../../../../../api/auth'
import {
  deleteChatMessage,
  getChatMessage,
  getChatMessageContext,
  getChatMessageByClientId,
  listChatHistory,
  listChatSince,
  markChatRead,
  removeChatReaction,
  searchChatMessages,
  sendChatMessage,
  setChatReaction,
} from '../../../../../api/chat'
import type { ChatMention, ChatMentionDraft, ChatMessage, ChatReactionType, ChatSearchResult } from '../../../../../api/chat'
import { ApiError } from '../../../../../api/client'
import type { ChatMessageChangedPayload, ChatMessageSentPayload, ChatTypingChangedPayload } from '../../../../../api/realtime'
import type { TripMember } from '../../../../../api/trips'

type ChatPanelProps = {
  accessToken: string
  tripId: string
  members: TripMember[]
  currentUser: AuthUser | null
  latestChatMessage: ChatMessageSentPayload | null
  latestChatChange: ChatMessageChangedPayload | null
  latestChatTyping?: ChatTypingChangedPayload | null
  chatConnected: boolean
  chatReconnectedAt: number
  onChatRead: () => void
  sendChatTyping?: (state: 'STARTED' | 'HEARTBEAT' | 'STOPPED', clientSessionId: string, clientEventId: string) => boolean
}

type MessageStatus = 'sent' | 'sending' | 'unknown' | 'failed'
type DisplayMessage = ChatMessage & { status: MessageStatus }
type LocalMention = {
  memberId: number
  displayNameSnapshot: string
  startUtf16: number
  endUtf16: number
}

type MentionTrigger = { start: number; end: number; query: string }

const reactionCopy: Record<ChatReactionType, { icon: string; label: string }> = {
  LIKE: { icon: '♥', label: '좋아요' },
  ACKNOWLEDGED: { icon: '✓', label: '확인했어요' },
}

const noopTyping = () => false

function nicknameFor(members: TripMember[], userId: number | null) {
  if (userId === null) return '시스템'
  return members.find((member) => member.userId === userId)?.nickname ?? '알 수 없음'
}

function formatTime(iso: string) {
  return new Date(iso).toLocaleTimeString('ko-KR', { hour: 'numeric', minute: '2-digit' })
}

function canStillDelete(message: DisplayMessage, currentUserId: number | undefined) {
  if (message.deleted || message.status !== 'sent' || message.authorUserId !== currentUserId) return false
  const fallback = new Date(message.sentAt).getTime() + 5 * 60 * 1000
  const deadline = message.deletableUntil ? new Date(message.deletableUntil).getTime() : fallback
  return Date.now() <= deadline
}

function toDisplayMessage(message: ChatMessage): DisplayMessage {
  return {
    ...message,
    replyTo: message.replyTo ?? null,
    deleted: message.deleted ?? false,
    deletedAt: message.deletedAt ?? null,
    deletableUntil: message.deletableUntil
      ?? new Date(new Date(message.sentAt).getTime() + 5 * 60 * 1000).toISOString(),
    reactions: message.reactions ?? [],
    mentions: message.mentions ?? [],
    status: 'sent',
  }
}

function eventToMessage(payload: ChatMessageSentPayload, tripId: string): DisplayMessage {
  return {
    id: payload.messageId,
    tripId,
    authorUserId: payload.authorUserId,
    type: payload.type,
    body: payload.body,
    clientMessageId: payload.clientMessageId,
    sentAt: payload.sentAt,
    replyTo: payload.replyToMessageId === null ? null : {
      messageId: payload.replyToMessageId,
      authorUserId: payload.replyAuthorUserId,
      body: payload.replyBody ?? '',
      deleted: payload.replyDeleted,
    },
    deleted: false,
    deletedAt: null,
    deletableUntil: new Date(new Date(payload.sentAt).getTime() + 5 * 60 * 1000).toISOString(),
    reactions: [],
    mentions: payload.mentions ?? [],
    status: 'sent',
  }
}

function mentionTriggerAt(value: string, cursor: number): MentionTrigger | null {
  const before = value.slice(0, cursor)
  const match = before.match(/(?:^|\s)@([^\s@]*)$/u)
  if (!match) return null
  const start = cursor - match[1].length - 1
  return { start, end: cursor, query: match[1] }
}

function shiftMentions(mentions: LocalMention[], previous: string, next: string) {
  let prefix = 0
  while (prefix < previous.length && prefix < next.length && previous[prefix] === next[prefix]) prefix += 1
  let suffix = 0
  while (
    suffix < previous.length - prefix
    && suffix < next.length - prefix
    && previous[previous.length - 1 - suffix] === next[next.length - 1 - suffix]
  ) suffix += 1
  const previousChangeEnd = previous.length - suffix
  const nextChangeEnd = next.length - suffix
  const delta = nextChangeEnd - previousChangeEnd
  return mentions.flatMap((mention) => {
    if (mention.endUtf16 <= prefix) return [mention]
    if (mention.startUtf16 >= previousChangeEnd) {
      return [{ ...mention, startUtf16: mention.startUtf16 + delta, endUtf16: mention.endUtf16 + delta }]
    }
    return []
  })
}

function toMentionDrafts(body: string, mentions: LocalMention[], leadingTrim: number): ChatMentionDraft[] {
  return mentions.flatMap((mention) => {
    const start = mention.startUtf16 - leadingTrim
    const end = mention.endUtf16 - leadingTrim
    if (start < 0 || end > body.length || body.slice(start, end) !== `@${mention.displayNameSnapshot}`) return []
    return [{
      memberId: mention.memberId,
      startCodePoint: Array.from(body.slice(0, start)).length,
      endCodePoint: Array.from(body.slice(0, end)).length,
    }]
  })
}

function renderMentionedBody(body: string, mentions: ChatMention[], currentUserId?: number) {
  if (mentions.length === 0) return body
  const points = Array.from(body)
  const sorted = [...mentions].sort((left, right) => left.startCodePoint - right.startCodePoint)
  const result: React.ReactNode[] = []
  let cursor = 0
  sorted.forEach((mention, index) => {
    if (mention.startCodePoint < cursor || mention.endCodePoint > points.length) return
    if (cursor < mention.startCodePoint) result.push(points.slice(cursor, mention.startCodePoint).join(''))
    result.push(
      <span
        className={`chat-mention${mention.memberId === currentUserId ? ' mine' : ''}`}
        key={`${mention.memberId}-${mention.startCodePoint}-${index}`}
        aria-label={`${mention.displayNameSnapshot}님 언급`}
      >
        {points.slice(mention.startCodePoint, mention.endCodePoint).join('')}
      </span>,
    )
    cursor = mention.endCodePoint
  })
  if (cursor < points.length) result.push(points.slice(cursor).join(''))
  return result
}

function renderSearchSnippet(result: ChatSearchResult) {
  const points = Array.from(result.snippet)
  const match = result.matchedRanges[0]
  if (!match) return result.snippet
  return <>{points.slice(0, match.startCodePoint).join('')}<mark>{points.slice(match.startCodePoint, match.endCodePoint).join('')}</mark>{points.slice(match.endCodePoint).join('')}</>
}

/**
 * WP-D phase 1-4: 저장/history/send, reconnect gap, unread, 답장·반응·5분 내 전체 삭제.
 * 새 메시지는 즉시 merge하고 삭제·반응처럼 사용자별 응답이 달라지는 변경은 REST snapshot을 다시 읽는다.
 */
export function ChatPanel({
  accessToken,
  tripId,
  members,
  currentUser,
  latestChatMessage,
  latestChatChange,
  latestChatTyping = null,
  chatConnected,
  chatReconnectedAt,
  onChatRead,
  sendChatTyping = noopTyping,
}: ChatPanelProps) {
  const [messages, setMessages] = useState<DisplayMessage[]>([])
  const [status, setStatus] = useState<'loading' | 'success' | 'error'>('loading')
  const [draft, setDraft] = useState('')
  const [mentionDrafts, setMentionDrafts] = useState<LocalMention[]>([])
  const [mentionTrigger, setMentionTrigger] = useState<MentionTrigger | null>(null)
  const [activeMentionIndex, setActiveMentionIndex] = useState(0)
  const [typingMembers, setTypingMembers] = useState<Record<number, { expiresAt: string; sequence: number }>>({})
  const [searchOpen, setSearchOpen] = useState(false)
  const [searchQuery, setSearchQuery] = useState('')
  const [searchResults, setSearchResults] = useState<ChatSearchResult[]>([])
  const [searchCursor, setSearchCursor] = useState<string | null>(null)
  const [searchStatus, setSearchStatus] = useState<'idle' | 'loading' | 'success' | 'error'>('idle')
  const [searchComposing, setSearchComposing] = useState(false)
  const [searchStale, setSearchStale] = useState(false)
  const [searchRevision, setSearchRevision] = useState(0)
  const [returnToSearch, setReturnToSearch] = useState(false)
  const [highlightMessageId, setHighlightMessageId] = useState<number | null>(null)
  const [replyTarget, setReplyTarget] = useState<DisplayMessage | null>(null)
  const [deleteTargetId, setDeleteTargetId] = useState<number | null>(null)
  const [busyMessageId, setBusyMessageId] = useState<number | null>(null)
  const [actionError, setActionError] = useState('')
  const lastHandledEventId = useRef<number | null>(null)
  const messagesRef = useRef<DisplayMessage[]>([])
  const scrollContainerRef = useRef<HTMLDivElement | null>(null)
  const composerRef = useRef<HTMLTextAreaElement | null>(null)
  const searchInputRef = useRef<HTMLInputElement | null>(null)
  const composingRef = useRef(false)
  const typingActiveRef = useRef(false)
  const typingHeartbeatRef = useRef<number | null>(null)
  const typingBlurRef = useRef<number | null>(null)
  const typingSessionIdRef = useRef(crypto.randomUUID())
  const onChatReadRef = useRef(onChatRead)

  const mentionCandidates = members.filter((member) => (
    member.userId !== currentUser?.id
    && (mentionTrigger?.query ? member.nickname.toLocaleLowerCase('ko-KR').includes(mentionTrigger.query.toLocaleLowerCase('ko-KR')) : true)
  ))

  const typingNames = Object.keys(typingMembers)
    .map(Number)
    .filter((memberId) => memberId !== currentUser?.id)
    .map((memberId) => nicknameFor(members, memberId))

  function typingCopy() {
    if (typingNames.length === 1) return `${typingNames[0]}님이 입력 중`
    if (typingNames.length === 2) return `${typingNames[0]}님과 ${typingNames[1]}님이 입력 중`
    if (typingNames.length >= 3) return '여러 명이 입력 중'
    return ''
  }

  const stopTyping = useCallback(() => {
    if (typingBlurRef.current !== null) {
      window.clearTimeout(typingBlurRef.current)
      typingBlurRef.current = null
    }
    if (typingHeartbeatRef.current !== null) {
      window.clearInterval(typingHeartbeatRef.current)
      typingHeartbeatRef.current = null
    }
    if (!typingActiveRef.current) return
    typingActiveRef.current = false
    sendChatTyping('STOPPED', typingSessionIdRef.current, crypto.randomUUID())
  }, [sendChatTyping])

  const startTyping = useCallback(() => {
    if (!chatConnected || typingActiveRef.current) return
    typingActiveRef.current = true
    sendChatTyping('STARTED', typingSessionIdRef.current, crypto.randomUUID())
    typingHeartbeatRef.current = window.setInterval(() => {
      sendChatTyping('HEARTBEAT', typingSessionIdRef.current, crypto.randomUUID())
    }, 3000)
  }, [chatConnected, sendChatTyping])

  useEffect(() => {
    onChatReadRef.current = onChatRead
  })

  useEffect(() => () => stopTyping(), [stopTyping])

  useEffect(() => {
    const handleVisibility = () => {
      if (document.hidden) stopTyping()
      else if (draft.length > 0) startTyping()
    }
    document.addEventListener('visibilitychange', handleVisibility)
    return () => document.removeEventListener('visibilitychange', handleVisibility)
  }, [draft.length, startTyping, stopTyping])

  useEffect(() => {
    if (!chatConnected) {
      stopTyping()
    } else if (draft.length > 0) {
      startTyping()
    }
  }, [chatConnected, chatReconnectedAt, draft.length, startTyping, stopTyping])

  useEffect(() => {
    if (!latestChatTyping || latestChatTyping.memberId === currentUser?.id) return undefined
    const { memberId, active, expiresAtUtc, eventSequence } = latestChatTyping
    const updateTimer = window.setTimeout(() => {
      setTypingMembers((current) => {
        const existing = current[memberId]
        if (existing && existing.sequence >= eventSequence) return current
        if (!active || !expiresAtUtc) {
          const next = { ...current }
          delete next[memberId]
          return next
        }
        return { ...current, [memberId]: { expiresAt: expiresAtUtc, sequence: eventSequence } }
      })
    }, 0)
    if (!active || !expiresAtUtc) return () => window.clearTimeout(updateTimer)
    const timeout = Math.max(0, new Date(expiresAtUtc).getTime() - Date.now())
    const timer = window.setTimeout(() => {
      setTypingMembers((current) => {
        const existing = current[memberId]
        if (!existing || existing.sequence !== eventSequence) return current
        const next = { ...current }
        delete next[memberId]
        return next
      })
    }, timeout + 50)
    return () => {
      window.clearTimeout(updateTimer)
      window.clearTimeout(timer)
    }
  }, [currentUser?.id, latestChatTyping])

  useEffect(() => {
    if (!searchOpen || searchComposing) return undefined
    const normalized = searchQuery.trim().normalize('NFC')
    if (Array.from(normalized).length < 2) {
      return undefined
    }
    let cancelled = false
    const timer = window.setTimeout(() => {
      setSearchStatus('loading')
      searchChatMessages(accessToken, tripId, normalized)
        .then((response) => {
          if (cancelled) return
          setSearchResults(response.results)
          setSearchCursor(response.nextCursor)
          setSearchStatus('success')
          setSearchStale(false)
        })
        .catch(() => {
          if (!cancelled) setSearchStatus('error')
        })
    }, 300)
    return () => {
      cancelled = true
      window.clearTimeout(timer)
    }
  }, [accessToken, searchComposing, searchOpen, searchQuery, searchRevision, tripId])

  const scrollToBottom = useCallback(() => {
    const container = scrollContainerRef.current
    if (container) container.scrollTop = container.scrollHeight
  }, [])

  const replaceMessage = useCallback((updated: ChatMessage) => {
    setMessages((current) => current.map((message) =>
      message.id === updated.id ? toDisplayMessage(updated) : message))
  }, [])

  useEffect(() => {
    messagesRef.current = messages
    scrollToBottom()
  }, [messages, scrollToBottom])

  useEffect(() => {
    const container = scrollContainerRef.current
    if (!container) return undefined
    const observer = new ResizeObserver(() => scrollToBottom())
    observer.observe(container)
    return () => observer.disconnect()
  }, [scrollToBottom, searchOpen])

  const markLatestRead = useCallback((messageId: number) => {
    markChatRead(accessToken, tripId, messageId)
      .then(() => onChatReadRef.current())
      .catch(() => {})
  }, [accessToken, tripId])

  useEffect(() => {
    let ignore = false
    listChatHistory(accessToken, tripId)
      .then((page) => {
        if (ignore) return
        setMessages([...page.messages].reverse().map(toDisplayMessage))
        setStatus('success')
        if (page.messages.length > 0) markLatestRead(Math.max(...page.messages.map((message) => message.id)))
      })
      .catch(() => {
        if (!ignore) setStatus('error')
      })
    return () => {
      ignore = true
    }
  }, [accessToken, tripId, markLatestRead])

  useEffect(() => {
    if (!latestChatMessage || latestChatMessage.messageId === lastHandledEventId.current) return
    lastHandledEventId.current = latestChatMessage.messageId
    setMessages((current) => {
      if (current.some((message) => message.clientMessageId === latestChatMessage.clientMessageId)) return current
      return [...current, eventToMessage(latestChatMessage, tripId)]
    })
    const staleTimer = searchOpen && searchQuery.trim().length >= 2
      ? window.setTimeout(() => setSearchStale(true), 0)
      : null
    markLatestRead(latestChatMessage.messageId)
    return () => {
      if (staleTimer !== null) window.clearTimeout(staleTimer)
    }
  }, [latestChatMessage, tripId, markLatestRead, searchOpen, searchQuery])

  useEffect(() => {
    if (!latestChatChange) return
    let cancelled = false
    getChatMessage(accessToken, tripId, latestChatChange.messageId)
      .then((message) => {
        if (!cancelled) {
          replaceMessage(message)
          if (message.deleted) {
            setSearchResults((current) => current.filter((result) => result.messageId !== message.id))
          }
        }
      })
      .catch(() => {})
    return () => {
      cancelled = true
    }
  }, [latestChatChange, accessToken, tripId, replaceMessage])

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
          const toAdd = page.messages.filter((message) => !knownIds.has(message.id)).map(toDisplayMessage)
          return toAdd.length ? [...latest, ...toAdd] : latest
        })
      })
      .catch(() => {})

    current.filter((message) => message.status === 'unknown').forEach((unknownMessage) => {
      getChatMessageByClientId(accessToken, tripId, unknownMessage.clientMessageId)
        .then((found) => {
          if (!cancelled) replaceMessage(found)
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
  }, [chatReconnectedAt, accessToken, tripId, replaceMessage])

  async function attemptSend(clientMessageId: string, body: string, replyToMessageId?: number, mentions?: ChatMentionDraft[]) {
    setMessages((current) => current.map((message) =>
      message.clientMessageId === clientMessageId ? { ...message, status: 'sending' } : message))
    try {
      const sent = await sendChatMessage(accessToken, tripId, { clientMessageId, body, replyToMessageId, mentions })
      setMessages((current) => current.map((message) =>
        message.clientMessageId === clientMessageId ? toDisplayMessage(sent) : message))
    } catch {
      try {
        const found = await getChatMessageByClientId(accessToken, tripId, clientMessageId)
        replaceMessage(found)
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
    stopTyping()
    const leadingTrim = draft.length - draft.trimStart().length
    const mentions = toMentionDrafts(body, mentionDrafts, leadingTrim)
    const optimisticMentions: ChatMention[] = mentions.map((mention) => ({
      ...mention,
      displayNameSnapshot: mentionDrafts.find((candidate) => candidate.memberId === mention.memberId
        && Array.from(body.slice(0, candidate.startUtf16 - leadingTrim)).length === mention.startCodePoint)?.displayNameSnapshot ?? nicknameFor(members, mention.memberId),
    }))
    const clientMessageId = crypto.randomUUID()
    const replyTo = replyTarget ? {
      messageId: replyTarget.id,
      authorUserId: replyTarget.authorUserId,
      body: replyTarget.body,
      deleted: replyTarget.deleted,
    } : null
    const optimisticMessage: DisplayMessage = {
      id: -Date.now(),
      tripId,
      authorUserId: currentUser?.id ?? null,
      type: 'USER_TEXT',
      body,
      clientMessageId,
      sentAt: new Date().toISOString(),
      replyTo,
      deleted: false,
      deletedAt: null,
      deletableUntil: new Date(Date.now() + 5 * 60 * 1000).toISOString(),
      reactions: [],
      mentions: optimisticMentions,
      status: 'sending',
    }
    setMessages((current) => [...current, optimisticMessage])
    setDraft('')
    setMentionDrafts([])
    setMentionTrigger(null)
    setReplyTarget(null)
    setActionError('')
    void attemptSend(clientMessageId, body, replyTo?.messageId, mentions)
  }

  async function handleDelete(messageId: number) {
    setBusyMessageId(messageId)
    setActionError('')
    try {
      replaceMessage(await deleteChatMessage(accessToken, tripId, messageId))
      setDeleteTargetId(null)
      if (replyTarget?.id === messageId) setReplyTarget(null)
    } catch (error) {
      setActionError(error instanceof ApiError && error.code === 'MESSAGE_DELETE_WINDOW_EXPIRED'
        ? '보낸 지 5분이 지나 삭제할 수 없습니다.'
        : '메시지를 삭제하지 못했습니다. 잠시 후 다시 시도해 주세요.')
    } finally {
      setBusyMessageId(null)
    }
  }

  async function handleReaction(message: DisplayMessage, reaction: ChatReactionType) {
    setBusyMessageId(message.id)
    setActionError('')
    try {
      const selected = message.reactions.some((summary) => summary.reaction === reaction && summary.reactedByMe)
      const updated = selected
        ? await removeChatReaction(accessToken, tripId, message.id)
        : await setChatReaction(accessToken, tripId, message.id, reaction)
      replaceMessage(updated)
    } catch {
      setActionError('반응을 남기지 못했습니다. 잠시 후 다시 시도해 주세요.')
    } finally {
      setBusyMessageId(null)
    }
  }

  function beginReply(message: DisplayMessage) {
    setReplyTarget(message)
    setDeleteTargetId(null)
    setActionError('')
    requestAnimationFrame(() => composerRef.current?.focus())
  }

  function handleDraftChange(value: string, cursor: number) {
    setMentionDrafts((current) => shiftMentions(current, draft, value))
    setDraft(value)
    const trigger = mentionTriggerAt(value, cursor)
    setMentionTrigger(trigger)
    setActiveMentionIndex(0)
    if (value.length > 0) startTyping()
    else stopTyping()
  }

  function handleSearchQueryChange(value: string) {
    setSearchQuery(value)
    if (Array.from(value.trim().normalize('NFC')).length < 2) {
      setSearchResults([])
      setSearchCursor(null)
      setSearchStatus('idle')
      setSearchStale(false)
    }
  }

  function selectMention(member: TripMember) {
    if (!mentionTrigger) return
    const visible = `@${member.nickname}`
    const next = `${draft.slice(0, mentionTrigger.start)}${visible} ${draft.slice(mentionTrigger.end)}`
    const shifted = shiftMentions(mentionDrafts, draft, next)
    const inserted: LocalMention = {
      memberId: member.userId,
      displayNameSnapshot: member.nickname,
      startUtf16: mentionTrigger.start,
      endUtf16: mentionTrigger.start + visible.length,
    }
    setMentionDrafts([...shifted, inserted])
    setDraft(next)
    setMentionTrigger(null)
    const cursor = inserted.endUtf16 + 1
    requestAnimationFrame(() => {
      composerRef.current?.focus()
      composerRef.current?.setSelectionRange(cursor, cursor)
    })
  }

  async function loadMoreSearchResults() {
    if (!searchCursor || searchStatus === 'loading') return
    setSearchStatus('loading')
    try {
      const response = await searchChatMessages(accessToken, tripId, searchQuery.trim(), searchCursor)
      setSearchResults((current) => [...current, ...response.results])
      setSearchCursor(response.nextCursor)
      setSearchStatus('success')
    } catch {
      setSearchStatus('error')
    }
  }

  async function openSearchResult(result: ChatSearchResult) {
    setActionError('')
    try {
      if (!messagesRef.current.some((message) => message.id === result.messageId)) {
        const page = await getChatMessageContext(accessToken, tripId, result.messageId)
        setMessages((current) => {
          const byId = new Map(current.map((message) => [message.id, message]))
          page.messages.forEach((message) => byId.set(message.id, toDisplayMessage(message)))
          return [...byId.values()].sort((left, right) => left.id - right.id)
        })
      }
      setSearchOpen(false)
      setReturnToSearch(true)
      setHighlightMessageId(result.messageId)
      window.setTimeout(() => {
        const target = document.getElementById(`chat-message-${result.messageId}`)
        target?.scrollIntoView({ block: 'center' })
        target?.focus({ preventScroll: true })
      }, 50)
    } catch {
      setActionError('이 메시지를 볼 수 없습니다.')
    }
  }

  function reopenSearch() {
    setSearchOpen(true)
    setReturnToSearch(false)
    setHighlightMessageId(null)
    requestAnimationFrame(() => searchInputRef.current?.focus())
  }

  function closeSearch() {
    setSearchOpen(false)
    setReturnToSearch(false)
    setHighlightMessageId(null)
  }

  return (
    <>
      <div className="trip-chat-tools">
        {returnToSearch ? (
          <button type="button" onClick={reopenSearch}>검색 결과로 돌아가기</button>
        ) : (
          <button
            type="button"
            aria-expanded={searchOpen}
            onClick={() => {
              if (searchOpen) closeSearch()
              else reopenSearch()
            }}
          >
            {searchOpen ? '검색 닫기' : '대화 검색'}
          </button>
        )}
      </div>
      {searchOpen ? (
        <section className="trip-chat-search" aria-label="대화 검색">
          <label htmlFor="trip-chat-search-input">대화에서 찾기</label>
          <div className="trip-chat-search-field">
            <input
              ref={searchInputRef}
              id="trip-chat-search-input"
              name="chat-search"
              type="search"
              autoComplete="off"
              value={searchQuery}
              placeholder="장소나 약속 검색…"
              onChange={(event) => handleSearchQueryChange(event.target.value)}
              onCompositionStart={() => setSearchComposing(true)}
              onCompositionEnd={() => setSearchComposing(false)}
              onKeyDown={(event) => {
                if (event.key === 'Escape') closeSearch()
              }}
            />
            <button type="button" aria-label="검색 닫기" onClick={closeSearch}>×</button>
          </div>
          {Array.from(searchQuery.trim()).length < 2 && <p className="trip-chat-search-help">두 글자 이상 입력해 주세요.</p>}
          {searchStale && (
            <button className="trip-chat-search-refresh" type="button" onClick={() => setSearchRevision((current) => current + 1)}>
              새 메시지가 있어요. 다시 검색
            </button>
          )}
          <div className="trip-chat-search-results">
            {searchStatus === 'loading' && <p role="status">대화를 찾고 있습니다…</p>}
            {searchStatus === 'error' && <p role="status">대화를 검색하지 못했어요. 검색어를 다시 입력해 주세요.</p>}
            {searchStatus === 'success' && searchResults.length === 0 && <p role="status">이 대화에서는 찾지 못했어요.</p>}
            {searchResults.map((result) => (
              <button key={result.messageId} type="button" onClick={() => void openSearchResult(result)}>
                <span><strong>{result.senderSnapshot}</strong><time>{formatTime(result.createdAtUtc)}</time></span>
                <span>{renderSearchSnippet(result)}</span>
              </button>
            ))}
            {searchCursor && searchStatus !== 'loading' && (
              <button className="trip-chat-search-more" type="button" onClick={() => void loadMoreSearchResults()}>더 보기</button>
            )}
          </div>
        </section>
      ) : (
      <div className="trip-chat-preview" aria-label="여행방 대화" aria-live="polite" ref={scrollContainerRef}>
        {status === 'loading' && <p className="trip-chat-empty">대화를 불러오는 중입니다…</p>}
        {status === 'error' && <p className="trip-chat-empty" role="alert">대화를 불러오지 못했습니다.</p>}
        {status === 'success' && messages.length === 0 && (
          <p className="trip-chat-empty">아직 대화가 없습니다. 여행 이야기를 시작해 보세요.</p>
        )}
        {status === 'success' && messages.map((message) => {
          const mine = message.authorUserId !== null && message.authorUserId === currentUser?.id
          const busy = busyMessageId === message.id
          return (
            <article
              className={`chat-message-row${mine ? ' mine' : ''}${message.deleted ? ' deleted' : ''}${highlightMessageId === message.id ? ' search-highlight' : ''}`}
              id={`chat-message-${message.id}`}
              key={message.clientMessageId}
              tabIndex={highlightMessageId === message.id ? -1 : undefined}
            >
              {!mine && <span className="preview-avatar blue" aria-hidden="true">{nicknameFor(members, message.authorUserId).slice(0, 1)}</span>}
              <div className="chat-message-content">
                {!mine && <strong>{nicknameFor(members, message.authorUserId)}</strong>}
                <div className="chat-message-bubble">
                  {message.replyTo && (
                    <div className={`chat-reply-preview${message.replyTo.deleted ? ' deleted' : ''}`}>
                      <strong>{nicknameFor(members, message.replyTo.authorUserId)}</strong>
                      <span>{message.replyTo.body}</span>
                    </div>
                  )}
                  <span className="chat-message-body">{renderMentionedBody(message.body, message.mentions, currentUser?.id)}</span>
                </div>
                {message.mentions.some((mention) => mention.memberId === currentUser?.id) && !message.deleted && (
                  <span className="chat-mentioned-me">나를 언급했어요</span>
                )}
                {message.reactions.length > 0 && !message.deleted && (
                  <div className="chat-reaction-summary" aria-label="메시지 반응">
                    {message.reactions.map((summary) => (
                      <button
                        aria-label={`${reactionCopy[summary.reaction].label} ${summary.count}명: ${summary.memberNames.join(', ')}`}
                        aria-pressed={summary.reactedByMe}
                        className={summary.reactedByMe ? 'selected' : ''}
                        disabled={busy}
                        key={summary.reaction}
                        type="button"
                        onClick={() => void handleReaction(message, summary.reaction)}
                      >
                        <span aria-hidden="true">{reactionCopy[summary.reaction].icon}</span>
                        <span>{summary.count}</span>
                        <span className="chat-reaction-names" role="tooltip">{summary.memberNames.join(', ')}</span>
                      </button>
                    ))}
                  </div>
                )}
                {!message.deleted && message.status === 'sent' && (
                  <div className="chat-message-actions" aria-label="메시지 동작">
                    <button type="button" disabled={busy} onClick={() => beginReply(message)}>답장</button>
                    {(Object.keys(reactionCopy) as ChatReactionType[]).map((reaction) => (
                      <button
                        aria-label={`${reactionCopy[reaction].label} 반응`}
                        disabled={busy}
                        key={reaction}
                        type="button"
                        onClick={() => void handleReaction(message, reaction)}
                      >
                        {reactionCopy[reaction].label}
                      </button>
                    ))}
                    {canStillDelete(message, currentUser?.id) && (
                      <button className="danger" type="button" disabled={busy} onClick={() => setDeleteTargetId(message.id)}>삭제</button>
                    )}
                  </div>
                )}
                {deleteTargetId === message.id && (
                  <div className="chat-delete-confirm" role="status">
                    <span>이 메시지를 삭제할까요?</span>
                    <button type="button" disabled={busy} onClick={() => void handleDelete(message.id)}>삭제</button>
                    <button type="button" disabled={busy} onClick={() => setDeleteTargetId(null)}>취소</button>
                  </div>
                )}
                {message.status === 'sent' && <time>{formatTime(message.sentAt)}</time>}
                {message.status === 'sending' && <time className="chat-message-status">보내는 중…</time>}
                {message.status === 'unknown' && <time className="chat-message-status">전송 확인 중…</time>}
                {message.status === 'failed' && (
                  <span className="chat-message-status failed">
                    전송 실패
                    <button type="button" onClick={() => void attemptSend(
                      message.clientMessageId,
                      message.body,
                      message.replyTo?.messageId,
                      message.mentions.map(({ memberId, startCodePoint, endCodePoint }) => ({ memberId, startCodePoint, endCodePoint })),
                    )}>다시 보내기</button>
                  </span>
                )}
              </div>
            </article>
          )
        })}
      </div>
      )}
      {!chatConnected && (
        <p className="trip-chat-connection-notice" role="status">
          연결을 다시 확인하고 있습니다. 대화 기록은 계속 볼 수 있습니다.
        </p>
      )}
      <p
        className="trip-chat-typing"
        role={typingNames.length > 0 ? 'status' : undefined}
        aria-live={typingNames.length > 0 ? 'polite' : undefined}
        aria-label={typingNames.length > 0 ? typingCopy() : undefined}
      >
        {chatConnected && typingNames.length > 0 ? <><i aria-hidden="true" />{typingCopy()}</> : null}
      </p>
      {actionError && <p className="trip-chat-action-error" role="alert">{actionError}</p>}
      {replyTarget && (
        <div className="trip-chat-replying" role="status">
          <span><strong>{nicknameFor(members, replyTarget.authorUserId)}</strong>에게 답장</span>
          <span>{replyTarget.body}</span>
          <button type="button" aria-label="답장 취소" onClick={() => setReplyTarget(null)}>×</button>
        </div>
      )}
      {mentionTrigger && (
        <div className="trip-chat-mentions" id="chat-mention-results" role="listbox" aria-label="언급할 참여자">
          {mentionCandidates.length === 0 ? (
            <p role="status">일치하는 참여자가 없어요.</p>
          ) : mentionCandidates.map((member, index) => (
            <button
              aria-selected={activeMentionIndex === index}
              className={activeMentionIndex === index ? 'active' : ''}
              id={`chat-mention-option-${member.userId}`}
              key={member.userId}
              role="option"
              type="button"
              onMouseDown={(event) => event.preventDefault()}
              onClick={() => selectMention(member)}
            >
              <span aria-hidden="true">{member.nickname.slice(0, 1)}</span>
              <strong>{member.nickname}</strong>
              {member.role === 'OWNER' && <small>방장</small>}
            </button>
          ))}
        </div>
      )}
      <form className="trip-chat-composer" aria-label="메시지 보내기" onSubmit={(event) => void handleSend(event)}>
        <textarea
          ref={composerRef}
          rows={1}
          aria-label="메시지 입력"
          autoComplete="off"
          name="chat-message"
          placeholder="메시지를 입력하세요…"
          value={draft}
          disabled={!chatConnected}
          aria-autocomplete="list"
          aria-controls={mentionTrigger ? 'chat-mention-results' : undefined}
          aria-expanded={Boolean(mentionTrigger)}
          aria-activedescendant={mentionTrigger && mentionCandidates[activeMentionIndex]
            ? `chat-mention-option-${mentionCandidates[activeMentionIndex].userId}`
            : undefined}
          role="combobox"
          onChange={(event) => handleDraftChange(event.target.value, event.target.selectionStart)}
          onCompositionStart={() => { composingRef.current = true }}
          onCompositionEnd={(event) => {
            composingRef.current = false
            handleDraftChange(event.currentTarget.value, event.currentTarget.selectionStart)
          }}
          onBlur={() => {
            typingBlurRef.current = window.setTimeout(stopTyping, 600)
          }}
          onFocus={() => {
            if (typingBlurRef.current !== null) window.clearTimeout(typingBlurRef.current)
            if (draft.length > 0) startTyping()
          }}
          onKeyDown={(event) => {
            if (composingRef.current || event.nativeEvent.isComposing) return
            if (mentionTrigger && mentionCandidates.length > 0) {
              if (event.key === 'ArrowDown' || event.key === 'ArrowUp') {
                event.preventDefault()
                const direction = event.key === 'ArrowDown' ? 1 : -1
                setActiveMentionIndex((current) => (current + direction + mentionCandidates.length) % mentionCandidates.length)
                return
              }
              if (event.key === 'Enter' || event.key === 'Tab') {
                event.preventDefault()
                selectMention(mentionCandidates[activeMentionIndex] ?? mentionCandidates[0])
                return
              }
              if (event.key === 'Escape') {
                event.preventDefault()
                setMentionTrigger(null)
                return
              }
            }
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
