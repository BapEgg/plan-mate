package com.planmate.auth.controller;

import com.planmate.auth.dto.AuthStatusResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    @GetMapping("/status")
    public AuthStatusResponse status() {
        return AuthStatusResponse.anonymous();
    }

}
