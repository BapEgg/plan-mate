package com.planmate.auth.service;

import com.planmate.auth.dto.LoginIdRecoveryResponse;
import com.planmate.auth.dto.PasswordResetResponse;
import com.planmate.auth.email.AuthEmailSender;
import com.planmate.auth.entity.AuthEmailLogEntity;
import com.planmate.auth.entity.AuthEmailTokenEntity;
import com.planmate.auth.entity.AuthEmailTokenPurpose;
import com.planmate.auth.entity.LocalCredentialEntity;
import com.planmate.auth.exception.ExpiredAuthTokenException;
import com.planmate.auth.exception.InvalidAuthTokenException;
import com.planmate.auth.exception.TokenAlreadyUsedException;
import com.planmate.auth.repository.AuthEmailLogRepository;
import com.planmate.auth.repository.AuthEmailTokenRepository;
import com.planmate.auth.repository.LocalCredentialRepository;
import com.planmate.auth.security.OpaqueTokenService;
import com.planmate.user.domain.UserStatus;
import com.planmate.user.entity.UserEntity;
import com.planmate.user.repository.UserRepository;
import java.time.Duration;
import java.time.Instant;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AccountRecoveryService {

    private static final Duration ACCOUNT_RECOVERY_TOKEN_TTL = Duration.ofMinutes(30);

    private final UserRepository userRepository;
    private final LocalCredentialRepository localCredentialRepository;
    private final AuthEmailTokenRepository authEmailTokenRepository;
    private final AuthEmailLogRepository authEmailLogRepository;
    private final OpaqueTokenService opaqueTokenService;
    private final AuthEmailSender authEmailSender;
    private final PasswordEncoder passwordEncoder;
    private final AuthTokenService authTokenService;
    private final AuthNormalizer authNormalizer;

    public AccountRecoveryService(
            UserRepository userRepository,
            LocalCredentialRepository localCredentialRepository,
            AuthEmailTokenRepository authEmailTokenRepository,
            AuthEmailLogRepository authEmailLogRepository,
            OpaqueTokenService opaqueTokenService,
            AuthEmailSender authEmailSender,
            PasswordEncoder passwordEncoder,
            AuthTokenService authTokenService,
            AuthNormalizer authNormalizer
    ) {
        this.userRepository = userRepository;
        this.localCredentialRepository = localCredentialRepository;
        this.authEmailTokenRepository = authEmailTokenRepository;
        this.authEmailLogRepository = authEmailLogRepository;
        this.opaqueTokenService = opaqueTokenService;
        this.authEmailSender = authEmailSender;
        this.passwordEncoder = passwordEncoder;
        this.authTokenService = authTokenService;
        this.authNormalizer = authNormalizer;
    }

    @Transactional
    public void requestLoginIdRecovery(String email) {
        Instant now = Instant.now();
        String normalizedEmail = authNormalizer.normalizeEmail(email);
        authEmailLogRepository.save(AuthEmailLogEntity.create(
                normalizedEmail,
                AuthEmailTokenPurpose.LOGIN_ID_RECOVERY,
                null
        ));

        userRepository.findByEmailCanonical(normalizedEmail)
                .filter(this::isActive)
                .filter(user -> localCredentialRepository.findByUserId(user.getId()).isPresent())
                .ifPresent(user -> issueRecoveryToken(
                        user,
                        normalizedEmail,
                        AuthEmailTokenPurpose.LOGIN_ID_RECOVERY,
                        now
                ));
    }

    @Transactional
    public LoginIdRecoveryResponse confirmLoginIdRecovery(String rawToken) {
        Instant now = Instant.now();
        AuthEmailTokenEntity token = findUsableToken(rawToken, AuthEmailTokenPurpose.LOGIN_ID_RECOVERY, now);
        LocalCredentialEntity credential = findActiveCredential(token.getUser());

        token.markUsed(now);
        return new LoginIdRecoveryResponse(credential.getLoginId());
    }

    @Transactional
    public void requestPasswordReset(String loginId, String email) {
        Instant now = Instant.now();
        String normalizedLoginId = authNormalizer.normalizeLoginId(loginId);
        String normalizedEmail = authNormalizer.normalizeEmail(email);
        authEmailLogRepository.save(AuthEmailLogEntity.create(
                normalizedEmail,
                AuthEmailTokenPurpose.PASSWORD_RESET,
                null
        ));

        localCredentialRepository.findByLoginId(normalizedLoginId)
                .filter(credential -> credential.getUser().getEmailCanonical().equals(normalizedEmail))
                .map(LocalCredentialEntity::getUser)
                .filter(this::isActive)
                .ifPresent(user -> issueRecoveryToken(
                        user,
                        normalizedEmail,
                        AuthEmailTokenPurpose.PASSWORD_RESET,
                        now
                ));
    }

    @Transactional
    public PasswordResetResponse confirmPasswordReset(String rawToken, String newPassword) {
        Instant now = Instant.now();
        AuthEmailTokenEntity token = findUsableToken(rawToken, AuthEmailTokenPurpose.PASSWORD_RESET, now);
        LocalCredentialEntity credential = findActiveCredential(token.getUser());

        credential.changePassword(passwordEncoder.encode(newPassword), now);
        token.markUsed(now);
        authTokenService.revokeAllForUser(credential.getUserId());
        return new PasswordResetResponse(true);
    }

    private void issueRecoveryToken(
            UserEntity user,
            String email,
            AuthEmailTokenPurpose purpose,
            Instant now
    ) {
        String rawToken = opaqueTokenService.generateRawToken();
        authEmailTokenRepository.save(AuthEmailTokenEntity.create(
                user,
                email,
                opaqueTokenService.hash(rawToken),
                purpose,
                now.plus(ACCOUNT_RECOVERY_TOKEN_TTL)
        ));

        if (purpose == AuthEmailTokenPurpose.LOGIN_ID_RECOVERY) {
            authEmailSender.sendLoginIdRecovery(user, rawToken);
            return;
        }
        authEmailSender.sendPasswordReset(user, rawToken);
    }

    private AuthEmailTokenEntity findUsableToken(
            String rawToken,
            AuthEmailTokenPurpose expectedPurpose,
            Instant now
    ) {
        AuthEmailTokenEntity token = authEmailTokenRepository.findByTokenHash(opaqueTokenService.hash(rawToken))
                .orElseThrow(InvalidAuthTokenException::new);
        if (token.getPurpose() != expectedPurpose) {
            throw new InvalidAuthTokenException();
        }
        if (token.getUsedAt() != null) {
            throw new TokenAlreadyUsedException();
        }
        if (!token.getExpiresAt().isAfter(now)) {
            throw new ExpiredAuthTokenException();
        }

        return token;
    }

    private LocalCredentialEntity findActiveCredential(UserEntity user) {
        if (user == null || !isActive(user)) {
            throw new InvalidAuthTokenException();
        }

        return localCredentialRepository.findByUserId(user.getId())
                .orElseThrow(InvalidAuthTokenException::new);
    }

    private boolean isActive(UserEntity user) {
        return user.getStatus() == UserStatus.ACTIVE;
    }

}
