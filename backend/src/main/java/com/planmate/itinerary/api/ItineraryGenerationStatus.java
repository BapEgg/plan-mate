package com.planmate.itinerary.api;

public enum ItineraryGenerationStatus {
    /**
     * Generation, input snapshot, and outbox have been saved,
     * but the candidate collection worker has not started yet.
     */
    CREATED,

    /**
     * The worker is collecting and recommending Google Places candidates.
     * A long-lived generation in this status is a stale recovery candidate.
     */
    COLLECTING_CANDIDATES,

    /**
     * Input and candidate snapshots have been fixed, so the prompt can be
     * retrieved and an AI draft can be submitted.
     *
     * Semantic AI draft validation failures keep the generation in this status.
     */
    READY_FOR_PLANNING,

    /**
     * A validated itinerary has been persisted successfully.
     *
     * Resubmitting the same canonical draft is idempotent, and resubmitting a
     * different draft returns 409.
     */
    COMPLETED,

    /**
     * The asynchronous candidate collection job exhausted all retries and failed.
     *
     * This status is not used for semantic AI draft validation failures or
     * WebSocket delivery failures.
     */
    FAILED
}
