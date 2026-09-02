package com.planmate.revision.service;

import com.planmate.itinerary.repository.ItineraryRepository;
import com.planmate.revision.dto.ItineraryRevisionResponse;
import com.planmate.trip.api.TripAccessChecker;
import com.planmate.trip.repository.TripRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ItineraryRevisionQueryService {

    private final TripAccessChecker tripAccessChecker;
    private final TripRepository tripRepository;
    private final ItineraryRepository itineraryRepository;

    public ItineraryRevisionQueryService(
            TripAccessChecker tripAccessChecker,
            TripRepository tripRepository,
            ItineraryRepository itineraryRepository
    ) {
        this.tripAccessChecker = tripAccessChecker;
        this.tripRepository = tripRepository;
        this.itineraryRepository = itineraryRepository;
    }

    @Transactional(readOnly = true)
    public List<ItineraryRevisionResponse> list(Long userId, Long tripId) {
        tripAccessChecker.checkAccessible(userId, tripId);
        Long currentId = tripRepository.findById(tripId).orElseThrow().getCurrentItineraryId();
        return itineraryRepository.findByTripIdOrderByCreatedAtDesc(tripId).stream()
                .map(itinerary -> ItineraryRevisionResponse.from(itinerary, currentId))
                .toList();
    }
}
