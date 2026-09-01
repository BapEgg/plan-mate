package com.planmate.itinerary.service;

import com.planmate.itinerary.api.ItineraryReadModel;
import com.planmate.itinerary.api.LatestItineraryReader;
import com.planmate.itinerary.entity.ItineraryDayEntity;
import com.planmate.itinerary.entity.ItineraryEntity;
import com.planmate.itinerary.entity.ItineraryItemEntity;
import com.planmate.itinerary.repository.ItineraryRepository;
import java.util.Comparator;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ItineraryQueryService implements LatestItineraryReader {

    private final ItineraryRepository itineraryRepository;

    public ItineraryQueryService(ItineraryRepository itineraryRepository) {
        this.itineraryRepository = itineraryRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ItineraryReadModel> findLatestByTripId(Long tripId) {
        // ADR-0002: current pointer(trips.current_itinerary_id)가 진짜 소스다. 백필이
        // 모든 trip에 포인터를 채워두므로 fallback은 방어적 상황에서만 쓰인다.
        return itineraryRepository.findCurrentByTripId(tripId)
                .or(() -> itineraryRepository.findFirstByTripIdOrderByCreatedAtDesc(tripId))
                .map(this::toReadModel);
    }

    private ItineraryReadModel toReadModel(ItineraryEntity itinerary) {
        return new ItineraryReadModel(
                itinerary.getId(),
                itinerary.getGeneration().getId(),
                itinerary.getCreatedAt(),
                itinerary.getVersion(),
                itinerary.getDays().stream()
                        .sorted(Comparator.comparingInt(ItineraryDayEntity::getDay))
                        .map(this::toDayReadModel)
                        .toList()
        );
    }

    private ItineraryReadModel.Day toDayReadModel(ItineraryDayEntity day) {
        return new ItineraryReadModel.Day(
                day.getId(),
                day.getDay(),
                day.getDate(),
                day.getItems().stream()
                        .sorted(Comparator.comparingInt(ItineraryItemEntity::getSequence))
                        .map(this::toItemReadModel)
                        .toList()
        );
    }

    private ItineraryReadModel.Item toItemReadModel(ItineraryItemEntity item) {
        return new ItineraryReadModel.Item(
                item.getId(),
                item.getSequence(),
                item.getPlaceId(),
                item.getStartTime(),
                item.getDurationMinutes(),
                item.getCreatedSource().name()
        );
    }
}
