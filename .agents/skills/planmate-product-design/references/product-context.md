# PlanMate product context

Read this reference before changing information architecture, designing data-backed UI, or making capability claims.

## Product promise

PlanMate is a Korean personal travel-planning project. It collects structured trip preferences, finds real place candidates, and stores a validated day-by-day itinerary. The credible product story is specificity and verifiability, not AI spectacle.

## Current user-facing surfaces

- Authentication: login, signup, email verification, account recovery, and Google/Naver/Kakao OAuth entry points.
- Main dashboard: traveler profile, trip list, trip status, and new-trip entry.
- Trip creation: a structured multi-step flow for destination, dates, companions, budget, pace, interests, transportation, accommodation, daily time window, must-visit places, avoid conditions, and a free-form request.
- Trip detail: generation status, planning-profile summary, members, day tabs, visit order, start time, duration, place display data, coordinates, and Google Maps URI when resolved.

## Truthful capability boundaries

- Itinerary generation is asynchronous and currently uses manual AI handoff. Do not claim an instant or fully automated end-to-end AI service.
- The product can expose collection and validation progress. Describe the observable stages rather than simulating conversational intelligence.
- Coordinates and a Google Maps URI may be available. A full interactive map, calculated travel time, and route optimization are not established frontend capabilities; verify before presenting them as live data.
- Real-time collaboration, booking, flight or hotel prices, reservations, offline access, and expense splitting are not established capabilities. Do not promote placeholders or “coming soon” implementation as current value.
- Email verification is an account requirement, not a lead product benefit.

## Technical constraints that affect design

- Frontend: React 19, TypeScript, Vite, and repository-owned CSS without a general-purpose UI component library.
- Preserve authentication and API contracts unless functional changes are explicitly in scope.
- Prefer real local data for review. If stable visual states require fixtures, keep them development-only and obtain authorization before adding a new fixture surface.
- Existing working-tree changes belong to the user and must be preserved.

## Useful product evidence

Prefer these artifacts when demonstrating value:

- Destination and travel dates
- Companion type and count
- Travel pace, interests, and primary transportation
- Accommodation preference and daily start/end time
- Must-visit places and avoid conditions
- Candidate collection and validation state
- Day number, visit order, start time, duration, and resolved place data
