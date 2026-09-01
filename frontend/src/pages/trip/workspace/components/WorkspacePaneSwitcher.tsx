import { useRef } from 'react'
import type { KeyboardEvent } from 'react'
import type { MobileWorkspacePane } from '../workspaceTypes'

const PANES: Array<[MobileWorkspacePane, string]> = [
  ['SCHEDULE', '일정'],
  ['MAP', '지도'],
  ['ROOM', '여행방'],
]

/**
 * NARROW-mode bottom pane switcher, implemented as a real WAI-ARIA tabs
 * widget (role=tablist/tab/tabpanel, roving tabindex, arrow-key navigation)
 * rather than a plain button row — spec §10.3 calls out the previous
 * `<nav>`-of-buttons as missing the tab/tabpanel keyboard contract.
 */
export function WorkspacePaneSwitcher({ activePane, onChange, panelIds }: {
  activePane: MobileWorkspacePane
  onChange: (pane: MobileWorkspacePane) => void
  panelIds: Record<MobileWorkspacePane, string>
}) {
  const tabRefs = useRef<Partial<Record<MobileWorkspacePane, HTMLButtonElement | null>>>({})

  function handleKeyDown(event: KeyboardEvent<HTMLButtonElement>, index: number) {
    if (event.key !== 'ArrowRight' && event.key !== 'ArrowLeft') return
    event.preventDefault()
    const nextIndex = event.key === 'ArrowRight'
      ? (index + 1) % PANES.length
      : (index - 1 + PANES.length) % PANES.length
    const [nextPane] = PANES[nextIndex]
    onChange(nextPane)
    tabRefs.current[nextPane]?.focus()
  }

  return (
    <nav className="planning-mobile-switcher" role="tablist" aria-label="여행 상세 화면 선택">
      {PANES.map(([pane, label], index) => (
        <button
          aria-controls={panelIds[pane]}
          aria-selected={activePane === pane}
          className={activePane === pane ? 'active' : ''}
          id={`workspace-pane-tab-${pane}`}
          key={pane}
          ref={(element) => { tabRefs.current[pane] = element }}
          role="tab"
          tabIndex={activePane === pane ? 0 : -1}
          type="button"
          onClick={() => onChange(pane)}
          onKeyDown={(event) => handleKeyDown(event, index)}
        >{label}</button>
      ))}
    </nav>
  )
}
