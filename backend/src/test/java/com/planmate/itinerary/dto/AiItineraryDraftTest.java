package com.planmate.itinerary.dto;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import org.junit.jupiter.api.Test;

class AiItineraryDraftTest {

    private final ObjectMapper objectMapper = JsonMapper.builder()
            .findAndAddModules()
            .build();

    @Test
    void deserializesExistingManualResponseJsonShape() throws Exception {
        String json = """
                {
                  "generationId": "10",
                  "days": [
                    {
                      "day": 1,
                      "items": [
                        {
                          "sequence": 1,
                          "placeId": "place-1",
                          "startTime": "09:00",
                          "durationMinutes": 60
                        }
                      ]
                    }
                  ]
                }
                """;

        AiItineraryDraft draft = objectMapper.readValue(json, AiItineraryDraft.class);

        assertThat(draft.generationId()).isEqualTo("10");
        assertThat(draft.days()).hasSize(1);
        assertThat(draft.days().getFirst().day()).isEqualTo(1);
        assertThat(draft.days().getFirst().items().getFirst().placeId()).isEqualTo("place-1");
    }
}
