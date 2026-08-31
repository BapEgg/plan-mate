# PlanMate visual review

Read this reference when implementing or reviewing rendered UI.

## Before editing

- Inspect the current desktop and mobile rendering and the relevant component/CSS ownership.
- Identify real data, asynchronous states, and long Korean strings that can affect layout.
- Record which existing working-tree changes overlap the files in scope.

## Rendered checks

- Review at a representative desktop width and a narrow mobile width; include tablet when the layout changes materially there.
- Check the screen's supported default, loading, empty, error, success, disabled, hover, and keyboard-focus states.
- Check long destination names, multi-line Korean copy, date/time values, and validation messages for overflow.
- Confirm semantic heading order, labels, keyboard operation, visible focus, contrast, touch targets, and reduced-motion behavior.
- Verify that decorative elements do not compete with the main travel task and that the primary action remains obvious.
- For authentication, exercise both login and the longer signup form plus account-recovery links and social entry points.
- For trip creation, inspect step progress, selected/unselected options, search results, validation, review, and generation progress.
- For trip detail, inspect generation states, day switching, resolved/unresolved places, member density, and developer-only panels.

## Audit order

1. Compare the rendered result with the chosen PlanMate direction and the screen's single job.
2. Run `$web-design-guidelines` against the touched frontend files.
3. Apply accessibility and interaction findings. Treat sections explicitly labeled Vercel-specific as suggestions rather than PlanMate requirements.
4. Run `npm.cmd run lint` and `npm.cmd run build` from `frontend/`.
5. Summarize before/after evidence, tested sizes and states, and any unverified dependency on backend data.

Do not add a production dependency, remote font, stock image, or map provider solely to improve appearance. Establish its product need, license, performance cost, and fallback first.
