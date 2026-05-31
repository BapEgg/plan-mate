package com.planmate.auth.controller;

import com.planmate.auth.dto.AuthStatusResponse;
import com.planmate.auth.dto.SignupRequest;
import com.planmate.auth.dto.SignupResponse;
import com.planmate.auth.service.SignupService;
import jakarta.validation.Valid;
import java.net.URI;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final SignupService signupService;

    public AuthController(SignupService signupService) {
        this.signupService = signupService;
    }

    @GetMapping("/status")
    public AuthStatusResponse status() {
        return AuthStatusResponse.anonymous();
    }

    @PostMapping("/signup")
    public ResponseEntity<SignupResponse> signup(@Valid @RequestBody SignupRequest request) {
        SignupResponse response = signupService.signup(request);
        return ResponseEntity
                .created(URI.create("/api/users/" + response.id()))
                .body(response);
    }

}
