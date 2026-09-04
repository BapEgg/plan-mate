package com.planmate.itinerary.service;

import com.planmate.itinerary.dto.ItineraryPlaceDisplayView;
import com.planmate.itinerary.dto.ItineraryPlaceView;
import com.planmate.itinerary.entity.ItineraryDayEntity;
import com.planmate.itinerary.entity.ItineraryEntity;
import com.planmate.itinerary.entity.ItineraryItemEntity;
import com.planmate.itinerary.repository.ItineraryRepository;
import com.planmate.trip.api.TripAccessChecker;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ItineraryPlaceViewService {

    private final TripAccessChecker tripAccessChecker;
    private final ItineraryRepository itineraryRepository;
    private final PlaceDisplayResolver placeDisplayResolver;

    public ItineraryPlaceViewService(
            TripAccessChecker tripAccessChecker,
            ItineraryRepository itineraryRepository,
            PlaceDisplayResolver placeDisplayResolver
    ) {
        this.tripAccessChecker = tripAccessChecker;
        this.itineraryRepository = itineraryRepository;
        this.placeDisplayResolver = placeDisplayResolver;
    }

    @Transactional(readOnly = true)
    public List<ItineraryPlaceView> listLatestItineraryPlaceViews(Long userId, Long tripId, Integer dayNo) {
        tripAccessChecker.checkAccessible(userId, tripId);
        ItineraryEntity itinerary = itineraryRepository.findCurrentByTripId(tripId)
                .orElse(null);
        if (itinerary == null) {
            return List.of();
        }

        List<ItineraryItemEntity> items = itinerary.getDays().stream()
                .filter(day -> dayNo == null || day.getDay() == dayNo)
                .sorted(Comparator.comparingInt(ItineraryDayEntity::getDay))
                .flatMap(day -> day.getItems().stream())
                .sorted(Comparator.comparingInt(item -> item.getDay().getDay() * 1000 + item.getSequence()))
                .toList();
        Map<String, ItineraryPlaceDisplayView> displays = placeDisplayResolver.resolveListViews(
                tripId,
                items.stream().map(ItineraryItemEntity::getPlaceId).toList()
        );

        return items.stream()
                .map(item -> toPlaceView(itinerary, item, displays))
                .toList();
    }

    private ItineraryPlaceView toPlaceView(
            ItineraryEntity itinerary,
            ItineraryItemEntity item,
            Map<String, ItineraryPlaceDisplayView> displays
    ) {
        return new ItineraryPlaceView(
                itinerary.getId(),
                item.getId(),
                item.getDay().getDay(),
                item.getSequence(),
                item.getPlaceId(),
                item.getStartTime(),
                item.getDurationMinutes(),
                item.getCreatedSource(),
                displays.getOrDefault(item.getPlaceId(), ItineraryPlaceDisplayView.unresolved())
        );
    }
}
