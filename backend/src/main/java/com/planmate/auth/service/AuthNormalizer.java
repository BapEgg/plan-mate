package com.planmate.auth.service;

import java.util.Locale;
import org.springframework.stereotype.Component;

@Component
public class AuthNormalizer {

    public String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    public String normalizeLoginId(String loginId) {
        return loginId.trim();
    }

}
