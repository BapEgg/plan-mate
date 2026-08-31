package com.planmate.auth.local;

import com.planmate.auth.entity.LocalCredentialEntity;
import com.planmate.auth.repository.LocalCredentialRepository;
import com.planmate.user.entity.UserEntity;
import com.planmate.user.repository.UserRepository;
import java.time.Instant;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Profile("local")
@Order(0)
@ConditionalOnProperty(name = "app.local-test-users.enabled", havingValue = "true")
public class LocalTestUserInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(LocalTestUserInitializer.class);

    private final UserRepository userRepository;
    private final LocalCredentialRepository localCredentialRepository;
    private final PasswordEncoder passwordEncoder;
    private final LocalTestUserProperties properties;

    public LocalTestUserInitializer(
            UserRepository userRepository,
            LocalCredentialRepository localCredentialRepository,
            PasswordEncoder passwordEncoder,
            LocalTestUserProperties properties
    ) {
        this.userRepository = userRepository;
        this.localCredentialRepository = localCredentialRepository;
        this.passwordEncoder = passwordEncoder;
        this.properties = properties;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        validateConfiguration();
        Instant now = Instant.now();

        for (LocalTestUserProperties.Account account : properties.getAccounts()) {
            ensureAccount(account, now);
        }
    }

    private void ensureAccount(LocalTestUserProperties.Account account, Instant now) {
        String loginId = account.getLoginId().trim();
        String email = account.getEmail().trim().toLowerCase(Locale.ROOT);
        String nickname = account.getNickname().trim();

        LocalCredentialEntity credential = localCredentialRepository.findByLoginId(loginId).orElse(null);
        if (credential != null) {
            UserEntity user = credential.getUser();
            if (!user.getEmailCanonical().equals(email)) {
                throw new IllegalStateException("Local test loginId is already linked to another email: " + loginId);
            }
            activateAndUpdate(user, credential, account.getPassword(), nickname, now);
            log.info("Local test user refreshed: loginId={}, nickname={}", loginId, nickname);
            return;
        }

        UserEntity user = userRepository.findByEmailCanonical(email).orElse(null);
        if (user != null && localCredentialRepository.findByUserId(user.getId()).isPresent()) {
            throw new IllegalStateException("Local test email is already linked to another loginId: " + email);
        }
        if (user == null) {
            user = userRepository.save(UserEntity.createPendingLocalUser(email, email, nickname));
        }

        user.updateNickname(nickname);
        user.verifyEmail(now);
        userRepository.save(user);

        localCredentialRepository.save(LocalCredentialEntity.create(
                user,
                loginId,
                passwordEncoder.encode(account.getPassword()),
                now
        ));
        log.info("Local test user created: loginId={}, nickname={}", loginId, nickname);
    }

    private void activateAndUpdate(
            UserEntity user,
            LocalCredentialEntity credential,
            String password,
            String nickname,
            Instant now
    ) {
        user.updateNickname(nickname);
        user.verifyEmail(now);
        credential.changePassword(passwordEncoder.encode(password), now);
        userRepository.save(user);
        localCredentialRepository.save(credential);
    }

    private void validateConfiguration() {
        if (!properties.isEnabled()) {
            throw new IllegalStateException("Local test user initializer was loaded while disabled");
        }
        if (properties.getAccounts().isEmpty()) {
            throw new IllegalStateException("At least one local test account must be configured");
        }

        Set<String> loginIds = new HashSet<>();
        Set<String> emails = new HashSet<>();
        for (LocalTestUserProperties.Account account : properties.getAccounts()) {
            requireText(account.getLoginId(), "loginId");
            requireText(account.getPassword(), "password");
            requireText(account.getEmail(), "email");
            requireText(account.getNickname(), "nickname");

            String loginId = account.getLoginId().trim();
            String email = account.getEmail().trim().toLowerCase(Locale.ROOT);
            if (!loginIds.add(loginId)) {
                throw new IllegalStateException("Duplicate local test loginId: " + loginId);
            }
            if (!emails.add(email)) {
                throw new IllegalStateException("Duplicate local test email: " + email);
            }
        }
    }

    private void requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Local test user " + field + " must not be blank");
        }
    }

}
