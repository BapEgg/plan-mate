package com.planmate.auth.service;

import com.planmate.auth.dto.SignupRequest;
import com.planmate.auth.dto.SignupResponse;
import com.planmate.auth.email.AuthEmailSender;
import com.planmate.auth.entity.AuthEmailLogEntity;
import com.planmate.auth.entity.AuthEmailTokenEntity;
import com.planmate.auth.entity.AuthEmailTokenPurpose;
import com.planmate.auth.entity.LocalCredentialEntity;
import com.planmate.auth.exception.DuplicateEmailException;
import com.planmate.auth.exception.DuplicateLoginIdException;
import com.planmate.auth.repository.AuthEmailLogRepository;
import com.planmate.auth.repository.AuthEmailTokenRepository;
import com.planmate.auth.repository.LocalCredentialRepository;
import com.planmate.auth.security.OpaqueTokenService;
import com.planmate.user.entity.UserEntity;
import com.planmate.user.repository.UserRepository;
import java.time.Duration;
import java.time.Instant;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SignupService {

    private static final Duration SIGNUP_VERIFY_TOKEN_TTL = Duration.ofHours(24);

    private final UserRepository userRepository;
    private final LocalCredentialRepository localCredentialRepository;
    private final AuthEmailTokenRepository authEmailTokenRepository;
    private final AuthEmailLogRepository authEmailLogRepository;
    private final PasswordEncoder passwordEncoder;
    private final OpaqueTokenService opaqueTokenService;
    private final AuthEmailSender authEmailSender;
    private final AuthNormalizer authNormalizer;

    public SignupService(
            UserRepository userRepository,
            LocalCredentialRepository localCredentialRepository,
            AuthEmailTokenRepository authEmailTokenRepository,
            AuthEmailLogRepository authEmailLogRepository,
            PasswordEncoder passwordEncoder,
            OpaqueTokenService opaqueTokenService,
            AuthEmailSender authEmailSender,
            AuthNormalizer authNormalizer
    ) {
        this.userRepository = userRepository;
        this.localCredentialRepository = localCredentialRepository;
        this.authEmailTokenRepository = authEmailTokenRepository;
        this.authEmailLogRepository = authEmailLogRepository;
        this.passwordEncoder = passwordEncoder;
        this.opaqueTokenService = opaqueTokenService;
        this.authEmailSender = authEmailSender;
        this.authNormalizer = authNormalizer;
    }

    @Transactional
    public SignupResponse signup(SignupRequest request) {
        Instant now = Instant.now();
        String loginId = authNormalizer.normalizeLoginId(request.loginId());
        String email = authNormalizer.normalizeEmail(request.email());
        String nickname = request.nickname().trim();

        if (localCredentialRepository.existsByLoginId(loginId)) {
            throw new DuplicateLoginIdException(loginId);
        }
        if (userRepository.existsByEmailCanonical(email)) {
            throw new DuplicateEmailException(email);
        }

        UserEntity user = userRepository.save(UserEntity.createPendingLocalUser(email, email, nickname));
        localCredentialRepository.save(LocalCredentialEntity.create(
                user,
                loginId,
                passwordEncoder.encode(request.password()),
                now
        ));

        String rawToken = opaqueTokenService.generateRawToken();
        authEmailTokenRepository.save(AuthEmailTokenEntity.create(
                user,
                email,
                opaqueTokenService.hash(rawToken),
                AuthEmailTokenPurpose.SIGNUP_VERIFY,
                now.plus(SIGNUP_VERIFY_TOKEN_TTL)
        ));
        authEmailLogRepository.save(AuthEmailLogEntity.create(email, AuthEmailTokenPurpose.SIGNUP_VERIFY, null));
        authEmailSender.sendSignupVerification(user, rawToken);

        return SignupResponse.from(user.toAccount(), loginId);
    }

}
