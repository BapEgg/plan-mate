package com.planmate.itinerary.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.rabbit.annotation.EnableRabbit;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableRabbit
public class ItineraryGenerationRabbitConfig {

    private final ItineraryGenerationWorkerProperties properties;

    public ItineraryGenerationRabbitConfig(ItineraryGenerationWorkerProperties properties) {
        this.properties = properties;
    }

    @Bean
    public DirectExchange itineraryGenerationExchange() {
        return new DirectExchange(properties.getExchange(), true, false);
    }

    @Bean
    public DirectExchange itineraryGenerationDeadLetterExchange() {
        return new DirectExchange(properties.getDeadLetterExchange(), true, false);
    }

    @Bean
    public Queue itineraryGenerationQueue() {
        return QueueBuilder.durable(properties.getQueue())
                .deadLetterExchange(properties.getDeadLetterExchange())
                .deadLetterRoutingKey(properties.getDeadLetterRoutingKey())
                .build();
    }

    @Bean
    public Queue itineraryGenerationDeadLetterQueue() {
        return QueueBuilder.durable(properties.getDeadLetterQueue()).build();
    }

    @Bean
    public Binding itineraryGenerationBinding(
            Queue itineraryGenerationQueue,
            DirectExchange itineraryGenerationExchange
    ) {
        return BindingBuilder.bind(itineraryGenerationQueue)
                .to(itineraryGenerationExchange)
                .with(properties.getRoutingKey());
    }

    @Bean
    public Binding itineraryGenerationDeadLetterBinding(
            Queue itineraryGenerationDeadLetterQueue,
            DirectExchange itineraryGenerationDeadLetterExchange
    ) {
        return BindingBuilder.bind(itineraryGenerationDeadLetterQueue)
                .to(itineraryGenerationDeadLetterExchange)
                .with(properties.getDeadLetterRoutingKey());
    }

    @Bean
    public MessageConverter messageConverter(ObjectMapper objectMapper) {
        return new Jackson2JsonMessageConverter(objectMapper);
    }
}
