import '@testing-library/jest-dom/vitest'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import type { ItineraryRegeneration } from '../../../../api/regenerations'
import { ItineraryEditor } from './ItineraryEditor'
import type { ItineraryPlace } from '../workspaceTypes'

vi.mock('../../../../api/regenerations', () => ({
  applyItineraryRegeneration: vi.fn(),
  createItineraryRegeneration: vi.fn(),
  getItineraryRegeneration: vi.fn(),
  rejectItineraryRegeneration: vi.fn(),
}))

const places: ItineraryPlace[] = [
  place('101', 1, '매미성', '10:00'),
  place('102', 2, '현지식 점심', '12:00'),
  place('103', 3, '바람의 언덕', '15:00'),
]

describe('ItineraryEditor', () => {
  it('selects a continuous range and exposes fixed-item controls', async () => {
    const user = userEvent.setup()
    renderEditor({ mode: 'PARTIAL', initialJob: null })

    await user.click(screen.getByRole('button', { name: /매미성/ }))
    expect(screen.getByRole('heading', { name: '마지막 장소를 선택하세요.' })).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: /바람의 언덕/ }))

    expect(screen.getByText('3곳 중 3곳 바꾸기')).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: '현지식 점심, 바꾸기' }))
    expect(screen.getByText('3곳 중 2곳 바꾸기')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: '현지식 점심, 그대로 두기' })).toHaveAttribute('aria-pressed', 'true')
  })

  it('shows changed rows first in the review state', () => {
    renderEditor({ mode: 'FULL', initialJob: readyJob() })

    expect(screen.getByRole('heading', { name: '새 일정에서 1곳이 달라져요.' })).toBeInTheDocument()
    expect(screen.getByText('새로운 해안 명소')).toBeInTheDocument()
    expect(screen.queryByText('현지식 점심')).not.toBeInTheDocument()
    expect(screen.getByRole('button', { name: '기존 일정 유지' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: '이 일정으로 바꾸기' })).toBeInTheDocument()
  })

  it('protects an unfinished request and traps focus inside the discard dialog', async () => {
    const user = userEvent.setup()
    renderEditor({ mode: 'FULL', initialJob: null })

    await user.type(screen.getByRole('textbox', { name: /추가 요청/ }), '바다를 오래 보고 싶어요')
    await user.click(screen.getByRole('button', { name: '닫기' }))

    const keepEditing = screen.getByRole('button', { name: '계속 수정' })
    const discard = screen.getByRole('button', { name: '변경 내용 버리고 나가기' })
    expect(screen.getByRole('alertdialog', { name: '작성 중인 수정을 그만둘까요?' })).toBeInTheDocument()
    expect(keepEditing).toHaveFocus()

    await user.tab({ shift: true })
    expect(discard).toHaveFocus()
    await user.tab()
    expect(keepEditing).toHaveFocus()
  })

  it('keeps reverse keyboard navigation inside the editor from its focused heading', async () => {
    const user = userEvent.setup()
    renderEditor({ mode: 'FULL', initialJob: null })

    expect(screen.getByRole('heading', { name: '일정 다시 만들기' })).toHaveFocus()
    await user.tab({ shift: true })

    expect(screen.getByRole('button', { name: '전체 일정 다시 만들기' })).toHaveFocus()
  })
})

function renderEditor(overrides: { mode: 'FULL' | 'PARTIAL'; initialJob: ItineraryRegeneration | null }) {
  return render(
    <ItineraryEditor
      accessToken="token"
      activeDay={1}
      baseItineraryId={50}
      baseItineraryVersion={1}
      initialJob={overrides.initialJob}
      mode={overrides.mode}
      onApplied={vi.fn()}
      onClose={vi.fn()}
      onJobChanged={vi.fn()}
      places={places}
      tripId="1530"
    />,
  )
}

function place(id: string, order: number, title: string, startTime: string): ItineraryPlace {
  return {
    id,
    day: 1,
    order,
    title,
    startTime,
    duration: '1시간',
    durationMinutes: 60,
    latitude: null,
    longitude: null,
    locationLabel: null,
    googleMapsUri: null,
    placeId: `place-${id}`,
    resolved: true,
    displaySource: 'PROVIDER',
    source: 'AI_DRAFT',
  }
}

function readyJob(): ItineraryRegeneration {
  return {
    regenerationId: 8,
    tripId: '1530',
    generationId: '1400',
    baseItineraryId: 50,
    baseItineraryVersion: 1,
    scope: 'FULL',
    dayNumber: null,
    startItemId: null,
    endItemId: null,
    fixedItemIds: [],
    status: 'READY_FOR_REVIEW',
    failureReason: null,
    appliedItineraryId: null,
    createdAt: '2026-09-01T00:00:00Z',
    updatedAt: '2026-09-01T00:01:00Z',
    days: [{
      day: 1,
      date: '2026-10-10',
      items: [
        {
          sequence: 1,
          originalItemId: 101,
          originalPlaceId: 'place-101',
          originalDisplayName: '매미성',
          originalStartTime: '10:00:00',
          originalDurationMinutes: 60,
          proposedPlaceId: 'place-new',
          proposedDisplayName: '새로운 해안 명소',
          proposedStartTime: '10:10:00',
          proposedDurationMinutes: 60,
          fixed: false,
          changed: true,
        },
        {
          sequence: 2,
          originalItemId: 102,
          originalPlaceId: 'place-102',
          originalDisplayName: '현지식 점심',
          originalStartTime: '12:00:00',
          originalDurationMinutes: 60,
          proposedPlaceId: 'place-102',
          proposedDisplayName: '현지식 점심',
          proposedStartTime: '12:00:00',
          proposedDurationMinutes: 60,
          fixed: true,
          changed: false,
        },
      ],
    }],
  }
}
