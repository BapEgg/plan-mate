package com.planmate.itinerary.service;

import com.planmate.itinerary.dto.ItineraryDayResponse;
import com.planmate.itinerary.dto.ItineraryItemResponse;
import com.planmate.itinerary.dto.ItineraryResponse;
import com.planmate.itinerary.entity.ItineraryDayEntity;
import com.planmate.itinerary.entity.ItineraryEntity;
import com.planmate.itinerary.entity.ItineraryItemEntity;
import com.planmate.itinerary.repository.ItineraryRepository;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ItineraryQueryService {

    private final ItineraryRepository itineraryRepository;

    public ItineraryQueryService(ItineraryRepository itineraryRepository) {
        this.itineraryRepository = itineraryRepository;
    }

    @Transactional(readOnly = true)
    public List<ItineraryResponse> listTripItineraries(Long tripId) {
        return itineraryRepository.findByTrip_IdOrderByCreatedAtDesc(tripId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    private ItineraryResponse toResponse(ItineraryEntity itinerary) {
        return new ItineraryResponse(
                itinerary.getId(),
                itinerary.getGeneration().getId(),
                itinerary.getSummary(),
                itinerary.getCreatedAt(),
                itinerary.getDays().stream()
                        .sorted(Comparator.comparingInt(ItineraryDayEntity::getDay))
                        .map(this::toDayResponse)
                        .toList()
        );
    }

    private ItineraryDayResponse toDayResponse(ItineraryDayEntity day) {
        return new ItineraryDayResponse(
                day.getId(),
                day.getDay(),
                day.getDate(),
                day.getItems().stream()
                        .sorted(Comparator.comparingInt(ItineraryItemEntity::getSequence))
                        .map(this::toItemResponse)
                        .toList()
        );
    }

    private ItineraryItemResponse toItemResponse(ItineraryItemEntity item) {
        return new ItineraryItemResponse(
                item.getId(),
                item.getSequence(),
                item.getPlaceId(),
                item.getPlaceName(),
                item.getLatitude(),
                item.getLongitude(),
                item.getStartTime(),
                item.getDurationMinutes(),
                item.getReason()
        );
    }
}
