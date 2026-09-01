/**
 * Decides whether the map should auto-fit bounds again for the current
 * `fitSignal` (DAY change or manual "일정 전체 보기"). Coordinates can arrive
 * late — the schedule renders stored items first and place-view lookups
 * resolve lat/lng asynchronously afterwards — so a signal that first painted
 * with zero located markers must still get one fit once markers appear. Once
 * a signal has been fit with located markers present, later updates for the
 * same signal must never override the user's viewport again.
 */
export function shouldRefitBounds(
  previousSignal: string | null,
  previousLocatedCount: number,
  nextSignal: string,
  nextLocatedCount: number,
): boolean {
  if (previousSignal !== nextSignal) return true
  return previousLocatedCount === 0 && nextLocatedCount > 0
}
