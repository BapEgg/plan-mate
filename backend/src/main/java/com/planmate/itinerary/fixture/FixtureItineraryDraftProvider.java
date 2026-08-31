package com.planmate.itinerary.fixture;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.planmate.itinerary.dto.AiItineraryDraft;
import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;
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

    private final ObjectMapper objectMapper;
    private final ResourceLoader resourceLoader;

    public FixtureItineraryDraftProvider(ObjectMapper objectMapper, ResourceLoader resourceLoader) {
        this.objectMapper = objectMapper;
        this.resourceLoader = resourceLoader;
    }

    public Optional<AiItineraryDraft> load(Long generationId, int tripDayCount) {
        String fixtureLocation = switch (tripDayCount) {
            case 2 -> MINIMUM_CONDITIONS_FIXTURE;
            case 4 -> MAXIMUM_CONDITIONS_FIXTURE;
            default -> null;
        };
        if (fixtureLocation == null) {
            return Optional.empty();
        }

        AiItineraryDraft fixture = readFixture(fixtureLocation);
        return Optional.of(new AiItineraryDraft(generationId.toString(), fixture.days()));
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
