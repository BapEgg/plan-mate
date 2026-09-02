package com.planmate.regeneration.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.planmate.itinerary.dto.AiItineraryDraft;
import com.planmate.itinerary.dto.ItineraryDraftDay;
import com.planmate.itinerary.dto.ItineraryDraftItem;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class ItineraryRegenerationEntityTest {

    private static final Instant CREATED_AT = Instant.parse("2026-09-01T00:00:00Z");

    @Test
    void reviewDraftCanBeRejectedWithoutApplyingAnItinerary() {
        ItineraryRegenerationEntity regeneration = partial();
        regeneration.markReady(draft(), CREATED_AT.plusSeconds(10));

        regeneration.markRejected(CREATED_AT.plusSeconds(20));

        assertThat(regeneration.getStatus()).isEqualTo(ItineraryRegenerationStatus.REJECTED);
        assertThat(regeneration.getAppliedItineraryId()).isNull();
        assertThat(regeneration.getDraftPayload()).isEqualTo(draft());
    }

    @Test
    void onlyReviewableDraftCanBeApplied() {
        ItineraryRegenerationEntity regeneration = partial();

        assertThatThrownBy(() -> regeneration.markApplied(99L, CREATED_AT.plusSeconds(20)))
                .isInstanceOf(IllegalStateException.class);

        regeneration.markReady(draft(), CREATED_AT.plusSeconds(10));
        regeneration.markApplied(99L, CREATED_AT.plusSeconds(20));

        assertThat(regeneration.getStatus()).isEqualTo(ItineraryRegenerationStatus.APPLIED);
        assertThat(regeneration.getAppliedItineraryId()).isEqualTo(99L);
    }

    @Test
    void fixedItemSnapshotCannotBeMutatedByCaller() {
        List<Long> fixed = new java.util.ArrayList<>(List.of(11L));
        ItineraryRegenerationEntity regeneration = ItineraryRegenerationEntity.create(
                1L, 2L, 3L, 4, 5L, RegenerationScopeType.PARTIAL,
                1, 10L, 12L, fixed, null, CREATED_AT
        );

        fixed.add(12L);

        assertThat(regeneration.getFixedItemIds()).containsExactly(11L);
        assertThatThrownBy(() -> regeneration.getFixedItemIds().add(13L))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    private ItineraryRegenerationEntity partial() {
        return ItineraryRegenerationEntity.create(
                1L, 2L, 3L, 4, 5L, RegenerationScopeType.PARTIAL,
                1, 10L, 12L, List.of(11L), "카페는 유지", CREATED_AT
        );
    }

    private AiItineraryDraft draft() {
        return new AiItineraryDraft("2", List.of(new ItineraryDraftDay(1, List.of(
                new ItineraryDraftItem(1, "place-a", "10:00", 60)
        ))));
    }
}
