package com.planmate.itinerary.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.planmate.itinerary.dto.AiItineraryRequest;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.util.StreamUtils;

@Service
public class ItineraryPromptService {

    public static final String PROMPT_VERSION = "itinerary-plan-v1";
    private static final String TEMPLATE_PATH = "prompts/itinerary-plan-v1.txt";
    private static final String AI_REQUEST_PLACEHOLDER = "{{AI_REQUEST_JSON}}";

    private final ObjectMapper objectMapper;

    public ItineraryPromptService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public String createPrompt(AiItineraryRequest request) {
        return loadTemplate().replace(AI_REQUEST_PLACEHOLDER, writeJson(request));
    }

    private String loadTemplate() {
        try {
            ClassPathResource resource = new ClassPathResource(TEMPLATE_PATH);
            return StreamUtils.copyToString(resource.getInputStream(), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new IllegalStateException("Itinerary prompt template cannot be loaded.", exception);
        }
    }

    private String writeJson(AiItineraryRequest request) {
        try {
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(request);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("AI itinerary request cannot be serialized.", exception);
        }
    }
}
