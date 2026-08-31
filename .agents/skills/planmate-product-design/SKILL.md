---
name: planmate-product-design
description: Design or revise PlanMate user-facing web screens and Korean product copy so the AI travel planner feels like a credible travel product. Use for UI/UX direction, visual implementation, responsive states, copywriting, and visual QA in this repository; do not use for backend-only reliability work.
---

# PlanMate Product Design

Create a coherent travel product, not a generic AI-themed interface. Preserve the user's explicit visual direction and the product's implemented behavior.

## Load only the relevant context

- Read [product-context.md](references/product-context.md) before making product claims, changing information architecture, or designing data-backed UI.
- Read [content-style.md](references/content-style.md) when writing or revising Korean interface copy.
- Read [visual-review.md](references/visual-review.md) when implementing or reviewing rendered UI.

## Design workflow

1. Identify the screen's single job, the traveler's current state, and the real product data that proves value on that screen.
2. For a substantial visual change, use `$frontend-design` to form a compact direction: 4–6 color tokens, deliberate type roles, a layout concept, and one product-specific signature element. Reject any direction that would work unchanged for an unrelated AI product.
3. Verify claims and controls against the implementation. Do not invent booking, collaboration, live pricing, route optimization, instant generation, or other capabilities. Present speculative additions as concepts until the user authorizes functional work.
4. Build the smallest coherent system that supports the direction. Prefer shared tokens, semantic structure, and existing project patterns over decorative one-off components or a new library.
5. Write copy from the traveler's side of the screen. Name the outcome or next action, keep vocabulary consistent across controls and feedback, and keep AI terminology to provenance, assistance, or processing where it is truthful.
6. Render and inspect the result. Use `$web-design-guidelines` as a final accessibility and interaction audit, but treat its Vercel-specific brand and English-copy preferences as optional; Korean usage and the PlanMate brief win.
7. Run the repository checks in `AGENTS.md` and report the visual states exercised.

## Quality bar

- The primary visual evidence is a concrete travel artifact, not an AI badge, sparkle, gradient, or abstract promise.
- One memorable signature element carries the direction; surrounding elements remain restrained.
- Responsive layouts preserve task priority instead of merely stacking every desktop block.
- Loading, empty, error, success, disabled, and focus states explain what happened and what the traveler can do next.
- Copy never promises an unimplemented capability or an unverified speed, price, availability, distance, or travel time.
