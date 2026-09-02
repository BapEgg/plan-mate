package com.planmate.regeneration.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.planmate.itinerary.dto.AiItineraryDraft;
import com.planmate.itinerary.dto.ItineraryDraftDay;
import com.planmate.itinerary.dto.ItineraryDraftItem;
import com.planmate.itinerary.entity.ItineraryDayEntity;
import com.planmate.itinerary.entity.ItineraryEntity;
import com.planmate.itinerary.entity.ItineraryGenerationEntity;
import com.planmate.itinerary.entity.ItineraryItemCreatedSource;
import com.planmate.itinerary.entity.ItineraryItemEntity;
import com.planmate.regeneration.entity.ItineraryRegenerationEntity;
import com.planmate.regeneration.entity.RegenerationScopeType;
import com.planmate.regeneration.exception.RegenerationErrorCode;
import com.planmate.regeneration.exception.RegenerationException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class ItineraryRegenerationServiceTest {

    private static final Instant NOW = Instant.parse("2026-09-01T00:00:00Z");

    @Test
    void rejectsGeneratedDraftThatMovesAFixedPlace() {
        Fixture fixture = fixture();
        AiItineraryDraft moved = draft("different-place", "10:00", 60);

        assertThatThrownBy(() -> ItineraryRegenerationService.verifyFixedItems(
                fixture.base(), fixture.regeneration(), moved
        ))
                .isInstanceOf(RegenerationException.class)
                .extracting(error -> ((RegenerationException) error).errorCode())
                .isEqualTo(RegenerationErrorCode.REGENERATION_FIXED_ITEM_CONFLICT);
    }

    @Test
    void allowsFixedPlaceToMoveWithinThirtyMinutes() {
        Fixture fixture = fixture();

        assertThatCode(() -> ItineraryRegenerationService.verifyFixedItems(
                fixture.base(), fixture.regeneration(), draft("fixed-place", "10:30", 60)
        )).doesNotThrowAnyException();
    }

    @Test
    void rejectsFixedPlaceThatMovesBeyondThirtyMinutes() {
        Fixture fixture = fixture();

        assertThatThrownBy(() -> ItineraryRegenerationService.verifyFixedItems(
                fixture.base(), fixture.regeneration(), draft("fixed-place", "10:31", 60)
        ))
                .isInstanceOf(RegenerationException.class)
                .extracting(error -> ((RegenerationException) error).code())
                .isEqualTo("REGENERATION_FIXED_ITEM_CONFLICT");
    }

    private Fixture fixture() {
        ItineraryGenerationEntity generation = mock(ItineraryGenerationEntity.class);
        when(generation.getTripId()).thenReturn(1L);
        ItineraryEntity base = ItineraryEntity.create(generation, NOW, 1);
        ItineraryDayEntity day = ItineraryDayEntity.create(base, 1, LocalDate.of(2026, 10, 10));
        ItineraryItemEntity item = ItineraryItemEntity.create(
                day, 1, "fixed-place", LocalTime.of(10, 0), 60, ItineraryItemCreatedSource.AI_DRAFT
        );
        ReflectionTestUtils.setField(item, "id", 101L);
        ReflectionTestUtils.setField(day, "items", new ArrayList<>(List.of(item)));
        ReflectionTestUtils.setField(base, "days", new ArrayList<>(List.of(day)));

        ItineraryRegenerationEntity regeneration = ItineraryRegenerationEntity.create(
                1L, 2L, 3L, 1, 4L, RegenerationScopeType.PARTIAL,
                1, 101L, 101L, List.of(101L), null, NOW
        );
        return new Fixture(base, regeneration);
    }

    private AiItineraryDraft draft(String placeId, String startTime, int durationMinutes) {
        return new AiItineraryDraft("2", List.of(new ItineraryDraftDay(1, List.of(
                new ItineraryDraftItem(1, placeId, startTime, durationMinutes)
        ))));
    }

    private record Fixture(ItineraryEntity base, ItineraryRegenerationEntity regeneration) {
    }
}
