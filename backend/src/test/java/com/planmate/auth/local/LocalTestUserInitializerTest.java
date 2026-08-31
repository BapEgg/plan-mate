package com.planmate.auth.local;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.planmate.auth.entity.LocalCredentialEntity;
import com.planmate.auth.repository.LocalCredentialRepository;
import com.planmate.user.domain.UserStatus;
import com.planmate.user.entity.UserEntity;
import com.planmate.user.repository.UserRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

class LocalTestUserInitializerTest {

    private final UserRepository userRepository = mock(UserRepository.class);
    private final LocalCredentialRepository localCredentialRepository = mock(LocalCredentialRepository.class);
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    @Test
    void createsVerifiedLocalAccountsFromConfiguration() throws Exception {
        LocalTestUserProperties properties = properties(
                account("local1", "test-password-1", "local1@planmate.local", "민준"),
                account("local2", "test-password-2", "local2@planmate.local", "서윤")
        );
        given(localCredentialRepository.findByLoginId(any())).willReturn(Optional.empty());
        given(userRepository.findByEmailCanonical(any())).willReturn(Optional.empty());
        given(userRepository.save(any(UserEntity.class))).willAnswer(invocation -> invocation.getArgument(0));

        LocalTestUserInitializer initializer = new LocalTestUserInitializer(
                userRepository,
                localCredentialRepository,
                passwordEncoder,
                properties
        );

        initializer.run(null);

        verify(userRepository, times(4)).save(any(UserEntity.class));
        verify(localCredentialRepository, times(2)).save(any(LocalCredentialEntity.class));
    }

    @Test
    void refreshesExistingAccountAndKeepsItActive() throws Exception {
        LocalTestUserProperties properties = properties(
                account("local1", "new-password", "local1@planmate.local", "새 닉네임")
        );
        UserEntity user = UserEntity.createPendingLocalUser(
                "local1@planmate.local",
                "local1@planmate.local",
                "기존 닉네임"
        );
        LocalCredentialEntity credential = LocalCredentialEntity.create(
                user,
                "local1",
                passwordEncoder.encode("old-password"),
                java.time.Instant.EPOCH
        );
        given(localCredentialRepository.findByLoginId("local1")).willReturn(Optional.of(credential));

        LocalTestUserInitializer initializer = new LocalTestUserInitializer(
                userRepository,
                localCredentialRepository,
                passwordEncoder,
                properties
        );

        initializer.run(null);

        assertThat(user.getNickname()).isEqualTo("새 닉네임");
        assertThat(user.getStatus()).isEqualTo(UserStatus.ACTIVE);
        assertThat(passwordEncoder.matches("new-password", credential.getPasswordHash())).isTrue();
        verify(userRepository).save(user);
        verify(localCredentialRepository).save(credential);
    }

    private LocalTestUserProperties properties(LocalTestUserProperties.Account... accounts) {
        LocalTestUserProperties properties = new LocalTestUserProperties();
        properties.setEnabled(true);
        properties.setAccounts(List.of(accounts));
        return properties;
    }

    private LocalTestUserProperties.Account account(
            String loginId,
            String password,
            String email,
            String nickname
    ) {
        LocalTestUserProperties.Account account = new LocalTestUserProperties.Account();
        account.setLoginId(loginId);
        account.setPassword(password);
        account.setEmail(email);
        account.setNickname(nickname);
        return account;
    }

}
