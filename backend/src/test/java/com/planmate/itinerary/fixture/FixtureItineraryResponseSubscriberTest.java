package com.planmate.itinerary.fixture;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.planmate.itinerary.api.ItineraryGenerationStatus;
import com.planmate.itinerary.api.RegenerationConstraintProvider;
import com.planmate.itinerary.api.event.ItineraryGenerationStatusChangedEvent;
import com.planmate.itinerary.domain.GenerationCandidateSnapshot;
import com.planmate.itinerary.domain.GenerationInputSnapshot;
import com.planmate.itinerary.dto.AiItineraryDraft;
import com.planmate.itinerary.dto.ItineraryDraftDay;
import com.planmate.itinerary.dto.ItineraryDraftItem;
import com.planmate.itinerary.service.GenerationCandidateSnapshotStore;
import com.planmate.itinerary.service.GenerationInputSnapshotStore;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class FixtureItineraryResponseSubscriberTest {

    private final GenerationInputSnapshotStore snapshotStore = Mockito.mock(GenerationInputSnapshotStore.class);
    private final GenerationCandidateSnapshotStore candidateStore = Mockito.mock(GenerationCandidateSnapshotStore.class);
    private final RegenerationConstraintProvider constraintProvider = Mockito.mock(RegenerationConstraintProvider.class);
    private final FixtureItineraryDraftProvider fixtureProvider = Mockito.mock(FixtureItineraryDraftProvider.class);
    private final FixtureItineraryResponseExecutor responseExecutor =
            Mockito.mock(FixtureItineraryResponseExecutor.class);
    private final FixtureItineraryResponseSubscriber subscriber = new FixtureItineraryResponseSubscriber(
            snapshotStore,
            candidateStore,
            constraintProvider,
            fixtureProvider,
            responseExecutor
    );

    @Test
    void submitsFixtureOnlyAfterCandidatesBecomeReady() {
        GenerationInputSnapshot snapshot = Mockito.mock(GenerationInputSnapshot.class);
        AiItineraryDraft draft = new AiItineraryDraft(
                "1360",
                List.of(new ItineraryDraftDay(
                        1,
                        List.of(new ItineraryDraftItem(1, "place-1", "09:00", 60))
                ))
        );
        given(snapshotStore.getRequired(1360L)).willReturn(snapshot);
        given(snapshot.tripDayCount()).willReturn(2);
        given(candidateStore.findAllByGenerationId(1360L)).willReturn(List.of(candidate("candidate-place")));
        given(constraintProvider.findByGenerationId(1360L)).willReturn(Optional.of(new RegenerationConstraintProvider.Constraint(
                "FULL", null, null, null, List.of(), null,
                List.of(new RegenerationConstraintProvider.Item(1L, 1, 1, "current-place", "09:00", 60, "REPLACE"))
        )));
        given(fixtureProvider.load(1360L, 2, java.util.Set.of("candidate-place", "current-place")))
                .willReturn(Optional.of(draft));

        subscriber.handle(event(ItineraryGenerationStatus.READY_FOR_PLANNING));

        verify(responseExecutor).submit(1484L, 1360L, draft);
    }

    @Test
    void ignoresOtherGenerationStatuses() {
        subscriber.handle(event(ItineraryGenerationStatus.COLLECTING_CANDIDATES));

        verifyNoInteractions(snapshotStore, candidateStore, constraintProvider, fixtureProvider, responseExecutor);
    }

    @Test
    void doesNotBypassManualHandoffForUnsupportedDurations() {
        GenerationInputSnapshot snapshot = Mockito.mock(GenerationInputSnapshot.class);
        given(snapshotStore.getRequired(1360L)).willReturn(snapshot);
        given(snapshot.tripDayCount()).willReturn(3);
        given(candidateStore.findAllByGenerationId(1360L)).willReturn(List.of());
        given(constraintProvider.findByGenerationId(1360L)).willReturn(Optional.empty());
        given(fixtureProvider.load(1360L, 3, java.util.Set.of())).willReturn(Optional.empty());

        subscriber.handle(event(ItineraryGenerationStatus.READY_FOR_PLANNING));

        verifyNoInteractions(responseExecutor);
    }

    private GenerationCandidateSnapshot candidate(String placeId) {
        return new GenerationCandidateSnapshot(
                1, placeId, placeId, null,
                new GenerationCandidateSnapshot.Location(34.0, 128.0),
                null, List.of(), null, null, null, List.of(), List.of(), false, null, 0
        );
    }

    private ItineraryGenerationStatusChangedEvent event(ItineraryGenerationStatus status) {
        return new ItineraryGenerationStatusChangedEvent(
                1484L,
                1360L,
                ItineraryGenerationStatus.COLLECTING_CANDIDATES,
                status,
                120,
                null,
                Instant.parse("2026-08-31T00:00:00Z")
        );
    }
}
