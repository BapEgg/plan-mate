package com.planmate.common.exception;

import static org.assertj.core.api.Assertions.assertThat;

import com.planmate.auth.exception.InvalidCredentialsException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler exceptionHandler = new GlobalExceptionHandler();

    @Test
    void handlesPlanMateExceptionByErrorCode() {
        InvalidCredentialsException exception = new InvalidCredentialsException();

        ResponseEntity<ApiErrorResponse> response = exceptionHandler.handlePlanMateException(exception);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody()).isEqualTo(ApiErrorResponse.of(
                "INVALID_CREDENTIALS",
                "Invalid login id or password."
        ));
    }

    @Test
    void handlesDataIntegrityViolationAsDataConflict() {
        ResponseEntity<ApiErrorResponse> response = exceptionHandler.handleDataIntegrityViolation();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).isEqualTo(ApiErrorResponse.of(CommonErrorCode.DATA_CONFLICT));
    }

}
