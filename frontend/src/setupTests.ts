import '@testing-library/jest-dom/vitest'
import { afterEach } from 'vitest'
import { cleanup } from '@testing-library/react'

// vitest is configured without `globals: true`, so @testing-library/react's
// own auto-cleanup (which only registers when it finds a global `afterEach`)
// never runs — without this, DOM from every `render()` call in a file
// accumulates across `it` blocks instead of being torn down between them.
afterEach(() => {
  cleanup()
})
