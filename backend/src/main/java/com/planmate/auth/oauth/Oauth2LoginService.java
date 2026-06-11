package com.planmate.auth.oauth;

import com.planmate.auth.entity.OauthAccountEntity;
import com.planmate.auth.repository.OauthAccountRepository;
import com.planmate.auth.service.AuthNormalizer;
import com.planmate.auth.service.AuthTokenBundle;
import com.planmate.auth.service.AuthTokenService;
import com.planmate.user.domain.UserStatus;
import com.planmate.user.entity.UserEntity;
import com.planmate.user.repository.UserRepository;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class Oauth2LoginService {

    private static final int MAX_NICKNAME_LENGTH = 30;

    private final OauthAccountRepository oauthAccountRepository;
    private final UserRepository userRepository;
    private final AuthTokenService authTokenService;
    private final AuthNormalizer authNormalizer;

    public Oauth2LoginService(
            OauthAccountRepository oauthAccountRepository,
            UserRepository userRepository,
            AuthTokenService authTokenService,
            AuthNormalizer authNormalizer
    ) {
        this.oauthAccountRepository = oauthAccountRepository;
        this.userRepository = userRepository;
        this.authTokenService = authTokenService;
        this.authNormalizer = authNormalizer;
    }

    @Transactional
    public AuthTokenBundle login(OauthUserProfile profile) {
        Instant now = Instant.now();
        UserEntity user = oauthAccountRepository
                .findByProviderAndProviderUserId(profile.provider().name(), profile.providerUserId())
                .map(OauthAccountEntity::getUser)
                .orElseGet(() -> linkOrCreateUser(profile, now));

        if (user.getStatus() != UserStatus.ACTIVE) {
            throw OauthProvider.oauth2Exception("OAuth2 user is not active.");
        }

        return authTokenService.issueTokens(user);
    }

    private UserEntity linkOrCreateUser(OauthUserProfile profile, Instant now) {
        String email = authNormalizer.normalizeEmail(profile.email());
        UserEntity user = userRepository.findByEmailCanonical(email)
                .map(existingUser -> activatePendingUserIfPossible(existingUser, profile, now))
                .orElseGet(() -> userRepository.save(UserEntity.createOauthUser(
                        email,
                        email,
                        nickname(profile),
                        profile.emailVerified(),
                        now
                )));

        if (user.getStatus() != UserStatus.ACTIVE) {
            throw OauthProvider.oauth2Exception("OAuth2 email is linked to an inactive PlanMate account.");
        }

        oauthAccountRepository.save(OauthAccountEntity.create(
                user,
                profile.provider().name(),
                profile.providerUserId(),
                email,
                profile.emailVerified()
        ));
        return user;
    }

    private UserEntity activatePendingUserIfPossible(UserEntity user, OauthUserProfile profile, Instant now) {
        if (user.getStatus() == UserStatus.PENDING_EMAIL_VERIFICATION && profile.emailVerified()) {
            user.verifyEmail(now);
        }
        return user;
    }

    private String nickname(OauthUserProfile profile) {
        String nickname = StringUtils.hasText(profile.nickname())
                ? profile.nickname().trim()
                : "PlanMate";

        if (nickname.length() > MAX_NICKNAME_LENGTH) {
            return nickname.substring(0, MAX_NICKNAME_LENGTH);
        }
        if (nickname.length() < 2) {
            return "PlanMate";
        }
        return nickname;
    }

}
