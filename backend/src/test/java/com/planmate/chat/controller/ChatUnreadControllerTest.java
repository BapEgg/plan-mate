package com.planmate.chat.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.planmate.auth.security.JwtTokenProvider;
import com.planmate.trip.entity.TripEntity;
import com.planmate.trip.entity.TripMemberEntity;
import com.planmate.trip.repository.TripMemberRepository;
import com.planmate.trip.repository.TripRepository;
import com.planmate.user.domain.UserRole;
import com.planmate.user.entity.UserEntity;
import com.planmate.user.repository.UserRepository;
import jakarta.persistence.EntityManager;
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
class ChatUnreadControllerTest {

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
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private EntityManager entityManager;

    @Test
    void unreadCountExcludesMyOwnMessagesAndCountsOthers() throws Exception {
        UserEntity owner = createUser();
        UserEntity member = createUser();
        TripEntity trip = createTrip(owner);
        addMember(trip, member);

        send(trip, owner, "owner 1");
        send(trip, owner, "owner 2");
        send(trip, member, "member 1");

        mockMvc.perform(get("/api/trips/{tripId}/chat/unread-count", trip.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken(member)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.unreadCount").value(2));

        mockMvc.perform(get("/api/trips/{tripId}/chat/unread-count", trip.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.unreadCount").value(1));
    }

    @Test
    void markingReadBringsUnreadCountDownAndDoesNotRegressOnAnOlderId() throws Exception {
        UserEntity owner = createUser();
        UserEntity member = createUser();
        TripEntity trip = createTrip(owner);
        addMember(trip, member);

        Long firstId = send(trip, owner, "첫 메시지");
        Long secondId = send(trip, owner, "두번째 메시지");

        mockMvc.perform(post("/api/trips/{tripId}/chat/read", trip.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken(member))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ReadBody(secondId))))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/trips/{tripId}/chat/unread-count", trip.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken(member)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.unreadCount").value(0));

        // An out-of-order markRead for an older message must not move the read position backward.
        mockMvc.perform(post("/api/trips/{tripId}/chat/read", trip.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken(member))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ReadBody(firstId))))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/trips/{tripId}/chat/unread-count", trip.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken(member)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.unreadCount").value(0));
    }

    @Test
    void rejoiningAfterLeavingExcludesThePreviousIntervalsMessagesFromHistoryAndUnread() throws Exception {
        UserEntity owner = createUser();
        UserEntity member = createUser();
        TripEntity trip = createTrip(owner);
        addMember(trip, member);
        send(trip, owner, "떠나기 전 메시지");

        mockMvc.perform(post("/api/trips/{tripId}/leave", trip.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken(member)))
                .andExpect(status().isNoContent());

        entityManager.flush();
        entityManager.clear();
        TripEntity reloadedTrip = tripRepository.findById(trip.getId()).orElseThrow();
        UserEntity reloadedOwner = userRepository.findById(owner.getId()).orElseThrow();
        UserEntity reloadedMember = userRepository.findById(member.getId()).orElseThrow();
        addMember(reloadedTrip, reloadedMember);
        send(reloadedTrip, reloadedOwner, "재가입 뒤 메시지");

        mockMvc.perform(get("/api/trips/{tripId}/chat/unread-count", trip.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken(reloadedMember)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.unreadCount").value(1));

        mockMvc.perform(get("/api/trips/{tripId}/chat/messages", trip.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken(reloadedMember)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.messages.length()").value(1))
                .andExpect(jsonPath("$.messages[0].body").value("재가입 뒤 메시지"));
    }

    @Test
    void markReadRejectsAMessageIdFromAnotherTrip() throws Exception {
        UserEntity owner = createUser();
        TripEntity tripA = createTrip(owner);
        TripEntity tripB = createTrip(owner);
        Long messageInTripB = send(tripB, owner, "다른 여행방 메시지");

        mockMvc.perform(post("/api/trips/{tripId}/chat/read", tripA.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ReadBody(messageInTripB))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("MESSAGE_NOT_FOUND"));
    }

    private Long send(TripEntity trip, UserEntity author, String body) throws Exception {
        String response = mockMvc.perform(post("/api/trips/{tripId}/chat/messages", trip.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken(author))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new SendBody(UUID.randomUUID().toString(), body))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("id").asLong();
    }

    private TripMemberEntity addMember(TripEntity trip, UserEntity user) {
        return tripMemberRepository.save(TripMemberEntity.member(trip, user, Instant.now()));
    }

    private TripEntity createTrip(UserEntity owner) {
        Instant now = Instant.now();
        TripEntity trip = tripRepository.save(TripEntity.create(
                "Chat unread test trip",
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
                "chat-unread-" + suffix + "@example.com",
                "chat-unread-" + suffix + "@example.com",
                "chat-unread-user-" + suffix,
                true,
                now
        ));
    }

    private String accessToken(UserEntity user) {
        return jwtTokenProvider.issueAccessToken(user.getId(), UserRole.USER, Instant.now()).value();
    }

    private record SendBody(String clientMessageId, String body) {
    }

    private record ReadBody(Long messageId) {
    }
}
