package com.planmate.itinerary.service;

import com.planmate.itinerary.domain.GenerationInputSnapshot;
import com.planmate.itinerary.exception.ItineraryErrorCode;
import com.planmate.itinerary.exception.ItineraryException;
import com.planmate.recommendation.api.CandidateRecommendationRequest;
import com.planmate.recommendation.api.Interest;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class CandidateRecommendationRequestMapper {

    private static final String PLACE_SEARCH = "PLACE_SEARCH";

    public CandidateRecommendationRequest map(GenerationInputSnapshot snapshot) {
        return new CandidateRecommendationRequest(
                destination(snapshot.destination()),
                interests(snapshot.preference()),
                accommodation(snapshot.accommodation()),
                mustVisitPlaces(snapshot.mustVisitPlaces())
        );
    }

    private CandidateRecommendationRequest.Destination destination(GenerationInputSnapshot.Destination destination) {
        if (destination == null || !destination.isResolved()) {
            throw new ItineraryException(ItineraryErrorCode.DESTINATION_NOT_RESOLVED);
        }
        return new CandidateRecommendationRequest.Destination(
                destination.displayName(),
                location(destination.latitude(), destination.longitude()),
                viewport(destination.viewport())
        );
    }

    private List<Interest> interests(GenerationInputSnapshot.Preference preference) {
        if (preference == null) {
            return List.of();
        }
        Set<Interest> interests = new LinkedHashSet<>();
        for (String value : preference.interests()) {
            if (value == null || value.isBlank()) {
                continue;
            }
            try {
                interests.add(Interest.valueOf(value.trim().toUpperCase(Locale.ROOT)));
            } catch (IllegalArgumentException ignored) {
                // Unknown historical or future interest codes should not block default candidate collection.
            }
        }
        return List.copyOf(interests);
    }

    private CandidateRecommendationRequest.Accommodation accommodation(GenerationInputSnapshot.Accommodation accommodation) {
        if (accommodation == null || accommodation.accommodationMode() == null) {
            return null;
        }
        if (!PLACE_SEARCH.equals(accommodation.accommodationMode().trim().toUpperCase(Locale.ROOT))) {
            return null;
        }
        if (accommodation.latitude() == null || accommodation.longitude() == null) {
            return null;
        }
        return new CandidateRecommendationRequest.Accommodation(
                location(accommodation.latitude(), accommodation.longitude())
        );
    }

    private List<CandidateRecommendationRequest.MustVisitPlace> mustVisitPlaces(
            List<GenerationInputSnapshot.MustVisitPlace> mustVisitPlaces
    ) {
        return mustVisitPlaces.stream()
                .map(place -> new CandidateRecommendationRequest.MustVisitPlace(
                        place.placeId(),
                        place.name(),
                        place.formattedAddress(),
                        nullableLocation(place.latitude(), place.longitude()),
                        place.primaryType(),
                        place.types()
                ))
                .toList();
    }

    private CandidateRecommendationRequest.Viewport viewport(GenerationInputSnapshot.Viewport viewport) {
        if (viewport == null
                || viewport.lowLatitude() == null
                || viewport.lowLongitude() == null
                || viewport.highLatitude() == null
                || viewport.highLongitude() == null) {
            return null;
        }
        return new CandidateRecommendationRequest.Viewport(
                location(viewport.lowLatitude(), viewport.lowLongitude()),
                location(viewport.highLatitude(), viewport.highLongitude())
        );
    }

    private CandidateRecommendationRequest.Location nullableLocation(Double latitude, Double longitude) {
        if (latitude == null || longitude == null) {
            return null;
        }
        return location(latitude, longitude);
    }

    private CandidateRecommendationRequest.Location location(double latitude, double longitude) {
        return new CandidateRecommendationRequest.Location(latitude, longitude);
    }
}
