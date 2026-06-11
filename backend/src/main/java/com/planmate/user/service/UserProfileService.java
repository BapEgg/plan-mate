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
import org.springframework.web.multipart.MultipartFile;

@Service
public class UserProfileService {

    private final UserRepository userRepository;
    private final LocalCredentialRepository localCredentialRepository;
    private final OauthAccountRepository oauthAccountRepository;
    private final ProfileImageStorage profileImageStorage;

    public UserProfileService(
            UserRepository userRepository,
            LocalCredentialRepository localCredentialRepository,
            OauthAccountRepository oauthAccountRepository,
            ProfileImageStorage profileImageStorage
    ) {
        this.userRepository = userRepository;
        this.localCredentialRepository = localCredentialRepository;
        this.oauthAccountRepository = oauthAccountRepository;
        this.profileImageStorage = profileImageStorage;
    }

    @Transactional
    public MeResponse updateNickname(Long userId, String nickname) {
        UserEntity user = getUser(userId);
        user.updateNickname(nickname.trim());
        return toMeResponse(user);
    }

    @Transactional
    public MeResponse updateProfileImage(Long userId, MultipartFile image) {
        UserEntity user = getUser(userId);
        String previousImageUrl = user.getProfileImageUrl();
        String imageUrl = profileImageStorage.store(image);
        user.updateProfileImageUrl(imageUrl);
        profileImageStorage.deleteByUrl(previousImageUrl);
        return toMeResponse(user);
    }

    @Transactional
    public MeResponse clearProfileImage(Long userId) {
        UserEntity user = getUser(userId);
        String previousImageUrl = user.getProfileImageUrl();
        user.clearProfileImageUrl();
        profileImageStorage.deleteByUrl(previousImageUrl);
        return toMeResponse(user);
    }

    private UserEntity getUser(Long userId) {
        return userRepository.findById(userId).orElseThrow(UserNotFoundException::new);
    }

    private MeResponse toMeResponse(UserEntity user) {
        Long userId = user.getId();
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
                user.getProfileImageUrl(),
                user.getRole(),
                user.getStatus(),
                user.getEmailVerifiedAt() != null,
                providers
        );
    }

}
