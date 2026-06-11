package com.planmate.user.controller;

import com.planmate.auth.security.AuthenticatedUser;
import com.planmate.user.dto.MeResponse;
import com.planmate.user.dto.ProfileUpdateRequest;
import com.planmate.user.service.UserProfileService;
import com.planmate.user.service.UserQueryService;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserQueryService userQueryService;
    private final UserProfileService userProfileService;

    public UserController(UserQueryService userQueryService, UserProfileService userProfileService) {
        this.userQueryService = userQueryService;
        this.userProfileService = userProfileService;
    }

    @GetMapping("/me")
    public MeResponse me(@AuthenticationPrincipal AuthenticatedUser user) {
        return userQueryService.getMe(user.userId());
    }

    @PatchMapping("/me")
    public MeResponse updateMe(
            @AuthenticationPrincipal AuthenticatedUser user,
            @Valid @RequestBody ProfileUpdateRequest request
    ) {
        return userProfileService.updateNickname(user.userId(), request.nickname());
    }

    @PostMapping(value = "/me/profile-image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public MeResponse updateProfileImage(
            @AuthenticationPrincipal AuthenticatedUser user,
            @RequestPart("image") MultipartFile image
    ) {
        return userProfileService.updateProfileImage(user.userId(), image);
    }

    @DeleteMapping("/me/profile-image")
    public MeResponse clearProfileImage(@AuthenticationPrincipal AuthenticatedUser user) {
        return userProfileService.clearProfileImage(user.userId());
    }

}
