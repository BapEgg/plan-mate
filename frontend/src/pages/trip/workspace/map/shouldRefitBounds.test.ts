import { describe, expect, it } from 'vitest'
import { shouldRefitBounds } from './shouldRefitBounds'

describe('shouldRefitBounds', () => {
  it('refits when the signal changes (DAY change or manual re-fit)', () => {
    expect(shouldRefitBounds('1:0', 3, '2:0', 3)).toBe(true)
  })

  it('refits when coordinates arrive late for the same signal (0 located -> some located)', () => {
    expect(shouldRefitBounds('1:0', 0, '1:0', 4)).toBe(true)
  })

  it('does not refit again for the same signal once markers were already located', () => {
    expect(shouldRefitBounds('1:0', 3, '1:0', 5)).toBe(false)
  })

  it('does not refit for the same signal while still zero located (nothing changed)', () => {
    expect(shouldRefitBounds('1:0', 0, '1:0', 0)).toBe(false)
  })

  it('treats the very first render (no previous signal) as a signal change', () => {
    expect(shouldRefitBounds(null, 0, '1:0', 0)).toBe(true)
  })
})
