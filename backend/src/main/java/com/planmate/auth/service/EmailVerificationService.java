package com.planmate.auth.service;

import com.planmate.auth.dto.EmailVerificationResponse;
import com.planmate.auth.email.AuthEmailSender;
import com.planmate.auth.entity.AuthEmailLogEntity;
import com.planmate.auth.entity.AuthEmailTokenEntity;
import com.planmate.auth.entity.AuthEmailTokenPurpose;
import com.planmate.auth.exception.ExpiredAuthTokenException;
import com.planmate.auth.exception.InvalidAuthTokenException;
import com.planmate.auth.exception.TokenAlreadyUsedException;
import com.planmate.auth.repository.AuthEmailLogRepository;
import com.planmate.auth.repository.AuthEmailTokenRepository;
import com.planmate.auth.security.OpaqueTokenService;
import com.planmate.user.domain.UserStatus;
import com.planmate.user.entity.UserEntity;
import com.planmate.user.repository.UserRepository;
import java.time.Duration;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EmailVerificationService {

    private static final Duration SIGNUP_VERIFY_TOKEN_TTL = Duration.ofHours(24);

    private final AuthEmailTokenRepository authEmailTokenRepository;
    private final AuthEmailLogRepository authEmailLogRepository;
    private final UserRepository userRepository;
    private final OpaqueTokenService opaqueTokenService;
    private final AuthEmailSender authEmailSender;
    private final AuthNormalizer authNormalizer;

    public EmailVerificationService(
            AuthEmailTokenRepository authEmailTokenRepository,
            AuthEmailLogRepository authEmailLogRepository,
            UserRepository userRepository,
            OpaqueTokenService opaqueTokenService,
            AuthEmailSender authEmailSender,
            AuthNormalizer authNormalizer
    ) {
        this.authEmailTokenRepository = authEmailTokenRepository;
        this.authEmailLogRepository = authEmailLogRepository;
        this.userRepository = userRepository;
        this.opaqueTokenService = opaqueTokenService;
        this.authEmailSender = authEmailSender;
        this.authNormalizer = authNormalizer;
    }

    @Transactional
    public EmailVerificationResponse confirm(String rawToken) {
        Instant now = Instant.now();
        AuthEmailTokenEntity token = authEmailTokenRepository.findByTokenHash(opaqueTokenService.hash(rawToken))
                .orElseThrow(InvalidAuthTokenException::new);

        if (token.getPurpose() != AuthEmailTokenPurpose.SIGNUP_VERIFY) {
            throw new InvalidAuthTokenException();
        }
        if (token.getUsedAt() != null) {
            throw new TokenAlreadyUsedException();
        }
        if (!token.getExpiresAt().isAfter(now)) {
            throw new ExpiredAuthTokenException();
        }

        UserEntity user = token.getUser();
        if (user == null) {
            throw new InvalidAuthTokenException();
        }

        user.verifyEmail(now);
        token.markUsed(now);
        return new EmailVerificationResponse(true);
    }

    @Transactional
    public void resend(String email) {
        Instant now = Instant.now();
        String normalizedEmail = authNormalizer.normalizeEmail(email);
        authEmailLogRepository.save(AuthEmailLogEntity.create(normalizedEmail, AuthEmailTokenPurpose.SIGNUP_VERIFY, null));

        userRepository.findByEmailCanonical(normalizedEmail)
                .filter(user -> user.getStatus() == UserStatus.PENDING_EMAIL_VERIFICATION)
                .ifPresent(user -> {
                    String rawToken = opaqueTokenService.generateRawToken();
                    authEmailTokenRepository.save(AuthEmailTokenEntity.create(
                            user,
                            normalizedEmail,
                            opaqueTokenService.hash(rawToken),
                            AuthEmailTokenPurpose.SIGNUP_VERIFY,
                            now.plus(SIGNUP_VERIFY_TOKEN_TTL)
                    ));
                    authEmailSender.sendSignupVerification(user, rawToken);
                });
    }

}
