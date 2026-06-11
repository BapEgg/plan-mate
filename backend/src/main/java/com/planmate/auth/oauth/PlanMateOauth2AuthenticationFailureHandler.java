package com.planmate.auth.oauth;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationFailureHandler;
import org.springframework.stereotype.Component;

@Component
public class PlanMateOauth2AuthenticationFailureHandler implements AuthenticationFailureHandler {

    private final Oauth2RedirectUrlResolver redirectUrlResolver;
    private final SimpleUrlAuthenticationFailureHandler delegate = new SimpleUrlAuthenticationFailureHandler();

    public PlanMateOauth2AuthenticationFailureHandler(Oauth2RedirectUrlResolver redirectUrlResolver) {
        this.redirectUrlResolver = redirectUrlResolver;
    }

    @Override
    public void onAuthenticationFailure(
            HttpServletRequest request,
            HttpServletResponse response,
            AuthenticationException exception
    ) throws IOException, ServletException {
        delegate.setDefaultFailureUrl(redirectUrlResolver.failureUrl());
        delegate.onAuthenticationFailure(request, response, exception);
    }

}
