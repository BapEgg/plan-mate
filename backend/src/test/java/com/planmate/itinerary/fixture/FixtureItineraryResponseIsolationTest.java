package com.planmate.itinerary.fixture;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.planmate.itinerary.api.RegenerationConstraintProvider;
import com.planmate.itinerary.service.GenerationCandidateSnapshotStore;
import com.planmate.itinerary.service.GenerationInputSnapshotStore;
import com.planmate.itinerary.service.ManualItineraryResponseService;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.ResourceLoader;

class FixtureItineraryResponseIsolationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(FixtureComponents.class)
            .withBean(ObjectMapper.class, ObjectMapper::new)
            .withBean(ResourceLoader.class, DefaultResourceLoader::new)
            .withBean(
                    GenerationInputSnapshotStore.class,
                    () -> Mockito.mock(GenerationInputSnapshotStore.class)
            )
            .withBean(
                    GenerationCandidateSnapshotStore.class,
                    () -> Mockito.mock(GenerationCandidateSnapshotStore.class)
            )
            .withBean(
                    RegenerationConstraintProvider.class,
                    () -> Mockito.mock(RegenerationConstraintProvider.class)
            )
            .withBean(
                    ManualItineraryResponseService.class,
                    () -> Mockito.mock(ManualItineraryResponseService.class)
            );

    @Test
    void productionDefaultsDoNotRegisterFixtureComponents() {
        contextRunner.run(context -> {
            assertThat(context).doesNotHaveBean(FixtureItineraryDraftProvider.class);
            assertThat(context).doesNotHaveBean(FixtureItineraryResponseExecutor.class);
            assertThat(context).doesNotHaveBean(FixtureItineraryResponseSubscriber.class);
        });
    }

    @Test
    void featureFlagWithoutFixtureProfileDoesNotRegisterComponents() {
        contextRunner
                .withPropertyValues("app.itinerary.fixture-response.enabled=true")
                .run(context -> {
                    assertThat(context).doesNotHaveBean(FixtureItineraryDraftProvider.class);
                    assertThat(context).doesNotHaveBean(FixtureItineraryResponseExecutor.class);
                    assertThat(context).doesNotHaveBean(FixtureItineraryResponseSubscriber.class);
                });
    }

    @Test
    void fixtureProfileWithoutFeatureFlagDoesNotRegisterComponents() {
        contextRunner
                .withInitializer(context -> context.getEnvironment().setActiveProfiles("itinerary-fixture"))
                .run(context -> {
                    assertThat(context).doesNotHaveBean(FixtureItineraryDraftProvider.class);
                    assertThat(context).doesNotHaveBean(FixtureItineraryResponseExecutor.class);
                    assertThat(context).doesNotHaveBean(FixtureItineraryResponseSubscriber.class);
                });
    }

    @Test
    void fixtureProfileAndFeatureFlagRegisterComponentsTogether() {
        contextRunner
                .withInitializer(context -> context.getEnvironment().setActiveProfiles("itinerary-fixture"))
                .withPropertyValues("app.itinerary.fixture-response.enabled=true")
                .run(context -> {
                    assertThat(context).hasSingleBean(FixtureItineraryDraftProvider.class);
                    assertThat(context).hasSingleBean(FixtureItineraryResponseExecutor.class);
                    assertThat(context).hasSingleBean(FixtureItineraryResponseSubscriber.class);
                });
    }

    @Configuration(proxyBeanMethods = false)
    @Import({
            FixtureItineraryDraftProvider.class,
            FixtureItineraryResponseExecutor.class,
            FixtureItineraryResponseSubscriber.class
    })
    static class FixtureComponents {
    }
}
