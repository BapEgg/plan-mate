package com.planmate.revision.dto;

import com.planmate.itinerary.entity.ItineraryEntity;
import java.time.Instant;

public record ItineraryRevisionResponse(
        Long itineraryId,
        String tripId,
        Long generationId,
        int version,
        Long baseItineraryId,
        Long proposalId,
        String source,
        Long revisedByUserId,
        boolean current,
        Instant createdAt
) {
    public static ItineraryRevisionResponse from(ItineraryEntity itinerary, Long currentItineraryId) {
        return new ItineraryRevisionResponse(
                itinerary.getId(),
                itinerary.getTripId().toString(),
                itinerary.getGeneration() == null ? null : itinerary.getGeneration().getId(),
                itinerary.getVersion(),
                itinerary.getBaseItineraryId(),
                itinerary.getProposalId(),
                itinerary.getRevisionSource() == null ? "AI_GENERATION" : itinerary.getRevisionSource(),
                itinerary.getRevisedByUserId(),
                itinerary.getId().equals(currentItineraryId),
                itinerary.getCreatedAt()
        );
    }
}
