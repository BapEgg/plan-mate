package com.planmate.chat.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.planmate.auth.security.JwtTokenProvider;
import com.planmate.chat.repository.ChatMessageRepository;
import com.planmate.chat.entity.ChatMessageEntity;
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
import java.util.Map;
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

        assertThat(chatMessageRepository.findByTripIdAndSentAtAfterOrderByIdDesc(
                trip.getId(), java.time.Instant.EPOCH, org.springframework.data.domain.PageRequest.of(0, 10)))
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

    @Test
    void sinceReturnsOnlyNewerMessagesAscendingForGapRecovery() throws Exception {
        UserEntity owner = createUser();
        TripEntity trip = createTrip(owner);
        long[] ids = new long[5];
        for (int i = 0; i < 5; i++) {
            String response = mockMvc.perform(post("/api/trips/{tripId}/chat/messages", trip.getId())
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken(owner))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(new SendBody(UUID.randomUUID().toString(), "메시지 " + i))))
                    .andExpect(status().isCreated())
                    .andReturn().getResponse().getContentAsString();
            ids[i] = objectMapper.readTree(response).get("id").asLong();
        }

        mockMvc.perform(get("/api/trips/{tripId}/chat/messages", trip.getId())
                        .param("since", String.valueOf(ids[1]))
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.messages.length()").value(3))
                .andExpect(jsonPath("$.messages[0].body").value("메시지 2"))
                .andExpect(jsonPath("$.messages[1].body").value("메시지 3"))
                .andExpect(jsonPath("$.messages[2].body").value("메시지 4"))
                .andExpect(jsonPath("$.nextCursor").doesNotExist());
    }

    @Test
    void membersCanReplyAndKeepOnlyOneReactionPerMessage() throws Exception {
        UserEntity owner = createUser();
        UserEntity member = createUser();
        TripEntity trip = createTrip(owner);
        tripMemberRepository.save(TripMemberEntity.member(trip, member, Instant.now()));

        String firstResponse = mockMvc.perform(post("/api/trips/{tripId}/chat/messages", trip.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new SendBody(UUID.randomUUID().toString(), "첫 장소는 매미성 어때요?"))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long firstMessageId = objectMapper.readTree(firstResponse).get("id").asLong();

        mockMvc.perform(post("/api/trips/{tripId}/chat/messages", trip.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken(member))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ReplySendBody(
                                UUID.randomUUID().toString(),
                                "좋아요. 점심 전에 들러요.",
                                firstMessageId
                        ))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.replyTo.messageId").value(firstMessageId))
                .andExpect(jsonPath("$.replyTo.body").value("첫 장소는 매미성 어때요?"))
                .andExpect(jsonPath("$.replyTo.deleted").value(false));

        mockMvc.perform(put("/api/trips/{tripId}/chat/messages/{messageId}/reaction", trip.getId(), firstMessageId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken(member))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reaction\":\"LIKE\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reactions.length()").value(1))
                .andExpect(jsonPath("$.reactions[0].reaction").value("LIKE"))
                .andExpect(jsonPath("$.reactions[0].count").value(1))
                .andExpect(jsonPath("$.reactions[0].reactedByMe").value(true));

        mockMvc.perform(put("/api/trips/{tripId}/chat/messages/{messageId}/reaction", trip.getId(), firstMessageId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken(member))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reaction\":\"ACKNOWLEDGED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reactions.length()").value(1))
                .andExpect(jsonPath("$.reactions[0].reaction").value("ACKNOWLEDGED"));

        mockMvc.perform(delete("/api/trips/{tripId}/chat/messages/{messageId}/reaction", trip.getId(), firstMessageId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken(member)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reactions.length()").value(0));
    }

    @Test
    void authorCanDeleteWithinFiveMinutesAndRepliesKeepATombstonePreview() throws Exception {
        UserEntity owner = createUser();
        UserEntity member = createUser();
        TripEntity trip = createTrip(owner);
        tripMemberRepository.save(TripMemberEntity.member(trip, member, Instant.now()));

        String firstResponse = mockMvc.perform(post("/api/trips/{tripId}/chat/messages", trip.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new SendBody(UUID.randomUUID().toString(), "삭제할 원문"))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long firstMessageId = objectMapper.readTree(firstResponse).get("id").asLong();

        mockMvc.perform(post("/api/trips/{tripId}/chat/messages", trip.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken(member))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ReplySendBody(
                                UUID.randomUUID().toString(),
                                "원문에 대한 답장",
                                firstMessageId
                        ))))
                .andExpect(status().isCreated());

        mockMvc.perform(delete("/api/trips/{tripId}/chat/messages/{messageId}", trip.getId(), firstMessageId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken(member)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("MESSAGE_DELETE_FORBIDDEN"));

        mockMvc.perform(delete("/api/trips/{tripId}/chat/messages/{messageId}", trip.getId(), firstMessageId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.deleted").value(true))
                .andExpect(jsonPath("$.body").value("삭제된 메시지입니다."));

        mockMvc.perform(get("/api/trips/{tripId}/chat/messages", trip.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken(member)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.messages[0].replyTo.messageId").value(firstMessageId))
                .andExpect(jsonPath("$.messages[0].replyTo.deleted").value(true))
                .andExpect(jsonPath("$.messages[0].replyTo.body").value("삭제된 메시지입니다."));

        mockMvc.perform(put("/api/trips/{tripId}/chat/messages/{messageId}/reaction", trip.getId(), firstMessageId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken(member))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reaction\":\"LIKE\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("MESSAGE_ALREADY_DELETED"));
    }

    @Test
    void deleteWindowUsesServerSentTime() throws Exception {
        UserEntity owner = createUser();
        TripEntity trip = createTrip(owner);
        ChatMessageEntity oldMessage = chatMessageRepository.save(ChatMessageEntity.userText(
                trip.getId(),
                owner.getId(),
                "오래된 메시지",
                UUID.randomUUID().toString(),
                Instant.now().minusSeconds(301),
                null
        ));

        mockMvc.perform(delete("/api/trips/{tripId}/chat/messages/{messageId}", trip.getId(), oldMessage.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken(owner)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("MESSAGE_DELETE_WINDOW_EXPIRED"));
    }

    @Test
    void memberMentionSearchAndContextUseTheServerConfirmedMessage() throws Exception {
        UserEntity owner = createUser();
        UserEntity member = createUser();
        TripEntity trip = createTrip(owner);
        tripMemberRepository.save(TripMemberEntity.member(trip, member, Instant.now()));
        String body = "@" + member.getNickname() + " 거제 카페에서 만나요";
        int mentionEnd = body.codePointCount(0, ("@" + member.getNickname()).length());

        String created = mockMvc.perform(post("/api/trips/{tripId}/chat/messages", trip.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "clientMessageId", UUID.randomUUID().toString(),
                                "body", body,
                                "mentions", List.of(Map.of(
                                        "memberId", member.getId(),
                                        "startCodePoint", 0,
                                        "endCodePoint", mentionEnd
                                ))
                        ))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.mentions.length()").value(1))
                .andExpect(jsonPath("$.mentions[0].memberId").value(member.getId()))
                .andExpect(jsonPath("$.mentions[0].displayNameSnapshot").value(member.getNickname()))
                .andReturn().getResponse().getContentAsString();
        long messageId = objectMapper.readTree(created).get("id").asLong();

        mockMvc.perform(get("/api/trips/{tripId}/chat/messages/search", trip.getId())
                        .param("q", "거제 카페")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken(member)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results.length()").value(1))
                .andExpect(jsonPath("$.results[0].messageId").value(messageId))
                .andExpect(jsonPath("$.results[0].snippet").value(body))
                .andExpect(jsonPath("$.results[0].matchedRanges.length()").value(1));

        mockMvc.perform(get("/api/trips/{tripId}/chat/messages/{messageId}/context", trip.getId(), messageId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken(member)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.messages[0].id").value(messageId));

        mockMvc.perform(delete("/api/trips/{tripId}/chat/messages/{messageId}", trip.getId(), messageId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken(owner)))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/trips/{tripId}/chat/messages/search", trip.getId())
                        .param("q", "거제")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken(member)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results.length()").value(0));
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

    private record ReplySendBody(String clientMessageId, String body, Long replyToMessageId) {
    }
}
