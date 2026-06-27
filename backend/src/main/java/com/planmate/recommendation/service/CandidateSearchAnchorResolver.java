package com.planmate.recommendation.service;

import com.planmate.place.dto.GeoPoint;
import com.planmate.place.dto.ResolvedDestination;
import com.planmate.recommendation.domain.CandidateSearchAnchor;
import com.planmate.trip.domain.AccommodationMode;
import com.planmate.trip.entity.TripPlanningProfileEntity;
import org.springframework.stereotype.Component;

@Component
public class CandidateSearchAnchorResolver {

    public CandidateSearchAnchor resolve(ResolvedDestination destination, TripPlanningProfileEntity profile) {
        if (profile.getAccommodationMode() == AccommodationMode.PLACE_SEARCH
                && profile.getAccommodationLatitude() != null
                && profile.getAccommodationLongitude() != null) {
            return CandidateSearchAnchor.accommodation(new GeoPoint(
                    profile.getAccommodationLatitude(),
                    profile.getAccommodationLongitude()
            ));
        }

        return CandidateSearchAnchor.destination(destination.location(), destination.viewport());
    }
}
