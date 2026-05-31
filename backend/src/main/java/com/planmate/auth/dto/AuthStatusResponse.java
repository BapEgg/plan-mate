package com.planmate.auth.dto;

public record AuthStatusResponse(boolean authenticated) {

    public static AuthStatusResponse anonymous() {
        return new AuthStatusResponse(false);
    }

}
