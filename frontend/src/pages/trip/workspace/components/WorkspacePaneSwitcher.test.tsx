import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import { WorkspacePaneSwitcher } from './WorkspacePaneSwitcher'

const panelIds = {
  SCHEDULE: 'schedule-panel',
  MAP: 'map-panel',
  ROOM: 'room-panel',
} as const

describe('WorkspacePaneSwitcher', () => {
  it('moves focus and selection intent with arrow, Home, and End keys', async () => {
    const user = userEvent.setup()
    const onChange = vi.fn()
    render(<WorkspacePaneSwitcher activePane="SCHEDULE" onChange={onChange} panelIds={panelIds} />)
    const schedule = screen.getByRole('tab', { name: '일정' })
    const map = screen.getByRole('tab', { name: '지도' })
    const room = screen.getByRole('tab', { name: '여행방' })

    schedule.focus()
    await user.keyboard('{ArrowRight}')
    expect(onChange).toHaveBeenLastCalledWith('MAP')
    expect(map).toHaveFocus()

    await user.keyboard('{End}')
    expect(onChange).toHaveBeenLastCalledWith('ROOM')
    expect(room).toHaveFocus()

    await user.keyboard('{Home}')
    expect(onChange).toHaveBeenLastCalledWith('SCHEDULE')
    expect(schedule).toHaveFocus()
  })
})
