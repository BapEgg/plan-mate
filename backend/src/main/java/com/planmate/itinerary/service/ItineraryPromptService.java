package com.planmate.itinerary.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.planmate.itinerary.dto.AiItineraryRequest;
import com.planmate.itinerary.exception.ItineraryErrorCode;
import com.planmate.itinerary.exception.ItineraryException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.util.StreamUtils;

@Service
public class ItineraryPromptService {

    public static final String VERSION_V1 = "itinerary-plan-v1";
    public static final String VERSION_V2 = "itinerary-plan-v2";
    public static final String CURRENT_PROMPT_VERSION = VERSION_V2;

    private static final String TEMPLATE_PATH_V1 = "prompts/itinerary-plan-v1.txt";
    private static final String TEMPLATE_PATH_V2 = "prompts/itinerary-plan-v2.txt";
    private static final String AI_REQUEST_PLACEHOLDER = "{{AI_REQUEST_JSON}}";

    private final ObjectMapper objectMapper;

    public ItineraryPromptService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public String createPrompt(String promptVersion, AiItineraryRequest request) {
        return loadTemplate(promptVersion).replace(AI_REQUEST_PLACEHOLDER, writeJson(request));
    }

    private String loadTemplate(String promptVersion) {
        try {
            ClassPathResource resource = new ClassPathResource(templatePath(promptVersion));
            return StreamUtils.copyToString(resource.getInputStream(), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new IllegalStateException("Itinerary prompt template cannot be loaded.", exception);
        }
    }

    private String templatePath(String promptVersion) {
        return switch (promptVersion) {
            case VERSION_V1 -> TEMPLATE_PATH_V1;
            case VERSION_V2 -> TEMPLATE_PATH_V2;
            default -> throw new ItineraryException(ItineraryErrorCode.UNSUPPORTED_PROMPT_VERSION);
        };
    }

    private String writeJson(AiItineraryRequest request) {
        try {
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(request);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("AI itinerary request cannot be serialized.", exception);
        }
    }
}
