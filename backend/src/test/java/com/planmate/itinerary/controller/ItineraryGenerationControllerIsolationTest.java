package com.planmate.itinerary.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.planmate.itinerary.service.AiItineraryRequestService;
import com.planmate.itinerary.service.ItineraryGenerationService;
import com.planmate.itinerary.service.ManualItineraryResponseService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

class ItineraryGenerationControllerIsolationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(ControllerConfiguration.class);

    @Test
    void regularGenerationControllerIsAvailableWhenManualHandoffIsDisabled() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(ItineraryGenerationController.class);
            assertThat(context).doesNotHaveBean(ManualItineraryGenerationController.class);
        });
    }

    @Test
    void manualHandoffControllerRequiresItsFeatureFlag() {
        contextRunner
                .withPropertyValues("app.itinerary.manual-handoff-enabled=true")
                .run(context -> {
                    assertThat(context).hasSingleBean(ItineraryGenerationController.class);
                    assertThat(context).hasSingleBean(ManualItineraryGenerationController.class);
                });
    }

    @Configuration(proxyBeanMethods = false)
    @Import({ItineraryGenerationController.class, ManualItineraryGenerationController.class})
    static class ControllerConfiguration {

        @Bean
        ItineraryGenerationService itineraryGenerationService() {
            return mock(ItineraryGenerationService.class);
        }

        @Bean
        AiItineraryRequestService aiItineraryRequestService() {
            return mock(AiItineraryRequestService.class);
        }

        @Bean
        ManualItineraryResponseService manualItineraryResponseService() {
            return mock(ManualItineraryResponseService.class);
        }
    }
}
