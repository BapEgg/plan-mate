package com.planmate.itinerary.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.willThrow;

import com.planmate.common.outbox.OutboxEventRepository;
import com.planmate.itinerary.repository.ItineraryGenerationRepository;
import com.planmate.place.api.GeoPoint;
import com.planmate.place.api.GeoViewport;
import com.planmate.place.api.ResolvedPlace;
import com.planmate.trip.domain.AccommodationArea;
import com.planmate.trip.domain.AccommodationMode;
import com.planmate.trip.domain.BudgetItem;
import com.planmate.trip.domain.BudgetLevel;
import com.planmate.trip.domain.CompanionType;
import com.planmate.trip.domain.CurrencyCode;
import com.planmate.trip.domain.ResolvedSchedulePreference;
import com.planmate.trip.domain.TransportMode;
import com.planmate.trip.domain.TravelPace;
import com.planmate.trip.domain.TripInterest;
import com.planmate.trip.dto.TripCreateRequest;
import com.planmate.trip.entity.TripEntity;
import com.planmate.trip.repository.TripRepository;
import com.planmate.trip.service.TripCreationPersistenceService;
import com.planmate.user.entity.UserEntity;
import com.planmate.user.repository.UserRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest
class ItineraryGenerationInputSnapshotStoreFailureIntegrationTest {

    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TripRepository tripRepository;

    @Autowired
    private TripCreationPersistenceService tripCreationPersistenceService;

    @Autowired
    private ItineraryGenerationPersistenceService persistenceService;

    @Autowired
    private ItineraryGenerationRepository generationRepository;

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    @MockitoBean
    private GenerationInputSnapshotStore generationInputSnapshotStore;

    private final List<Long> tripIds = new ArrayList<>();
    private final List<Long> userIds = new ArrayList<>();

    @AfterEach
    void tearDown() {
        tripIds.forEach(tripRepository::deleteById);
        userIds.forEach(userRepository::deleteById);
    }

    @Test
    void rollsBackGenerationAndDoesNotSaveOutboxWhenSnapshotSaveFails() {
        TripFixture fixture = createTripFixture();
        willThrow(new IllegalStateException("snapshot save failed"))
                .given(generationInputSnapshotStore)
                .save(anyLong(), any(), any());

        assertThatThrownBy(() -> persistenceService.createGenerationRequest(
                fixture.userId(),
                fixture.tripId(),
                ItineraryPromptService.CURRENT_PROMPT_VERSION
        )).isInstanceOf(IllegalStateException.class);

        assertThat(generationRepository.findFirstByTripIdOrderByCreatedAtDesc(fixture.tripId())).isEmpty();
        assertThat(outboxEventRepository.findAll())
                .noneMatch(event -> fixture.tripId().equals(event.getPayload().get("tripId")));
    }

    private TripFixture createTripFixture() {
        UserEntity user = userRepository.save(UserEntity.createOauthUser(
                "snapshot-failure-" + UUID.randomUUID() + "@example.com",
                "snapshot-failure@example.com",
                "snapshot-failure-user",
                true,
                NOW
        ));
        TripEntity trip = tripCreationPersistenceService.create(
                user.getId(),
                tripCreateRequest(),
                destination(),
                null,
                List.of(),
                new ResolvedSchedulePreference(LocalTime.of(8, 0), LocalTime.of(20, 0))
        );
        userIds.add(user.getId());
        tripIds.add(trip.getId());
        return new TripFixture(user.getId(), trip.getId());
    }

    private TripCreateRequest tripCreateRequest() {
        return new TripCreateRequest(
                "Snapshot failure trip",
                "place-kyoto",
                LocalDate.of(2026, 4, 1),
                LocalDate.of(2026, 4, 2),
                new TripCreateRequest.CompanionRequest(2, CompanionType.FRIENDS, false, 0, null, false, 0),
                new TripCreateRequest.BudgetRequest(CurrencyCode.KRW, 1_000_000L, BudgetLevel.BALANCED, List.of(BudgetItem.FOOD)),
                new TripCreateRequest.PreferenceRequest(TravelPace.BALANCED, List.of(TripInterest.FOOD)),
                new TripCreateRequest.TransportationRequest(TransportMode.PUBLIC_TRANSIT, List.of(TransportMode.WALK)),
                new TripCreateRequest.AccommodationRequest(AccommodationMode.UNDECIDED, AccommodationArea.TRANSIT, null, null, null),
                new TripCreateRequest.SchedulePreferenceRequest(LocalTime.of(8, 0), LocalTime.of(20, 0)),
                new TripCreateRequest.AdditionalRequest(List.of(), List.of(), null)
        );
    }

    private ResolvedPlace destination() {
        return new ResolvedPlace(
                "place-kyoto",
                "Kyoto",
                "Kyoto, Japan",
                new GeoPoint(35.0, 135.0),
                new GeoViewport(new GeoPoint(34.8, 134.8), new GeoPoint(35.2, 135.2)),
                List.of("locality"),
                "locality"
        );
    }

    private record TripFixture(Long userId, Long tripId) {
    }
}
