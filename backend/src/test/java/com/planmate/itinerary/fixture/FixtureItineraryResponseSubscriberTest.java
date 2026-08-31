package com.planmate.itinerary.fixture;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.planmate.itinerary.api.ItineraryGenerationStatus;
import com.planmate.itinerary.api.event.ItineraryGenerationStatusChangedEvent;
import com.planmate.itinerary.domain.GenerationInputSnapshot;
import com.planmate.itinerary.dto.AiItineraryDraft;
import com.planmate.itinerary.dto.ItineraryDraftDay;
import com.planmate.itinerary.dto.ItineraryDraftItem;
import com.planmate.itinerary.service.GenerationInputSnapshotStore;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class FixtureItineraryResponseSubscriberTest {

    private final GenerationInputSnapshotStore snapshotStore = Mockito.mock(GenerationInputSnapshotStore.class);
    private final FixtureItineraryDraftProvider fixtureProvider = Mockito.mock(FixtureItineraryDraftProvider.class);
    private final FixtureItineraryResponseExecutor responseExecutor =
            Mockito.mock(FixtureItineraryResponseExecutor.class);
    private final FixtureItineraryResponseSubscriber subscriber = new FixtureItineraryResponseSubscriber(
            snapshotStore,
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
        given(fixtureProvider.load(1360L, 2)).willReturn(Optional.of(draft));

        subscriber.handle(event(ItineraryGenerationStatus.READY_FOR_PLANNING));

        verify(responseExecutor).submit(1484L, 1360L, draft);
    }

    @Test
    void ignoresOtherGenerationStatuses() {
        subscriber.handle(event(ItineraryGenerationStatus.COLLECTING_CANDIDATES));

        verifyNoInteractions(snapshotStore, fixtureProvider, responseExecutor);
    }

    @Test
    void doesNotBypassManualHandoffForUnsupportedDurations() {
        GenerationInputSnapshot snapshot = Mockito.mock(GenerationInputSnapshot.class);
        given(snapshotStore.getRequired(1360L)).willReturn(snapshot);
        given(snapshot.tripDayCount()).willReturn(3);
        given(fixtureProvider.load(1360L, 3)).willReturn(Optional.empty());

        subscriber.handle(event(ItineraryGenerationStatus.READY_FOR_PLANNING));

        verifyNoInteractions(responseExecutor);
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
