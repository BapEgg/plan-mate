package com.planmate.itinerary.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Queue;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;

class ItineraryGenerationRabbitConfigTest {

    @Test
    void mainQueueRoutesRejectedMessagesToConfiguredDeadLetterQueue() {
        ItineraryGenerationWorkerProperties properties = new ItineraryGenerationWorkerProperties();
        ItineraryGenerationRabbitConfig config = new ItineraryGenerationRabbitConfig(properties);

        Queue mainQueue = config.itineraryGenerationQueue();

        assertThat(mainQueue.getArguments())
                .containsEntry("x-dead-letter-exchange", properties.getDeadLetterExchange())
                .containsEntry("x-dead-letter-routing-key", properties.getDeadLetterRoutingKey());
        assertThat(config.itineraryGenerationDeadLetterQueue().getName())
                .isEqualTo(properties.getDeadLetterQueue());
        assertThat(config.itineraryGenerationDeadLetterBinding(
                config.itineraryGenerationDeadLetterQueue(),
                config.itineraryGenerationDeadLetterExchange()
        ).getRoutingKey()).isEqualTo(properties.getDeadLetterRoutingKey());
    }

    @Test
    void rejectedListenerExceptionsAreNotRequeued() throws IOException {
        List<PropertySource<?>> sources = new YamlPropertySourceLoader().load(
                "application",
                new ClassPathResource("application.yaml")
        );

        assertThat(sources)
                .anySatisfy(source -> assertThat(source.getProperty(
                        "spring.rabbitmq.listener.simple.default-requeue-rejected"
                )).isEqualTo(false));
        assertThat(sources)
                .anySatisfy(source -> assertThat(source.getProperty(
                        "spring.rabbitmq.listener.simple.acknowledge-mode"
                )).isEqualTo("auto"));
    }
}
