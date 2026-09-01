package com.planmate.membership.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.planmate.auth.security.JwtTokenProvider;
import com.planmate.trip.entity.LeftReason;
import com.planmate.trip.entity.MembershipStatus;
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

/**
 * WP-B exit gate: 세 계정(OWNER/MEMBER/non-member) 권한 E2E, 재가입 boundary, 제거 즉시 REST
 * 차단.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class TripMembershipControllerTest {

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
    void ownerCanRemoveMemberAndRemovedUserImmediatelyLosesRestAccess() throws Exception {
        UserEntity owner = createUser();
        UserEntity member = createUser();
        TripEntity trip = createTrip(owner);
        addMember(trip, member);

        mockMvc.perform(delete("/api/trips/{tripId}/members/{userId}", trip.getId(), member.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken(owner)))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/trips/{tripId}", trip.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken(member)))
                .andExpect(status().isNotFound());

        TripMemberEntity removed = tripMemberRepository
                .findByTrip_IdAndUser_IdAndStatus(trip.getId(), member.getId(), MembershipStatus.REMOVED)
                .orElseThrow();
        assertThat(removed.getLeftReason()).isEqualTo(LeftReason.REMOVED);
    }

    @Test
    void memberCannotRemoveAnotherMember() throws Exception {
        UserEntity owner = createUser();
        UserEntity memberA = createUser();
        UserEntity memberB = createUser();
        TripEntity trip = createTrip(owner);
        addMember(trip, memberA);
        addMember(trip, memberB);

        mockMvc.perform(delete("/api/trips/{tripId}/members/{userId}", trip.getId(), memberB.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken(memberA)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void nonMemberCannotRemoveAnyoneAndGetsNotFoundNotForbidden() throws Exception {
        UserEntity owner = createUser();
        UserEntity member = createUser();
        UserEntity outsider = createUser();
        TripEntity trip = createTrip(owner);
        addMember(trip, member);

        mockMvc.perform(delete("/api/trips/{tripId}/members/{userId}", trip.getId(), member.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken(outsider)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("TRIP_NOT_FOUND"));
    }

    @Test
    void memberCanLeaveButOwnerCannot() throws Exception {
        UserEntity owner = createUser();
        UserEntity member = createUser();
        TripEntity trip = createTrip(owner);
        addMember(trip, member);

        mockMvc.perform(post("/api/trips/{tripId}/leave", trip.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken(member)))
                .andExpect(status().isNoContent());

        mockMvc.perform(post("/api/trips/{tripId}/leave", trip.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken(owner)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("OWNER_CANNOT_LEAVE"));
    }

    @Test
    void rejoiningAfterLeavingCreatesANewActiveIntervalWithoutTouchingTheOldOne() throws Exception {
        UserEntity owner = createUser();
        UserEntity member = createUser();
        TripEntity trip = createTrip(owner);
        TripMemberEntity firstInterval = addMember(trip, member);

        mockMvc.perform(post("/api/trips/{tripId}/leave", trip.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken(member)))
                .andExpect(status().isNoContent());

        entityManager.flush();
        entityManager.clear();
        TripMemberEntity secondInterval = addMember(trip, member);

        List<TripMemberEntity> intervals = tripMemberRepository.findByTrip_IdAndStatusOrderByCreatedAtAsc(trip.getId(), MembershipStatus.ACTIVE);
        assertThat(intervals).extracting(TripMemberEntity::getId).contains(secondInterval.getId());
        assertThat(intervals).extracting(TripMemberEntity::getId).doesNotContain(firstInterval.getId());

        TripMemberEntity reloadedFirst = tripMemberRepository.findById(firstInterval.getId()).orElseThrow();
        assertThat(reloadedFirst.getStatus()).isEqualTo(MembershipStatus.LEFT);
    }

    @Test
    void ownerCanUpdateTitleButMemberCannot() throws Exception {
        UserEntity owner = createUser();
        UserEntity member = createUser();
        TripEntity trip = createTrip(owner);
        addMember(trip, member);

        mockMvc.perform(patch("/api/trips/{tripId}/title", trip.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new TitleBody("  새   제목  \n입니다  "))))
                .andExpect(status().isNoContent());

        TripEntity reloaded = tripRepository.findById(trip.getId()).orElseThrow();
        assertThat(reloaded.getTitle()).isEqualTo("새 제목 입니다");

        mockMvc.perform(patch("/api/trips/{tripId}/title", trip.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken(member))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new TitleBody("멤버가 바꾸려는 제목"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void titleLongerThanThirtyGraphemesIsRejected() throws Exception {
        UserEntity owner = createUser();
        TripEntity trip = createTrip(owner);
        String tooLong = "가".repeat(31);

        mockMvc.perform(patch("/api/trips/{tripId}/title", trip.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new TitleBody(tooLong))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_TRIP_TITLE"));
    }

    @Test
    void ownerTransferAtomicallySwapsRolesOnlyWhenTargetAccepts() throws Exception {
        UserEntity owner = createUser();
        UserEntity member = createUser();
        TripEntity trip = createTrip(owner);
        addMember(trip, member);

        String responseJson = mockMvc.perform(post("/api/trips/{tripId}/owner-transfer-requests", trip.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new TargetBody(member.getId()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andReturn().getResponse().getContentAsString();
        Long requestId = objectMapper.readTree(responseJson).get("id").asLong();

        // duplicate PENDING request for the same trip is rejected
        mockMvc.perform(post("/api/trips/{tripId}/owner-transfer-requests", trip.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new TargetBody(member.getId()))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE_OWNER_TRANSFER_REQUEST"));

        mockMvc.perform(post("/api/owner-transfer-requests/{id}/accept", requestId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken(member)))
                .andExpect(status().isNoContent());

        TripMemberEntity newOwner = tripMemberRepository
                .findByTrip_IdAndUser_IdAndStatus(trip.getId(), member.getId(), MembershipStatus.ACTIVE).orElseThrow();
        TripMemberEntity formerOwner = tripMemberRepository
                .findByTrip_IdAndUser_IdAndStatus(trip.getId(), owner.getId(), MembershipStatus.ACTIVE).orElseThrow();
        assertThat(newOwner.getRole().name()).isEqualTo("OWNER");
        assertThat(formerOwner.getRole().name()).isEqualTo("MEMBER");
    }

    private TripEntity createTrip(UserEntity owner) {
        Instant now = Instant.now();
        TripEntity trip = tripRepository.save(TripEntity.create(
                "Membership test trip",
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

    private TripMemberEntity addMember(TripEntity trip, UserEntity user) {
        return tripMemberRepository.save(TripMemberEntity.member(trip, user, Instant.now()));
    }

    private UserEntity createUser() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        Instant now = Instant.now();
        return userRepository.save(UserEntity.createOauthUser(
                "membership-" + suffix + "@example.com",
                "membership-" + suffix + "@example.com",
                "membership-user-" + suffix,
                true,
                now
        ));
    }

    private String accessToken(UserEntity user) {
        return jwtTokenProvider.issueAccessToken(user.getId(), UserRole.USER, Instant.now()).value();
    }

    private record TitleBody(String title) {
    }

    private record TargetBody(Long targetUserId) {
    }
}
