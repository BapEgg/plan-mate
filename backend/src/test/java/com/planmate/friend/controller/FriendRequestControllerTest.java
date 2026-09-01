package com.planmate.friend.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.planmate.auth.security.JwtTokenProvider;
import com.planmate.user.domain.UserRole;
import com.planmate.user.entity.UserEntity;
import com.planmate.user.repository.UserRepository;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/** spec §5.1: 친구 관계는 여행방 멤버십과 독립적으로 수락한다. */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class FriendRequestControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Test
    void sendingAndAcceptingAFriendRequestMakesThemAppearInEachOthersFriendList() throws Exception {
        UserEntity a = createUser();
        UserEntity b = createUser();

        String responseJson = mockMvc.perform(post("/api/friend-requests")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken(a))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UserIdBody(b.getId()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andReturn().getResponse().getContentAsString();
        Long requestId = objectMapper.readTree(responseJson).get("id").asLong();

        mockMvc.perform(post("/api/friend-requests/{id}/accept", requestId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken(b)))
                .andExpect(status().isNoContent());

        JsonNode friendsOfA = objectMapper.readTree(
                mockMvc.perform(get("/api/friends").header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken(a)))
                        .andExpect(status().isOk())
                        .andReturn().getResponse().getContentAsString()
        );
        assertThat(friendsOfA).anyMatch(node -> node.get("userId").asLong() == b.getId());
    }

    @Test
    void cannotSendASecondPendingRequestInEitherDirectionWhileOneIsOpen() throws Exception {
        UserEntity a = createUser();
        UserEntity b = createUser();

        mockMvc.perform(post("/api/friend-requests")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken(a))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UserIdBody(b.getId()))))
                .andExpect(status().isOk());

        // reverse direction is blocked by the same pending pair, not just the same direction
        mockMvc.perform(post("/api/friend-requests")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken(b))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UserIdBody(a.getId()))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE_PENDING_FRIEND_REQUEST"));
    }

    @Test
    void cannotSendAFriendRequestToSelf() throws Exception {
        UserEntity a = createUser();

        mockMvc.perform(post("/api/friend-requests")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken(a))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UserIdBody(a.getId()))))
                .andExpect(status().isForbidden());
    }

    private UserEntity createUser() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        Instant now = Instant.now();
        return userRepository.save(UserEntity.createOauthUser(
                "friend-" + suffix + "@example.com",
                "friend-" + suffix + "@example.com",
                "friend-user-" + suffix,
                true,
                now
        ));
    }

    private String accessToken(UserEntity user) {
        return jwtTokenProvider.issueAccessToken(user.getId(), UserRole.USER, Instant.now()).value();
    }

    private record UserIdBody(Long addresseeUserId) {
    }
}
