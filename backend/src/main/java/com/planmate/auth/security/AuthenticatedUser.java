package com.planmate.auth.security;

public record AuthenticatedUser(Long userId, String role) {
}
