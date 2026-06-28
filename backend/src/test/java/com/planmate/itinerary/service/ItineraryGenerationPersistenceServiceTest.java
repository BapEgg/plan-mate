package com.planmate.itinerary.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.planmate.common.outbox.OutboxEventEntity;
import com.planmate.common.outbox.OutboxEventRepository;
import com.planmate.itinerary.entity.ItineraryGenerationEntity;
import com.planmate.itinerary.entity.ItineraryGenerationStatus;
import com.planmate.itinerary.repository.ItineraryGenerationRepository;
import com.planmate.itinerary.repository.PlaceCandidateRepository;
import com.planmate.trip.entity.TripEntity;
import com.planmate.trip.repository.TripPlanningProfileRepository;
import com.planmate.trip.repository.TripRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class ItineraryGenerationPersistenceServiceTest {

    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");

    @Mock
    private ItineraryGenerationRepository generationRepository;

    @Mock
    private PlaceCandidateRepository placeCandidateRepository;

    @Mock
    private OutboxEventRepository outboxEventRepository;

    @Mock
    private TripRepository tripRepository;

    @Mock
    private TripPlanningProfileRepository tripPlanningProfileRepository;

    private ItineraryGenerationPersistenceService service;

    @BeforeEach
    void setUp() {
        service = new ItineraryGenerationPersistenceService(
                generationRepository,
                placeCandidateRepository,
                outboxEventRepository,
                tripRepository,
                tripPlanningProfileRepository,
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
    }

    @Test
    void createGenerationRequestStoresGenerationAndOutboxEventInOneServiceCall() {
        TripEntity trip = trip(45L);
        given(tripRepository.findAccessibleTrip(45L, 7L)).willReturn(Optional.of(trip));
        given(generationRepository.save(any(ItineraryGenerationEntity.class)))
                .willAnswer(invocation -> {
                    ItineraryGenerationEntity generation = invocation.getArgument(0);
                    ReflectionTestUtils.setField(generation, "id", 123L);
                    return generation;
                });

        ItineraryGenerationEntity generation = service.createGenerationRequest(
                7L,
                45L,
                ItineraryPromptService.PROMPT_VERSION
        );

        assertThat(generation.getId()).isEqualTo(123L);
        ArgumentCaptor<OutboxEventEntity> outboxCaptor = ArgumentCaptor.forClass(OutboxEventEntity.class);
        verify(outboxEventRepository).save(outboxCaptor.capture());
        OutboxEventEntity outboxEvent = outboxCaptor.getValue();
        assertThat(outboxEvent.getId()).isNotNull();
        assertThat(outboxEvent.getAggregateType()).isEqualTo("ITINERARY_GENERATION");
        assertThat(outboxEvent.getAggregateId()).isEqualTo("123");
        assertThat(outboxEvent.getEventType()).isEqualTo("ITINERARY_GENERATION_REQUESTED");
        assertThat(outboxEvent.getPayload())
                .containsEntry("generationId", 123L)
                .containsEntry("tripId", 45L)
                .containsEntry("userId", 7L);
        assertThat(outboxEvent.getCreatedAt()).isEqualTo(NOW);
    }

    @Test
    void markCollectingIfCreatedMarksCreatedGenerationAndReturnsTrue() {
        TripEntity trip = trip(45L);
        ItineraryGenerationEntity generation = generation(123L, trip);
        given(tripRepository.findAccessibleTrip(45L, 7L)).willReturn(Optional.of(trip));
        given(generationRepository.findWithLockById(123L)).willReturn(Optional.of(generation));

        boolean result = service.markCollectingIfCreated(7L, 45L, 123L);

        assertThat(result).isTrue();
        assertThat(generation.getStatus()).isEqualTo(ItineraryGenerationStatus.COLLECTING_CANDIDATES);
    }

    @Test
    void markCollectingIfCreatedReturnsFalseForAlreadyProcessedGeneration() {
        TripEntity trip = trip(45L);
        ItineraryGenerationEntity generation = generation(123L, trip);
        generation.markReady(NOW);
        given(tripRepository.findAccessibleTrip(45L, 7L)).willReturn(Optional.of(trip));
        given(generationRepository.findWithLockById(123L)).willReturn(Optional.of(generation));

        boolean result = service.markCollectingIfCreated(7L, 45L, 123L);

        assertThat(result).isFalse();
        assertThat(generation.getStatus()).isEqualTo(ItineraryGenerationStatus.READY_FOR_PLANNING);
    }

    private TripEntity trip(Long tripId) {
        TripEntity trip = TripEntity.create(
                "Kyoto trip",
                "Kyoto",
                "place-kyoto",
                "Kyoto, Japan",
                35.0,
                135.0,
                null,
                null,
                null,
                null,
                List.of("locality"),
                "locality",
                LocalDate.of(2026, 4, 1),
                LocalDate.of(2026, 4, 3),
                null,
                NOW
        );
        ReflectionTestUtils.setField(trip, "id", tripId);
        return trip;
    }

    private ItineraryGenerationEntity generation(Long generationId, TripEntity trip) {
        ItineraryGenerationEntity generation = ItineraryGenerationEntity.create(
                trip,
                ItineraryPromptService.PROMPT_VERSION,
                NOW
        );
        ReflectionTestUtils.setField(generation, "id", generationId);
        return generation;
    }
}
