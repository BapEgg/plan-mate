package com.planmate.itinerary.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.planmate.itinerary.dto.AiItineraryResponse;
import com.planmate.itinerary.entity.ItineraryGenerationEntity;
import com.planmate.itinerary.entity.ItineraryGenerationStatus;
import com.planmate.itinerary.realtime.ItineraryGenerationStatusChangedEvent;
import com.planmate.itinerary.entity.PlaceCandidateEntity;
import com.planmate.itinerary.exception.ItineraryException;
import com.planmate.itinerary.repository.ItineraryDayRepository;
import com.planmate.itinerary.repository.ItineraryGenerationRepository;
import com.planmate.itinerary.repository.ItineraryItemRepository;
import com.planmate.itinerary.repository.ItineraryRepository;
import com.planmate.itinerary.repository.PlaceCandidateRepository;
import com.planmate.place.dto.GeoPoint;
import com.planmate.recommendation.domain.CandidateSearchCategory;
import com.planmate.recommendation.domain.CollectedPlaceCandidate;
import com.planmate.trip.entity.TripEntity;
import com.planmate.trip.repository.TripRepository;
import com.planmate.user.entity.UserEntity;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

class ManualItineraryResponseServiceTest {

    private final TripRepository tripRepository = Mockito.mock(TripRepository.class);
    private final ItineraryGenerationRepository generationRepository = Mockito.mock(ItineraryGenerationRepository.class);
    private final PlaceCandidateRepository placeCandidateRepository = Mockito.mock(PlaceCandidateRepository.class);
    private final ItineraryRepository itineraryRepository = Mockito.mock(ItineraryRepository.class);
    private final ItineraryDayRepository itineraryDayRepository = Mockito.mock(ItineraryDayRepository.class);
    private final ItineraryItemRepository itineraryItemRepository = Mockito.mock(ItineraryItemRepository.class);
    private final ApplicationEventPublisher eventPublisher = Mockito.mock(ApplicationEventPublisher.class);
    private final Clock clock = Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC);
    private final ManualItineraryResponseService service = new ManualItineraryResponseService(
            tripRepository,
            generationRepository,
            placeCandidateRepository,
            itineraryRepository,
            itineraryDayRepository,
            itineraryItemRepository,
            clock,
            eventPublisher
    );

    private TripEntity trip;
    private ItineraryGenerationEntity generation;
    private PlaceCandidateEntity candidate;

    @BeforeEach
    void setUp() {
        trip = trip();
        generation = ItineraryGenerationEntity.create(trip, ItineraryPromptService.PROMPT_VERSION, Instant.now(clock));
        generation.markReady(Instant.now(clock));
        ReflectionTestUtils.setField(generation, "id", 10L);
        candidate = PlaceCandidateEntity.from(generation, collectedCandidate("place-1", "Temple"), 1);

        given(tripRepository.findAccessibleTrip(1L, 99L)).willReturn(Optional.of(trip));
        given(generationRepository.findWithTripById(10L)).willReturn(Optional.of(generation));
        given(placeCandidateRepository.findByGeneration_IdOrderByRankAsc(10L)).willReturn(List.of(candidate));
    }

    @Test
    void savesValidResponseAndPublishesCompletedEvent() {
        given(itineraryRepository.save(Mockito.any())).willAnswer(invocation -> invocation.getArgument(0));
        given(itineraryDayRepository.save(Mockito.any())).willAnswer(invocation -> invocation.getArgument(0));
        given(itineraryItemRepository.save(Mockito.any())).willAnswer(invocation -> invocation.getArgument(0));

        service.submit(99L, 1L, 10L, response("place-1", "Temple"));

        assertThat(generation.getStatus()).isEqualTo(ItineraryGenerationStatus.COMPLETED);
        ArgumentCaptor<ItineraryGenerationStatusChangedEvent> eventCaptor =
                ArgumentCaptor.forClass(ItineraryGenerationStatusChangedEvent.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        assertThat(eventCaptor.getValue()).satisfies(event -> {
            assertThat(event.tripId()).isEqualTo(1L);
            assertThat(event.generationId()).isEqualTo(10L);
            assertThat(event.previousStatus()).isEqualTo(ItineraryGenerationStatus.VALIDATING);
            assertThat(event.status()).isEqualTo(ItineraryGenerationStatus.COMPLETED);
            assertThat(event.candidateCount()).isEqualTo(1);
            assertThat(event.failureReason()).isNull();
        });
    }

    @Test
    void rejectsUnregisteredPlaceId() {
        AiItineraryResponse response = response("missing-place", "Temple");

        assertThatThrownBy(() -> service.submit(99L, 1L, 10L, response))
                .isInstanceOf(ItineraryException.class)
                .hasMessage("placeId is not registered in this generation.");
        verify(itineraryRepository, never()).save(Mockito.any());
    }

    @Test
    void rejectsPlaceNameMismatch() {
        AiItineraryResponse response = response("place-1", "Wrong name");

        assertThatThrownBy(() -> service.submit(99L, 1L, 10L, response))
                .isInstanceOf(ItineraryException.class)
                .hasMessage("placeName does not match the registered candidate.");
        verify(itineraryRepository, never()).save(Mockito.any());
    }

    @Test
    void rejectsDateOutsideTripRange() {
        AiItineraryResponse response = new AiItineraryResponse(
                "10",
                "summary",
                List.of(new AiItineraryResponse.Day(
                        1,
                        LocalDate.of(2026, 10, 12),
                        List.of(item("place-1", "Temple"))
                ))
        );

        assertThatThrownBy(() -> service.submit(99L, 1L, 10L, response))
                .isInstanceOf(ItineraryException.class)
                .hasMessage("day and date do not match the trip date range.");
        verify(itineraryRepository, never()).save(Mockito.any());
    }

    private AiItineraryResponse response(String placeId, String placeName) {
        return new AiItineraryResponse(
                "10",
                "summary",
                List.of(new AiItineraryResponse.Day(
                        1,
                        LocalDate.of(2026, 10, 9),
                        List.of(item(placeId, placeName))
                ))
        );
    }

    private AiItineraryResponse.Item item(String placeId, String placeName) {
        return new AiItineraryResponse.Item(
                1,
                placeId,
                placeName,
                "09:00",
                120,
                "reason"
        );
    }

    private TripEntity trip() {
        UserEntity owner = UserEntity.createOauthUser(
                "owner@example.com",
                "owner@example.com",
                "owner",
                true,
                Instant.now(clock)
        );
        TripEntity trip = TripEntity.create(
                "Trip",
                "Kyoto",
                "place-kyoto",
                "Kyoto, Japan",
                35.0,
                135.0,
                34.8,
                134.8,
                35.2,
                135.2,
                List.of("locality"),
                "locality",
                LocalDate.of(2026, 10, 9),
                LocalDate.of(2026, 10, 10),
                owner,
                Instant.now(clock)
        );
        ReflectionTestUtils.setField(trip, "id", 1L);
        return trip;
    }

    private CollectedPlaceCandidate collectedCandidate(String placeId, String name) {
        return new CollectedPlaceCandidate(
                placeId,
                name,
                "address",
                new GeoPoint(35.0, 135.0),
                "tourist_attraction",
                List.of("tourist_attraction"),
                "OPERATIONAL",
                4.5,
                100,
                List.of("09:00-18:00"),
                List.of(CandidateSearchCategory.CORE_VISIT),
                100,
                50
        );
    }
}
