package com.planmate.auth.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.blankOrNullString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.planmate.auth.email.AuthEmailSender;
import com.planmate.auth.entity.LocalCredentialEntity;
import com.planmate.auth.repository.LocalCredentialRepository;
import com.planmate.user.domain.UserStatus;
import com.planmate.user.entity.UserEntity;
import com.planmate.user.repository.UserRepository;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AuthControllerSignupTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private LocalCredentialRepository localCredentialRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private CapturingAuthEmailSender authEmailSender;

    @BeforeEach
    void setUp() {
        authEmailSender.clear();
    }

    @Test
    void signupCreatesPendingLocalUserAndSendsVerificationEmail() throws Exception {
        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "loginId": "planmateUser",
                                  "email": "NewUser@Example.com",
                                  "password": "password123",
                                  "nickname": "new-user"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", not(blankOrNullString())))
                .andExpect(jsonPath("$.loginId").value("planmateUser"))
                .andExpect(jsonPath("$.email").value("newuser@example.com"))
                .andExpect(jsonPath("$.nickname").value("new-user"))
                .andExpect(jsonPath("$.role").value("USER"))
                .andExpect(jsonPath("$.status").value("PENDING_EMAIL_VERIFICATION"))
                .andExpect(jsonPath("$.emailVerified").value(false));

        UserEntity savedUser = userRepository.findByEmailCanonical("newuser@example.com").orElseThrow();
        LocalCredentialEntity credential = localCredentialRepository.findByLoginId("planmateUser").orElseThrow();
        assertThat(credential.getUser().getId()).isEqualTo(savedUser.getId());
        assertThat(passwordEncoder.matches("password123", credential.getPasswordHash())).isTrue();
        assertThat(authEmailSender.signupVerificationToken()).isNotBlank();
    }

    @Test
    void signupRejectsDuplicateEmail() throws Exception {
        signup("firstUser", "duplicate@example.com");

        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "loginId": "secondUser",
                                  "email": "DUPLICATE@example.com",
                                  "password": "password123",
                                  "nickname": "new-user"
                                }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE_EMAIL"));
    }

    @Test
    void signupRejectsDuplicateLoginId() throws Exception {
        signup("duplicateUser", "first@example.com");

        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "loginId": "duplicateUser",
                                  "email": "second@example.com",
                                  "password": "password123",
                                  "nickname": "new-user"
                                }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE_LOGIN_ID"));
    }

    @Test
    void signupRejectsInvalidRequest() throws Exception {
        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "loginId": "!",
                                  "email": "invalid-email",
                                  "password": "short",
                                  "nickname": ""
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void emailVerificationActivatesUser() throws Exception {
        signup("verifyUser", "verify@example.com");

        mockMvc.perform(post("/api/auth/email-verifications/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "token": "%s"
                                }
                                """.formatted(authEmailSender.signupVerificationToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.verified").value(true));

        UserEntity user = userRepository.findByEmailCanonical("verify@example.com").orElseThrow();
        assertThat(user.getStatus()).isEqualTo(UserStatus.ACTIVE);
        assertThat(user.getEmailVerifiedAt()).isNotNull();
    }

    @Test
    void loginRejectsUnverifiedUser() throws Exception {
        signup("loginUser", "login@example.com");

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "loginId": "loginUser",
                                  "password": "password123"
                                }
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("EMAIL_NOT_VERIFIED"));
    }

    @Test
    void loginIssuesAccessTokenAndRefreshTokenAfterEmailVerification() throws Exception {
        signupAndVerify("loginUser", "login@example.com");

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "loginId": "loginUser",
                                  "password": "password123"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(cookie().exists("refreshToken"))
                .andExpect(jsonPath("$.accessToken", not(blankOrNullString())))
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresIn").value(900))
                .andExpect(jsonPath("$.user.loginId").value("loginUser"))
                .andExpect(jsonPath("$.user.status").value("ACTIVE"));
    }

    @Test
    void bearerTokenCanAccessMeAndRefreshCanReissueAccessToken() throws Exception {
        signupAndVerify("meUser", "me@example.com");
        MvcResult loginResult = login("meUser");
        String accessToken = readAccessToken(loginResult);
        Cookie refreshCookie = refreshCookie(loginResult);

        mockMvc.perform(get("/api/users/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.loginId").value("meUser"))
                .andExpect(jsonPath("$.email").value("me@example.com"));

        mockMvc.perform(post("/api/auth/refresh")
                        .cookie(refreshCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken", not(blankOrNullString())))
                .andExpect(jsonPath("$.expiresIn").value(900));
    }

    @Test
    void logoutRevokesRefreshToken() throws Exception {
        signupAndVerify("logoutUser", "logout@example.com");
        MvcResult loginResult = login("logoutUser");
        Cookie refreshCookie = refreshCookie(loginResult);

        mockMvc.perform(post("/api/auth/logout")
                        .cookie(refreshCookie))
                .andExpect(status().isNoContent())
                .andExpect(cookie().maxAge("refreshToken", 0));

        mockMvc.perform(post("/api/auth/refresh")
                        .cookie(refreshCookie))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_TOKEN"));
    }

    private void signup(String loginId, String email) throws Exception {
        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "loginId": "%s",
                                  "email": "%s",
                                  "password": "password123",
                                  "nickname": "new-user"
                                }
                                """.formatted(loginId, email)))
                .andExpect(status().isCreated());
    }

    private void signupAndVerify(String loginId, String email) throws Exception {
        signup(loginId, email);
        mockMvc.perform(post("/api/auth/email-verifications/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "token": "%s"
                                }
                                """.formatted(authEmailSender.signupVerificationToken())))
                .andExpect(status().isOk());
    }

    private MvcResult login(String loginId) throws Exception {
        return mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "loginId": "%s",
                                  "password": "password123"
                                }
                                """.formatted(loginId)))
                .andExpect(status().isOk())
                .andReturn();
    }

    private String readAccessToken(MvcResult result) throws Exception {
        String content = result.getResponse().getContentAsString();
        String marker = "\"accessToken\":\"";
        int start = content.indexOf(marker) + marker.length();
        int end = content.indexOf('"', start);
        return content.substring(start, end);
    }

    private Cookie refreshCookie(MvcResult result) {
        String setCookie = result.getResponse().getHeader(HttpHeaders.SET_COOKIE);
        String value = setCookie.split(";", 2)[0].substring("refreshToken=".length());
        return new Cookie("refreshToken", value);
    }

    @TestConfiguration
    static class AuthEmailSenderTestConfig {

        @Bean
        @Primary
        CapturingAuthEmailSender capturingAuthEmailSender() {
            return new CapturingAuthEmailSender();
        }

    }

    static class CapturingAuthEmailSender implements AuthEmailSender {

        private String signupVerificationToken;

        @Override
        public void sendSignupVerification(UserEntity user, String rawToken) {
            this.signupVerificationToken = rawToken;
        }

        String signupVerificationToken() {
            return signupVerificationToken;
        }

        void clear() {
            signupVerificationToken = null;
        }

    }

}
