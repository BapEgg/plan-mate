# Manual Itinerary Verification

This flow does not call the OpenAI API. It collects real candidates from Google
Places and lets the developer paste the generated prompt into ChatGPT manually.

1. Set a valid Google Places API key.
   - Backend env: `GOOGLE_PLACES_API_KEY=...`
2. Enable manual handoff locally.
   - Backend env: `APP_ITINERARY_MANUAL_HANDOFF_ENABLED=true`
   - Frontend env: `VITE_MANUAL_HANDOFF_ENABLED=true`
3. Start the backend and frontend.
   - Backend: `cd backend && ./gradlew bootRun`
   - Frontend: `cd frontend && npm run dev`
4. Log in and create a trip.
5. Select a real destination from Google Places autocomplete.
6. Fill in detailed trip conditions.
7. Submit the trip creation form.
8. In manual mode, the frontend calls:
   - `POST /api/trips/{tripId}/itinerary-generations`
9. Confirm that candidate collection finishes with `READY_FOR_PLANNING`.
10. Open or copy:
    - `GET /api/trips/{tripId}/itinerary-generations/{generationId}/manual-prompt`
    - `GET /api/trips/{tripId}/itinerary-generations/{generationId}/ai-request`
11. Paste the prompt into ChatGPT manually.
12. Paste ChatGPT's JSON response into the manual response box or call:
    - `POST /api/trips/{tripId}/itinerary-generations/{generationId}/manual-response`
13. Confirm that the generation becomes `COMPLETED`.
14. Open the trip detail page and verify that the saved itinerary is displayed.

Do not add fake places, fixture candidates, sample itinerary results, OpenAI API
keys, or OpenAI SDK dependencies for this verification flow.
