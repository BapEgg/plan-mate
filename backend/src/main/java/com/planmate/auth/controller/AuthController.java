package com.planmate.auth.controller;

import com.planmate.auth.dto.AuthStatusResponse;
import com.planmate.auth.dto.EmailRequest;
import com.planmate.auth.dto.EmailVerificationConfirmRequest;
import com.planmate.auth.dto.EmailVerificationResponse;
import com.planmate.auth.dto.GuidanceResponse;
import com.planmate.auth.dto.LoginIdRecoveryConfirmRequest;
import com.planmate.auth.dto.LoginIdRecoveryRequest;
import com.planmate.auth.dto.LoginIdRecoveryResponse;
import com.planmate.auth.dto.LoginRequest;
import com.planmate.auth.dto.LoginResponse;
import com.planmate.auth.dto.PasswordResetConfirmRequest;
import com.planmate.auth.dto.PasswordResetRequest;
import com.planmate.auth.dto.PasswordResetResponse;
import com.planmate.auth.dto.SignupRequest;
import com.planmate.auth.dto.SignupResponse;
import com.planmate.auth.dto.TokenRefreshResponse;
import com.planmate.auth.security.AuthenticatedUser;
import com.planmate.auth.security.JwtToken;
import com.planmate.auth.service.AccountRecoveryService;
import com.planmate.auth.service.AuthTokenService;
import com.planmate.auth.service.EmailVerificationService;
import com.planmate.auth.service.LoginResult;
import com.planmate.auth.service.LoginService;
import com.planmate.auth.service.SignupService;
import com.planmate.user.dto.MeResponse;
import com.planmate.user.service.UserQueryService;
import jakarta.validation.Valid;
import java.net.URI;
import java.time.Duration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private static final String REFRESH_TOKEN_COOKIE = "refreshToken";

    private final SignupService signupService;
    private final EmailVerificationService emailVerificationService;
    private final LoginService loginService;
    private final AuthTokenService authTokenService;
    private final UserQueryService userQueryService;
    private final AccountRecoveryService accountRecoveryService;

    public AuthController(
            SignupService signupService,
            EmailVerificationService emailVerificationService,
            LoginService loginService,
            AuthTokenService authTokenService,
            UserQueryService userQueryService,
            AccountRecoveryService accountRecoveryService
    ) {
        this.signupService = signupService;
        this.emailVerificationService = emailVerificationService;
        this.loginService = loginService;
        this.authTokenService = authTokenService;
        this.userQueryService = userQueryService;
        this.accountRecoveryService = accountRecoveryService;
    }

    @GetMapping("/status")
    public AuthStatusResponse status(@AuthenticationPrincipal AuthenticatedUser user) {
        if (user == null) {
            return AuthStatusResponse.anonymous();
        }

        MeResponse me = userQueryService.getMe(user.userId());
        return AuthStatusResponse.authenticated(new AuthStatusResponse.AuthUserSummary(
                me.id(),
                me.loginId(),
                me.nickname(),
                me.role().name()
        ));
    }

    @PostMapping("/signup")
    public ResponseEntity<SignupResponse> signup(@Valid @RequestBody SignupRequest request) {
        SignupResponse response = signupService.signup(request);
        return ResponseEntity
                .created(URI.create("/api/users/" + response.id()))
                .body(response);
    }

    @PostMapping("/email-verifications/confirm")
    public EmailVerificationResponse confirmEmail(@Valid @RequestBody EmailVerificationConfirmRequest request) {
        return emailVerificationService.confirm(request.token());
    }

    @PostMapping("/email-verifications/resend")
    public GuidanceResponse resendEmailVerification(@Valid @RequestBody EmailRequest request) {
        emailVerificationService.resend(request.email());
        return GuidanceResponse.verificationSentIfPossible();
    }

    @PostMapping("/login-id-recoveries")
    public GuidanceResponse requestLoginIdRecovery(@Valid @RequestBody LoginIdRecoveryRequest request) {
        accountRecoveryService.requestLoginIdRecovery(request.email());
        return GuidanceResponse.recoverySentIfPossible();
    }

    @PostMapping("/login-id-recoveries/confirm")
    public LoginIdRecoveryResponse confirmLoginIdRecovery(
            @Valid @RequestBody LoginIdRecoveryConfirmRequest request
    ) {
        return accountRecoveryService.confirmLoginIdRecovery(request.token());
    }

    @PostMapping("/password-reset-requests")
    public GuidanceResponse requestPasswordReset(@Valid @RequestBody PasswordResetRequest request) {
        accountRecoveryService.requestPasswordReset(request.loginId(), request.email());
        return GuidanceResponse.recoverySentIfPossible();
    }

    @PostMapping("/password-resets/confirm")
    public PasswordResetResponse confirmPasswordReset(@Valid @RequestBody PasswordResetConfirmRequest request) {
        return accountRecoveryService.confirmPasswordReset(request.token(), request.newPassword());
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        LoginResult result = loginService.login(request);
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, refreshTokenCookie(result.refreshToken(), result.refreshTokenTtl()).toString())
                .body(result.response());
    }

    @PostMapping("/refresh")
    public TokenRefreshResponse refresh(
            @CookieValue(value = REFRESH_TOKEN_COOKIE, required = false) String refreshToken
    ) {
        JwtToken accessToken = authTokenService.refreshAccessToken(refreshToken);
        return TokenRefreshResponse.bearer(accessToken.value(), accessToken.expiresInSeconds());
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            @CookieValue(value = REFRESH_TOKEN_COOKIE, required = false) String refreshToken
    ) {
        authTokenService.logout(refreshToken);
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, clearRefreshTokenCookie().toString())
                .build();
    }

    private ResponseCookie refreshTokenCookie(String refreshToken, Duration maxAge) {
        return ResponseCookie.from(REFRESH_TOKEN_COOKIE, refreshToken)
                .httpOnly(true)
                .secure(false)
                .sameSite("Lax")
                .path("/api/auth")
                .maxAge(maxAge)
                .build();
    }

    private ResponseCookie clearRefreshTokenCookie() {
        return ResponseCookie.from(REFRESH_TOKEN_COOKIE, "")
                .httpOnly(true)
                .secure(false)
                .sameSite("Lax")
                .path("/api/auth")
                .maxAge(0)
                .build();
    }

}
