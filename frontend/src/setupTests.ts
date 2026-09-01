import '@testing-library/jest-dom/vitest'
import { afterEach, vi } from 'vitest'
import { cleanup } from '@testing-library/react'

// jsdom doesn't implement ResizeObserver — components that use it (e.g. to
// re-run a layout-dependent effect once a container's real size lands) just
// need it to exist here, not to actually observe anything.
if (typeof globalThis.ResizeObserver === 'undefined') {
  globalThis.ResizeObserver = class {
    observe() {}
    unobserve() {}
    disconnect() {}
  }
}

// vitest is configured without `globals: true`, so @testing-library/react's
// own auto-cleanup (which only registers when it finds a global `afterEach`)
// never runs — without this, DOM from every `render()` call in a file
// accumulates across `it` blocks instead of being torn down between them.
afterEach(() => {
  cleanup()
  // Module-level `vi.fn()` mocks (the `vi.mock(...)` pattern used for api/* modules)
  // are shared across every `it` in a file — without this, call counts/queued
  // once-resolvers from one test leak into the next.
  vi.clearAllMocks()
})
