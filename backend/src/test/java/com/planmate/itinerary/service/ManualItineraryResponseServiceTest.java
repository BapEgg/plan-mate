package com.planmate.itinerary.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.planmate.itinerary.dto.GroundedItineraryDraft;
import com.planmate.itinerary.dto.ItineraryDraftDay;
import com.planmate.itinerary.dto.ItineraryDraftItem;
import com.planmate.itinerary.entity.ItineraryGenerationEntity;
import com.planmate.itinerary.entity.ItineraryGenerationStatus;
import com.planmate.itinerary.entity.ItineraryItemEntity;
import com.planmate.itinerary.exception.ItineraryException;
import com.planmate.itinerary.realtime.ItineraryGenerationStatusChangedEvent;
import com.planmate.itinerary.repository.ItineraryDayRepository;
import com.planmate.itinerary.repository.ItineraryGenerationRepository;
import com.planmate.itinerary.repository.ItineraryItemRepository;
import com.planmate.itinerary.repository.ItineraryRepository;
import com.planmate.trip.domain.MustVisitPlaceSnapshot;
import com.planmate.trip.entity.TripEntity;
import com.planmate.trip.entity.TripPlanningProfileEntity;
import com.planmate.trip.repository.TripPlanningProfileRepository;
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
    private final TripPlanningProfileRepository tripPlanningProfileRepository = Mockito.mock(TripPlanningProfileRepository.class);
    private final ItineraryRepository itineraryRepository = Mockito.mock(ItineraryRepository.class);
    private final ItineraryDayRepository itineraryDayRepository = Mockito.mock(ItineraryDayRepository.class);
    private final ItineraryItemRepository itineraryItemRepository = Mockito.mock(ItineraryItemRepository.class);
    private final ApplicationEventPublisher eventPublisher = Mockito.mock(ApplicationEventPublisher.class);
    private final Clock clock = Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC);
    private final ManualItineraryResponseService service = new ManualItineraryResponseService(
            tripRepository,
            generationRepository,
            tripPlanningProfileRepository,
            itineraryRepository,
            itineraryDayRepository,
            itineraryItemRepository,
            clock,
            eventPublisher
    );

    private TripEntity trip;
    private ItineraryGenerationEntity generation;
    private TripPlanningProfileEntity profile;

    @BeforeEach
    void setUp() {
        trip = trip();
        generation = ItineraryGenerationEntity.create(trip, ItineraryPromptService.PROMPT_VERSION, Instant.now(clock));
        generation.markReady(Instant.now(clock));
        ReflectionTestUtils.setField(generation, "id", 10L);
        profile = Mockito.mock(TripPlanningProfileEntity.class);

        given(tripRepository.findAccessibleTrip(1L, 99L)).willReturn(Optional.of(trip));
        given(generationRepository.findWithTripById(10L)).willReturn(Optional.of(generation));
        given(tripPlanningProfileRepository.findByTrip_Id(1L)).willReturn(Optional.of(profile));
        given(profile.getMustVisitPlaces()).willReturn(List.of(mustVisitPlace("place-1"), mustVisitPlace("place-2")));
    }

    @Test
    void savesPlaceIdOnlyDraftAndPublishesCompletedEvent() {
        given(itineraryRepository.save(Mockito.any())).willAnswer(invocation -> invocation.getArgument(0));
        given(itineraryDayRepository.save(Mockito.any())).willAnswer(invocation -> invocation.getArgument(0));
        given(itineraryItemRepository.save(Mockito.any())).willAnswer(invocation -> invocation.getArgument(0));

        service.submit(99L, 1L, 10L, validDraft());

        assertThat(generation.getStatus()).isEqualTo(ItineraryGenerationStatus.COMPLETED);
        ArgumentCaptor<ItineraryItemEntity> itemCaptor = ArgumentCaptor.forClass(ItineraryItemEntity.class);
        verify(itineraryItemRepository, Mockito.times(2)).save(itemCaptor.capture());
        assertThat(itemCaptor.getAllValues())
                .extracting(ItineraryItemEntity::getPlaceId)
                .containsExactly("place-1", "place-2");

        ArgumentCaptor<ItineraryGenerationStatusChangedEvent> eventCaptor =
                ArgumentCaptor.forClass(ItineraryGenerationStatusChangedEvent.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        assertThat(eventCaptor.getValue()).satisfies(event -> {
            assertThat(event.tripId()).isEqualTo(1L);
            assertThat(event.generationId()).isEqualTo(10L);
            assertThat(event.previousStatus()).isEqualTo(ItineraryGenerationStatus.VALIDATING);
            assertThat(event.status()).isEqualTo(ItineraryGenerationStatus.COMPLETED);
            assertThat(event.candidateCount()).isZero();
            assertThat(event.failureReason()).isNull();
        });
    }

    @Test
    void rejectsDraftWhenDayCountDoesNotMatchTripDuration() {
        GroundedItineraryDraft draft = new GroundedItineraryDraft(
                "10",
                List.of(day(1, item(1, "place-1")))
        );

        assertThatThrownBy(() -> service.submit(99L, 1L, 10L, draft))
                .isInstanceOf(ItineraryException.class)
                .hasMessage("days 개수는 여행 일수와 일치해야 합니다.");
        verify(itineraryRepository, never()).save(Mockito.any());
    }

    @Test
    void rejectsDraftWhenMustVisitPlaceIsMissing() {
        GroundedItineraryDraft draft = new GroundedItineraryDraft(
                "10",
                List.of(
                        day(1, item(1, "place-1")),
                        day(2, item(1, "place-3"))
                )
        );

        assertThatThrownBy(() -> service.submit(99L, 1L, 10L, draft))
                .isInstanceOf(ItineraryException.class)
                .hasMessage("mustVisitPlaceIds는 일정에 포함되어야 합니다.");
        verify(itineraryRepository, never()).save(Mockito.any());
    }

    @Test
    void rejectsDraftWhenStartTimeIsInvalid() {
        GroundedItineraryDraft draft = new GroundedItineraryDraft(
                "10",
                List.of(
                        day(1, new ItineraryDraftItem(1, "place-1", "9am", 120)),
                        day(2, item(1, "place-2"))
                )
        );

        assertThatThrownBy(() -> service.submit(99L, 1L, 10L, draft))
                .isInstanceOf(ItineraryException.class)
                .hasMessage("startTime은 HH:mm 형식이어야 합니다.");
        verify(itineraryRepository, never()).save(Mockito.any());
    }

    private GroundedItineraryDraft validDraft() {
        return new GroundedItineraryDraft(
                "10",
                List.of(
                        day(1, item(1, "place-1")),
                        day(2, item(1, "place-2"))
                )
        );
    }

    private ItineraryDraftDay day(int day, ItineraryDraftItem item) {
        return new ItineraryDraftDay(day, List.of(item));
    }

    private ItineraryDraftItem item(int sequence, String placeId) {
        return new ItineraryDraftItem(sequence, placeId, "09:00", 120);
    }

    private MustVisitPlaceSnapshot mustVisitPlace(String placeId) {
        return new MustVisitPlaceSnapshot(
                placeId,
                "장소",
                "주소",
                35.0,
                135.0,
                List.of("tourist_attraction"),
                "tourist_attraction"
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
}
