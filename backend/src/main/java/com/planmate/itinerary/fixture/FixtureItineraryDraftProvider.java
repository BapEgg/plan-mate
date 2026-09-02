package com.planmate.itinerary.fixture;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.planmate.itinerary.dto.AiItineraryDraft;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;

@Component
@Profile("itinerary-fixture")
@ConditionalOnProperty(
        prefix = "app.itinerary.fixture-response",
        name = "enabled",
        havingValue = "true"
)
public class FixtureItineraryDraftProvider {

    private static final String MINIMUM_CONDITIONS_FIXTURE =
            "classpath:fixtures/itinerary/manual-itinerary-1360-min.json";
    private static final String MAXIMUM_CONDITIONS_FIXTURE =
            "classpath:fixtures/itinerary/manual-itinerary-1368-max.json";
    private static final String GEOJE_MAXIMUM_CONDITIONS_FIXTURE =
            "classpath:fixtures/itinerary/manual-itinerary-1413-geoje-max.json";

    private final ObjectMapper objectMapper;
    private final ResourceLoader resourceLoader;

    public FixtureItineraryDraftProvider(ObjectMapper objectMapper, ResourceLoader resourceLoader) {
        this.objectMapper = objectMapper;
        this.resourceLoader = resourceLoader;
    }

    public Optional<AiItineraryDraft> load(
            Long generationId,
            int tripDayCount,
            Set<String> allowedPlaceIds
    ) {
        List<String> fixtureLocations = switch (tripDayCount) {
            case 2 -> List.of(MINIMUM_CONDITIONS_FIXTURE);
            case 4 -> List.of(GEOJE_MAXIMUM_CONDITIONS_FIXTURE, MAXIMUM_CONDITIONS_FIXTURE);
            default -> List.of();
        };
        Set<String> safeAllowedPlaceIds = allowedPlaceIds == null ? Set.of() : Set.copyOf(allowedPlaceIds);
        for (String fixtureLocation : fixtureLocations) {
            AiItineraryDraft fixture = readFixture(fixtureLocation);
            boolean compatible = fixture.days().stream()
                    .flatMap(day -> day.items().stream())
                    .allMatch(item -> safeAllowedPlaceIds.contains(item.placeId()));
            if (compatible) {
                return Optional.of(new AiItineraryDraft(generationId.toString(), fixture.days()));
            }
        }
        return Optional.empty();
    }

    private AiItineraryDraft readFixture(String fixtureLocation) {
        Resource fixtureResource = resourceLoader.getResource(fixtureLocation);
        try (InputStream inputStream = fixtureResource.getInputStream()) {
            return objectMapper.readValue(inputStream, AiItineraryDraft.class);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to load itinerary fixture: " + fixtureLocation, exception);
        }
    }
}
