package com.planmate.user.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.planmate.auth.entity.LocalCredentialEntity;
import com.planmate.auth.repository.LocalCredentialRepository;
import com.planmate.auth.security.JwtTokenProvider;
import com.planmate.user.domain.UserRole;
import com.planmate.user.entity.UserEntity;
import com.planmate.user.repository.UserRepository;
import com.planmate.user.service.ProfileImageStorage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Comparator;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(properties = "app.profile-image.upload-dir=build/test-profile-images")
@AutoConfigureMockMvc
@Transactional
class UserControllerProfileTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private LocalCredentialRepository localCredentialRepository;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private ProfileImageStorage profileImageStorage;

    @BeforeEach
    void setUp() throws Exception {
        deleteProfileImages();
    }

    @AfterEach
    void tearDown() throws Exception {
        deleteProfileImages();
    }

    @Test
    void updateMeChangesNickname() throws Exception {
        UserEntity user = createUser();
        String accessToken = accessToken(user);

        mockMvc.perform(patch("/api/users/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "nickname": "updated-user"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nickname").value("updated-user"))
                .andExpect(jsonPath("$.profileImageUrl").value(nullValue()));

        UserEntity updated = userRepository.findById(user.getId()).orElseThrow();
        assertThat(updated.getNickname()).isEqualTo("updated-user");
    }

    @Test
    void updateMeRejectsInvalidNickname() throws Exception {
        UserEntity user = createUser();
        String accessToken = accessToken(user);

        mockMvc.perform(patch("/api/users/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "nickname": "x"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void updateProfileImageStoresImageAndReturnsUrl() throws Exception {
        UserEntity user = createUser();
        String accessToken = accessToken(user);
        MockMultipartFile image = new MockMultipartFile(
                "image",
                "avatar.png",
                "image/png",
                new byte[]{1, 2, 3}
        );

        String profileImageUrl = mockMvc.perform(multipart("/api/users/me/profile-image")
                        .file(image)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.profileImageUrl", startsWith("/uploads/profile-images/")))
                .andReturn()
                .getResponse()
                .getContentAsString()
                .split("\"profileImageUrl\":\"", 2)[1]
                .split("\"", 2)[0];

        UserEntity updated = userRepository.findById(user.getId()).orElseThrow();
        assertThat(updated.getProfileImageUrl()).isEqualTo(profileImageUrl);

        mockMvc.perform(get(profileImageUrl))
                .andExpect(status().isOk())
                .andExpect(content().bytes(new byte[]{1, 2, 3}));
    }

    @Test
    void updateProfileImageRejectsNonImageFile() throws Exception {
        UserEntity user = createUser();
        String accessToken = accessToken(user);
        MockMultipartFile image = new MockMultipartFile(
                "image",
                "avatar.txt",
                MediaType.TEXT_PLAIN_VALUE,
                "not image".getBytes()
        );

        mockMvc.perform(multipart("/api/users/me/profile-image")
                        .file(image)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PROFILE_IMAGE"));
    }

    @Test
    void clearProfileImageRemovesImageUrl() throws Exception {
        UserEntity user = createUser();
        String accessToken = accessToken(user);
        MockMultipartFile image = new MockMultipartFile(
                "image",
                "avatar.png",
                "image/png",
                new byte[]{1, 2, 3}
        );

        mockMvc.perform(multipart("/api/users/me/profile-image")
                        .file(image)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isOk());

        mockMvc.perform(delete("/api/users/me/profile-image")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.profileImageUrl").value(nullValue()));

        UserEntity updated = userRepository.findById(user.getId()).orElseThrow();
        assertThat(updated.getProfileImageUrl()).isNull();
    }

    private UserEntity createUser() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        Instant now = Instant.now();
        UserEntity user = userRepository.save(UserEntity.createOauthUser(
                "profile-" + suffix + "@example.com",
                "profile-" + suffix + "@example.com",
                "profile-user",
                true,
                now
        ));
        localCredentialRepository.save(LocalCredentialEntity.create(user, "profile" + suffix, "password-hash", now));
        return user;
    }

    private String accessToken(UserEntity user) {
        return jwtTokenProvider.issueAccessToken(user.getId(), UserRole.USER, Instant.now()).value();
    }

    private void deleteProfileImages() throws Exception {
        Path uploadDirectory = profileImageStorage.uploadDirectory();
        if (!Files.exists(uploadDirectory)) {
            return;
        }
        try (var paths = Files.walk(uploadDirectory)) {
            paths.sorted(Comparator.reverseOrder())
                    .forEach(path -> {
                        try {
                            Files.deleteIfExists(path);
                        } catch (Exception ignored) {
                            // Best effort cleanup for local test files.
                        }
                    });
        }
    }

}
