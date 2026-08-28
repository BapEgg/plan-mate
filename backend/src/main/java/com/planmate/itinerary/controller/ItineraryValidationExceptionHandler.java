package com.planmate.itinerary.controller;

import com.planmate.itinerary.controller.response.AiItineraryValidationErrorResponse;
import com.planmate.itinerary.exception.AiItineraryValidationException;
import java.util.List;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(assignableTypes = ManualItineraryGenerationController.class)
public class ItineraryValidationExceptionHandler {

    @ExceptionHandler(AiItineraryValidationException.class)
    public ResponseEntity<AiItineraryValidationErrorResponse> handle(
            AiItineraryValidationException exception
    ) {
        return ResponseEntity
                .status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(new AiItineraryValidationErrorResponse(
                        exception.code(),
                        exception.getMessage(),
                        List.of(),
                        exception.validationReport()
                ));
    }
}
