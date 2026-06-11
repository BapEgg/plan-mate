package com.planmate.auth.oauth;

import com.planmate.auth.security.JwtToken;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class Oauth2RedirectUrlResolver {

    private final String frontendBaseUrl;
    private final String successRedirectPath;
    private final String failureRedirectPath;

    public Oauth2RedirectUrlResolver(
            @Value("${app.frontend.base-url}") String frontendBaseUrl,
            @Value("${app.oauth2.success-redirect-path}") String successRedirectPath,
            @Value("${app.oauth2.failure-redirect-path}") String failureRedirectPath
    ) {
        this.frontendBaseUrl = frontendBaseUrl;
        this.successRedirectPath = successRedirectPath;
        this.failureRedirectPath = failureRedirectPath;
    }

    public String successUrl(JwtToken accessToken) {
        String fragment = "accessToken=" + encode(accessToken.value())
                + "&tokenType=Bearer"
                + "&expiresIn=" + accessToken.expiresInSeconds();
        return frontendUrl(successRedirectPath) + "#" + fragment;
    }

    public String failureUrl() {
        return frontendUrl(failureRedirectPath);
    }

    private String frontendUrl(String path) {
        String baseUrl = frontendBaseUrl.endsWith("/")
                ? frontendBaseUrl.substring(0, frontendBaseUrl.length() - 1)
                : frontendBaseUrl;
        String normalizedPath = path.startsWith("/") ? path : "/" + path;
        return baseUrl + normalizedPath;
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

}
