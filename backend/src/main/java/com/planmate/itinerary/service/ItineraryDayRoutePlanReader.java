package com.planmate.itinerary.service;

import com.planmate.itinerary.entity.ItineraryDayEntity;
import com.planmate.itinerary.entity.ItineraryEntity;
import com.planmate.itinerary.exception.ItineraryErrorCode;
import com.planmate.itinerary.exception.ItineraryException;
import com.planmate.itinerary.repository.ItineraryRepository;
import com.planmate.trip.api.TripAccessChecker;
import java.util.List;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class ItineraryDayRoutePlanReader {

    private final TripAccessChecker tripAccessChecker;
    private final ItineraryRepository itineraryRepository;

    public ItineraryDayRoutePlanReader(
            TripAccessChecker tripAccessChecker,
            ItineraryRepository itineraryRepository
    ) {
        this.tripAccessChecker = tripAccessChecker;
        this.itineraryRepository = itineraryRepository;
    }

    /** DB snapshot만 짧게 읽고 외부 route 호출 전 트랜잭션을 닫는다. */
    @Transactional(readOnly = true)
    public DayRoutePlan read(Long userId, Long tripId, int dayNumber) {
        tripAccessChecker.checkAccessible(userId, tripId);
        ItineraryEntity itinerary = itineraryRepository.findCurrentByTripId(tripId)
                .orElseThrow(() -> new ItineraryException(ItineraryErrorCode.GENERATION_NOT_READY));
        ItineraryDayEntity day = itinerary.getDays().stream()
                .filter(candidate -> candidate.getDay() == dayNumber)
                .findFirst()
                .orElseThrow(() -> new ItineraryException(ItineraryErrorCode.ITINERARY_DAY_NOT_FOUND));
        List<DayRouteItem> items = day.getItems().stream()
                .map(item -> new DayRouteItem(item.getId(), item.getSequence(), item.getPlaceId()))
                .toList();
        return new DayRoutePlan(itinerary.getId(), itinerary.getVersion(), dayNumber, items);
    }

    public record DayRoutePlan(
            Long itineraryId,
            int itineraryVersion,
            int dayNumber,
            List<DayRouteItem> items
    ) {
        public DayRoutePlan {
            items = List.copyOf(items);
        }
    }

    public record DayRouteItem(Long itemId, int sequence, String placeId) {
    }
}
