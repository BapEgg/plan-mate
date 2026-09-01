package com.planmate.itinerary.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.planmate.itinerary.dto.AiItineraryDraft;
import com.planmate.itinerary.dto.ItineraryDraftDay;
import com.planmate.itinerary.dto.ItineraryDraftItem;
import com.planmate.itinerary.entity.ItineraryDayEntity;
import com.planmate.itinerary.entity.ItineraryEntity;
import com.planmate.itinerary.entity.ItineraryGenerationEntity;
import com.planmate.itinerary.entity.ItineraryItemCreatedSource;
import com.planmate.itinerary.entity.ItineraryItemEntity;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class AiItineraryDraftNormalizerTest {

    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");

    private final AiItineraryDraftNormalizer normalizer = new AiItineraryDraftNormalizer();

    @Test
    void normalizesIncomingDraftByMeaning() {
        AiItineraryDraft draft = new AiItineraryDraft(
                "10",
                List.of(
                        day(2, List.of(
                                item(2, "place-3", "13:00", 60),
                                item(1, " place-2 ", "09:00", 120)
                        )),
                        day(1, List.of(item(1, "place-1", "09:00", 120)))
                )
        );

        NormalizedAiItineraryDraft normalized = normalizer.normalize(10L, draft);

        assertThat(normalized.days())
                .extracting(NormalizedAiItineraryDraft.Day::day)
                .containsExactly(1, 2);
        assertThat(normalized.days().get(1).items())
                .extracting(NormalizedAiItineraryDraft.Item::placeId)
                .containsExactly("place-2", "place-3");
        assertThat(normalized.days().get(1).items())
                .extracting(NormalizedAiItineraryDraft.Item::startTime)
                .containsExactly(LocalTime.of(9, 0), LocalTime.of(13, 0));
    }

    @Test
    void normalizesPersistedItineraryToSameCanonicalModel() {
        AiItineraryDraft incoming = new AiItineraryDraft(
                "10",
                List.of(
                        day(1, List.of(item(1, "place-1", "09:00", 120))),
                        day(2, List.of(item(1, " place-2 ", "10:00", 60)))
                )
        );
        ItineraryEntity persisted = persistedItinerary(List.of(
                persistedDay(2, List.of(persistedItem(1, "place-2", "10:00", 60))),
                persistedDay(1, List.of(persistedItem(1, "place-1", "09:00", 120)))
        ));

        assertThat(normalizer.normalize(10L, incoming))
                .isEqualTo(normalizer.normalize(persisted));
    }

    @Test
    void treatsCaseChangedPlaceIdAsDifferent() {
        AiItineraryDraft lowerCase = draft(item(1, "place-1", "09:00", 120));
        AiItineraryDraft upperCase = draft(item(1, "PLACE-1", "09:00", 120));

        assertThat(normalizer.normalize(10L, lowerCase))
                .isNotEqualTo(normalizer.normalize(10L, upperCase));
    }

    @Test
    void detectsMeaningfulDifferences() {
        NormalizedAiItineraryDraft base = normalizer.normalize(10L, new AiItineraryDraft(
                "10",
                List.of(
                        day(1, List.of(item(1, "place-1", "09:00", 120))),
                        day(2, List.of(item(1, "place-2", "10:00", 60)))
                )
        ));

        assertThat(base).isNotEqualTo(normalizer.normalize(10L, new AiItineraryDraft(
                "10",
                List.of(
                        day(1, List.of(item(1, "place-1", "09:00", 120))),
                        day(2, List.of(item(1, "place-3", "10:00", 60)))
                )
        )));
        assertThat(base).isNotEqualTo(normalizer.normalize(10L, new AiItineraryDraft(
                "10",
                List.of(
                        day(1, List.of(item(1, "place-1", "09:30", 120))),
                        day(2, List.of(item(1, "place-2", "10:00", 60)))
                )
        )));
        assertThat(base).isNotEqualTo(normalizer.normalize(10L, new AiItineraryDraft(
                "10",
                List.of(
                        day(1, List.of(item(1, "place-1", "09:00", 90))),
                        day(2, List.of(item(1, "place-2", "10:00", 60)))
                )
        )));
        assertThat(base).isNotEqualTo(normalizer.normalize(10L, new AiItineraryDraft(
                "10",
                List.of(
                        day(1, List.of(item(2, "place-1", "09:00", 120))),
                        day(2, List.of(item(1, "place-2", "10:00", 60)))
                )
        )));
        assertThat(base).isNotEqualTo(normalizer.normalize(10L, new AiItineraryDraft(
                "10",
                List.of(day(1, List.of(item(1, "place-1", "09:00", 120))))
        )));
    }

    @Test
    void trimsPlaceIdsButDoesNotMutateOriginalDraft() {
        AiItineraryDraft draft = draft(item(1, " place-1 ", "09:00", 120));

        NormalizedAiItineraryDraft normalized = normalizer.normalize(10L, draft);

        assertThat(normalized.days().get(0).items().get(0).placeId()).isEqualTo("place-1");
        assertThat(draft.days().get(0).items().get(0).placeId()).isEqualTo(" place-1 ");
    }

    @Test
    void defendsInvalidInternalCallsWithoutUserFacingItineraryException() {
        assertThatThrownBy(() -> normalizer.normalize(10L, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("draft must not be null");
        assertThatThrownBy(() -> normalizer.normalize(10L, draft(item(1, "place-1", "9am", 120))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("startTime");
    }

    private AiItineraryDraft draft(ItineraryDraftItem item) {
        return new AiItineraryDraft("10", List.of(day(1, List.of(item))));
    }

    private ItineraryDraftDay day(int day, List<ItineraryDraftItem> items) {
        return new ItineraryDraftDay(day, items);
    }

    private ItineraryDraftItem item(int sequence, String placeId, String startTime, int durationMinutes) {
        return new ItineraryDraftItem(sequence, placeId, startTime, durationMinutes);
    }

    private ItineraryEntity persistedItinerary(List<ItineraryDayEntity> days) {
        ItineraryGenerationEntity generation = ItineraryGenerationEntity.create(45L, "test", NOW);
        ReflectionTestUtils.setField(generation, "id", 10L);
        ItineraryEntity itinerary = ItineraryEntity.create(generation, NOW, 1);
        ReflectionTestUtils.setField(itinerary, "days", days);
        return itinerary;
    }

    private ItineraryDayEntity persistedDay(int day, List<ItineraryItemEntity> items) {
        ItineraryGenerationEntity generation = ItineraryGenerationEntity.create(45L, "test", NOW);
        ItineraryEntity itinerary = ItineraryEntity.create(generation, NOW, 1);
        ItineraryDayEntity entity = ItineraryDayEntity.create(itinerary, day, LocalDate.of(2026, 10, 8).plusDays(day));
        ReflectionTestUtils.setField(entity, "items", items);
        items.forEach(item -> ReflectionTestUtils.setField(item, "day", entity));
        return entity;
    }

    private ItineraryItemEntity persistedItem(int sequence, String placeId, String startTime, int durationMinutes) {
        ItineraryGenerationEntity generation = ItineraryGenerationEntity.create(45L, "test", NOW);
        ItineraryDayEntity day = ItineraryDayEntity.create(
                ItineraryEntity.create(generation, NOW, 1),
                1,
                LocalDate.of(2026, 10, 9)
        );
        return ItineraryItemEntity.create(
                day,
                sequence,
                placeId,
                LocalTime.parse(startTime),
                durationMinutes,
                ItineraryItemCreatedSource.AI_DRAFT
        );
    }
}
