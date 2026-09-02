package com.planmate.itinerary.api;

import com.planmate.itinerary.dto.AiItineraryDraft;

/**
 * Optional WP-F response boundary. Initial itinerary generation keeps using the
 * existing persistence path; generation ids registered as regeneration jobs are
 * stored as reviewable drafts instead.
 */
public interface RegenerationResponseHandler {
    boolean handles(Long generationId);
    void submit(Long tripId, Long generationId, AiItineraryDraft draft);
}
