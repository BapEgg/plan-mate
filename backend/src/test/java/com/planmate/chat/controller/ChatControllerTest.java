package com.planmate.chat.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.planmate.auth.security.JwtTokenProvider;
import com.planmate.chat.repository.ChatMessageRepository;
import com.planmate.trip.entity.TripEntity;
import com.planmate.trip.entity.TripMemberEntity;
import com.planmate.trip.repository.TripMemberRepository;
import com.planmate.trip.repository.TripRepository;
import com.planmate.user.domain.UserRole;
import com.planmate.user.entity.UserEntity;
import com.planmate.user.repository.UserRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ChatControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TripRepository tripRepository;

    @Autowired
    private TripMemberRepository tripMemberRepository;

    @Autowired
    private ChatMessageRepository chatMessageRepository;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Test
    void memberCanSendAMessageAndSeeItInHistory() throws Exception {
        UserEntity owner = createUser();
        TripEntity trip = createTrip(owner);
        String clientMessageId = UUID.randomUUID().toString();

        mockMvc.perform(post("/api/trips/{tripId}/chat/messages", trip.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new SendBody(clientMessageId, "안녕하세요"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.body").value("안녕하세요"))
                .andExpect(jsonPath("$.authorUserId").value(owner.getId()))
                .andExpect(jsonPath("$.clientMessageId").value(clientMessageId));

        mockMvc.perform(get("/api/trips/{tripId}/chat/messages", trip.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.messages.length()").value(1))
                .andExpect(jsonPath("$.messages[0].body").value("안녕하세요"))
                .andExpect(jsonPath("$.nextCursor").doesNotExist());
    }

    @Test
    void resendingTheSameClientMessageIdReturnsTheSameMessageWithoutADuplicateRow() throws Exception {
        UserEntity owner = createUser();
        TripEntity trip = createTrip(owner);
        String clientMessageId = UUID.randomUUID().toString();

        String firstResponse = mockMvc.perform(post("/api/trips/{tripId}/chat/messages", trip.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new SendBody(clientMessageId, "첫 메시지"))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        Long firstId = objectMapper.readTree(firstResponse).get("id").asLong();

        mockMvc.perform(post("/api/trips/{tripId}/chat/messages", trip.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new SendBody(clientMessageId, "다른 내용을 보내도 무시된다"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(firstId))
                .andExpect(jsonPath("$.body").value("첫 메시지"));

        assertThat(chatMessageRepository.findByTripIdOrderByIdDesc(trip.getId(), org.springframework.data.domain.PageRequest.of(0, 10)))
                .hasSize(1);
    }

    @Test
    void nonMemberCannotSendOrReadMessages() throws Exception {
        UserEntity owner = createUser();
        UserEntity outsider = createUser();
        TripEntity trip = createTrip(owner);

        mockMvc.perform(post("/api/trips/{tripId}/chat/messages", trip.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken(outsider))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new SendBody(UUID.randomUUID().toString(), "몰래 보내기"))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("TRIP_NOT_FOUND"));

        mockMvc.perform(get("/api/trips/{tripId}/chat/messages", trip.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken(outsider)))
                .andExpect(status().isNotFound());
    }

    @Test
    void emptyMessageBodyIsRejected() throws Exception {
        UserEntity owner = createUser();
        TripEntity trip = createTrip(owner);

        mockMvc.perform(post("/api/trips/{tripId}/chat/messages", trip.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new SendBody(UUID.randomUUID().toString(), "   "))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_MESSAGE_BODY"));
    }

    @Test
    void historyPaginatesNewestFirstWithACursor() throws Exception {
        UserEntity owner = createUser();
        TripEntity trip = createTrip(owner);
        for (int i = 0; i < 5; i++) {
            mockMvc.perform(post("/api/trips/{tripId}/chat/messages", trip.getId())
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken(owner))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(new SendBody(UUID.randomUUID().toString(), "메시지 " + i))))
                    .andExpect(status().isCreated());
        }

        String firstPage = mockMvc.perform(get("/api/trips/{tripId}/chat/messages", trip.getId())
                        .param("size", "3")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.messages.length()").value(3))
                .andExpect(jsonPath("$.messages[0].body").value("메시지 4"))
                .andReturn().getResponse().getContentAsString();
        String cursor = objectMapper.readTree(firstPage).get("nextCursor").asText();

        mockMvc.perform(get("/api/trips/{tripId}/chat/messages", trip.getId())
                        .param("cursor", cursor)
                        .param("size", "3")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.messages.length()").value(2))
                .andExpect(jsonPath("$.messages[0].body").value("메시지 1"))
                .andExpect(jsonPath("$.nextCursor").doesNotExist());
    }

    private TripEntity createTrip(UserEntity owner) {
        Instant now = Instant.now();
        TripEntity trip = tripRepository.save(TripEntity.create(
                "Chat test trip",
                "Busan",
                "place-busan",
                "Busan, Korea",
                35.1, 129.0,
                35.0, 128.9,
                35.2, 129.1,
                List.of("locality"),
                "locality",
                LocalDate.now().plusDays(1),
                LocalDate.now().plusDays(2),
                owner,
                now
        ));
        tripMemberRepository.save(TripMemberEntity.owner(trip, owner, now));
        return trip;
    }

    private UserEntity createUser() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        Instant now = Instant.now();
        return userRepository.save(UserEntity.createOauthUser(
                "chat-" + suffix + "@example.com",
                "chat-" + suffix + "@example.com",
                "chat-user-" + suffix,
                true,
                now
        ));
    }

    private String accessToken(UserEntity user) {
        return jwtTokenProvider.issueAccessToken(user.getId(), UserRole.USER, Instant.now()).value();
    }

    private record SendBody(String clientMessageId, String body) {
    }
}
