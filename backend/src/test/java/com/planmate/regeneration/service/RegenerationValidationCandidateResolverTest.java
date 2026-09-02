package com.planmate.regeneration.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import com.planmate.itinerary.domain.GenerationCandidateSnapshot;
import com.planmate.itinerary.entity.ItineraryDayEntity;
import com.planmate.itinerary.entity.ItineraryEntity;
import com.planmate.itinerary.entity.ItineraryGenerationEntity;
import com.planmate.itinerary.entity.ItineraryItemCreatedSource;
import com.planmate.itinerary.entity.ItineraryItemEntity;
import com.planmate.itinerary.repository.ItineraryRepository;
import com.planmate.itinerary.service.GenerationCandidateSnapshotStore;
import com.planmate.itinerary.service.PlaceDisplayResolver;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class RegenerationValidationCandidateResolverTest {

    private final ItineraryRepository itineraryRepository = mock(ItineraryRepository.class);
    private final GenerationCandidateSnapshotStore candidateStore = mock(GenerationCandidateSnapshotStore.class);
    private final PlaceDisplayResolver placeDisplayResolver = mock(PlaceDisplayResolver.class);
    private final RegenerationValidationCandidateResolver resolver = new RegenerationValidationCandidateResolver(
            itineraryRepository,
            candidateStore,
            placeDisplayResolver
    );

    @Test
    void combinesFreshCandidatesWithVerifiedCandidatesFromTheCurrentItinerary() {
        ItineraryGenerationEntity originalGeneration = mock(ItineraryGenerationEntity.class);
        given(originalGeneration.getTripId()).willReturn(1530L);
        given(originalGeneration.getId()).willReturn(1415L);
        ItineraryEntity base = itinerary(originalGeneration, "current-place");
        given(candidateStore.findAllByGenerationId(1415L)).willReturn(List.of(candidate(7, "current-place", true)));

        List<GenerationCandidateSnapshot> result = resolver.resolve(
                base,
                List.of(candidate(1, "fresh-place", false))
        );

        assertThat(result).extracting(GenerationCandidateSnapshot::placeId)
                .containsExactly("fresh-place", "current-place");
        assertThat(result.getLast().rank()).isEqualTo(2);
        assertThat(result.getLast().forcedMustVisit()).isFalse();
        verifyNoInteractions(itineraryRepository, placeDisplayResolver);
    }

    private ItineraryEntity itinerary(ItineraryGenerationEntity generation, String placeId) {
        ItineraryEntity itinerary = ItineraryEntity.create(generation, Instant.parse("2026-09-01T00:00:00Z"), 1);
        ReflectionTestUtils.setField(itinerary, "id", 505L);
        ItineraryDayEntity day = ItineraryDayEntity.create(itinerary, 1, LocalDate.of(2026, 10, 10));
        ItineraryItemEntity item = ItineraryItemEntity.create(
                day, 1, placeId, LocalTime.of(10, 0), 60, ItineraryItemCreatedSource.AI_DRAFT
        );
        ReflectionTestUtils.setField(day, "items", new ArrayList<>(List.of(item)));
        ReflectionTestUtils.setField(itinerary, "days", new ArrayList<>(List.of(day)));
        return itinerary;
    }

    private GenerationCandidateSnapshot candidate(int rank, String placeId, boolean forcedMustVisit) {
        return new GenerationCandidateSnapshot(
                rank, placeId, placeId, "address",
                new GenerationCandidateSnapshot.Location(34.0, 128.0),
                "tourist_attraction", List.of("tourist_attraction"), "OPERATIONAL",
                4.5, 100, List.of(), List.of("SIGHTSEEING"), forcedMustVisit, 10.0, 1.0
        );
    }
}
