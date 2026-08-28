package com.planmate.place.controller;

import com.planmate.common.exception.ApiErrorResponse;
import com.planmate.place.api.exception.PlaceApiException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class PlaceExceptionHandler {

    @ExceptionHandler(PlaceApiException.class)
    public ResponseEntity<ApiErrorResponse> handlePlaceApiException(PlaceApiException exception) {
        return ResponseEntity
                .status(HttpStatus.valueOf(exception.statusCode()))
                .body(ApiErrorResponse.of(exception.code(), exception.getMessage()));
    }
}
