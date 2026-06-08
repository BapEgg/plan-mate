package com.planmate.user.service;

import com.planmate.auth.entity.LocalCredentialEntity;
import com.planmate.auth.entity.OauthAccountEntity;
import com.planmate.auth.repository.LocalCredentialRepository;
import com.planmate.auth.repository.OauthAccountRepository;
import com.planmate.user.dto.MeResponse;
import com.planmate.user.entity.UserEntity;
import com.planmate.user.exception.UserNotFoundException;
import com.planmate.user.repository.UserRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserQueryService {

    private final UserRepository userRepository;
    private final LocalCredentialRepository localCredentialRepository;
    private final OauthAccountRepository oauthAccountRepository;

    public UserQueryService(
            UserRepository userRepository,
            LocalCredentialRepository localCredentialRepository,
            OauthAccountRepository oauthAccountRepository
    ) {
        this.userRepository = userRepository;
        this.localCredentialRepository = localCredentialRepository;
        this.oauthAccountRepository = oauthAccountRepository;
    }

    @Transactional(readOnly = true)
    public MeResponse getMe(Long userId) {
        UserEntity user = userRepository.findById(userId).orElseThrow(UserNotFoundException::new);
        String loginId = localCredentialRepository.findByUserId(userId)
                .map(LocalCredentialEntity::getLoginId)
                .orElse(null);
        List<String> providers = oauthAccountRepository.findByUserId(userId)
                .stream()
                .map(OauthAccountEntity::getProvider)
                .toList();

        return new MeResponse(
                user.getId(),
                loginId,
                user.getEmail(),
                user.getNickname(),
                user.getRole(),
                user.getStatus(),
                user.getEmailVerifiedAt() != null,
                providers
        );
    }

}
