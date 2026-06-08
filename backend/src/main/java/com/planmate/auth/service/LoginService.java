package com.planmate.auth.service;

import com.planmate.auth.dto.AuthUserResponse;
import com.planmate.auth.dto.LoginRequest;
import com.planmate.auth.dto.LoginResponse;
import com.planmate.auth.entity.LocalCredentialEntity;
import com.planmate.auth.exception.EmailNotVerifiedException;
import com.planmate.auth.exception.InvalidCredentialsException;
import com.planmate.auth.repository.LocalCredentialRepository;
import com.planmate.user.domain.UserStatus;
import com.planmate.user.entity.UserEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LoginService {

    private final LocalCredentialRepository localCredentialRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthTokenService authTokenService;
    private final AuthNormalizer authNormalizer;

    public LoginService(
            LocalCredentialRepository localCredentialRepository,
            PasswordEncoder passwordEncoder,
            AuthTokenService authTokenService,
            AuthNormalizer authNormalizer
    ) {
        this.localCredentialRepository = localCredentialRepository;
        this.passwordEncoder = passwordEncoder;
        this.authTokenService = authTokenService;
        this.authNormalizer = authNormalizer;
    }

    @Transactional
    public LoginResult login(LoginRequest request) {
        String loginId = authNormalizer.normalizeLoginId(request.loginId());
        LocalCredentialEntity credential = localCredentialRepository.findByLoginId(loginId)
                .orElseThrow(InvalidCredentialsException::new);

        if (!passwordEncoder.matches(request.password(), credential.getPasswordHash())) {
            throw new InvalidCredentialsException();
        }

        UserEntity user = credential.getUser();
        if (user.getStatus() == UserStatus.PENDING_EMAIL_VERIFICATION) {
            throw new EmailNotVerifiedException();
        }
        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new InvalidCredentialsException();
        }

        AuthTokenBundle tokens = authTokenService.issueTokens(user);
        LoginResponse response = LoginResponse.bearer(
                tokens.accessToken().value(),
                tokens.accessToken().expiresInSeconds(),
                AuthUserResponse.from(user.toAccount(), credential.getLoginId())
        );

        return new LoginResult(response, tokens.refreshToken(), tokens.refreshTokenTtl());
    }

}
