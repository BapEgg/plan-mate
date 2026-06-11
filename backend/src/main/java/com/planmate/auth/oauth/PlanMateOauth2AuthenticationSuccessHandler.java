package com.planmate.auth.oauth;

import com.planmate.auth.service.AuthTokenBundle;
import com.planmate.auth.web.RefreshTokenCookieFactory;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

@Component
public class PlanMateOauth2AuthenticationSuccessHandler implements AuthenticationSuccessHandler {

    private final OauthUserProfileExtractor profileExtractor;
    private final Oauth2LoginService oauth2LoginService;
    private final RefreshTokenCookieFactory refreshTokenCookieFactory;
    private final Oauth2RedirectUrlResolver redirectUrlResolver;
    private final AuthenticationFailureHandler failureHandler;
    private final SimpleUrlAuthenticationSuccessHandler delegate = new SimpleUrlAuthenticationSuccessHandler();

    public PlanMateOauth2AuthenticationSuccessHandler(
            OauthUserProfileExtractor profileExtractor,
            Oauth2LoginService oauth2LoginService,
            RefreshTokenCookieFactory refreshTokenCookieFactory,
            Oauth2RedirectUrlResolver redirectUrlResolver,
            PlanMateOauth2AuthenticationFailureHandler failureHandler
    ) {
        this.profileExtractor = profileExtractor;
        this.oauth2LoginService = oauth2LoginService;
        this.refreshTokenCookieFactory = refreshTokenCookieFactory;
        this.redirectUrlResolver = redirectUrlResolver;
        this.failureHandler = failureHandler;
    }

    @Override
    public void onAuthenticationSuccess(
            HttpServletRequest request,
            HttpServletResponse response,
            Authentication authentication
    ) throws IOException, ServletException {
        if (!(authentication instanceof OAuth2AuthenticationToken oauth2Authentication)) {
            failureHandler.onAuthenticationFailure(
                    request,
                    response,
                    OauthProvider.oauth2Exception("Unsupported OAuth2 authentication.")
            );
            return;
        }

        try {
            OauthUserProfile profile = profileExtractor.extract(oauth2Authentication);
            AuthTokenBundle tokens = oauth2LoginService.login(profile);
            response.addHeader(HttpHeaders.SET_COOKIE, refreshTokenCookieFactory.create(
                    tokens.refreshToken(),
                    tokens.refreshTokenTtl()
            ).toString());
            clearOauth2Session(request);
            delegate.setAlwaysUseDefaultTargetUrl(true);
            delegate.setDefaultTargetUrl(redirectUrlResolver.successUrl(tokens.accessToken()));
            delegate.onAuthenticationSuccess(request, response, authentication);
        } catch (OAuth2AuthenticationException exception) {
            clearOauth2Session(request);
            failureHandler.onAuthenticationFailure(request, response, exception);
        } catch (RuntimeException exception) {
            clearOauth2Session(request);
            failureHandler.onAuthenticationFailure(
                    request,
                    response,
                    OauthProvider.oauth2Exception("OAuth2 login failed.")
            );
        }
    }

    private void clearOauth2Session(HttpServletRequest request) {
        SecurityContextHolder.clearContext();
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
    }

}
