package com.planmate.invitation.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.planmate.auth.security.JwtTokenProvider;
import com.planmate.invitation.entity.InvitationStatus;
import com.planmate.invitation.entity.TripInvitationEntity;
import com.planmate.invitation.repository.TripInvitationRepository;
import com.planmate.trip.entity.MembershipStatus;
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
class TripInvitationControllerTest {

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
    private TripInvitationRepository tripInvitationRepository;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Test
    void ownerCanInviteByExactEmailAndInviteeCanAccept() throws Exception {
        UserEntity owner = createUser();
        UserEntity invitee = createUser();
        TripEntity trip = createTrip(owner);

        String responseJson = mockMvc.perform(post("/api/trips/{tripId}/invitations", trip.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new EmailBody(invitee.getEmail().toUpperCase()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andReturn().getResponse().getContentAsString();
        Long invitationId = objectMapper.readTree(responseJson).get("id").asLong();

        mockMvc.perform(post("/api/invitations/{id}/accept", invitationId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken(invitee)))
                .andExpect(status().isNoContent());

        assertThat(tripMemberRepository.existsByTrip_IdAndUser_IdAndStatus(trip.getId(), invitee.getId(), MembershipStatus.ACTIVE))
                .isTrue();
    }

    @Test
    void duplicatePendingInvitationToTheSameUserIsRejected() throws Exception {
        UserEntity owner = createUser();
        UserEntity invitee = createUser();
        TripEntity trip = createTrip(owner);

        mockMvc.perform(post("/api/trips/{tripId}/invitations", trip.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UserIdBody(invitee.getId()))))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/trips/{tripId}/invitations", trip.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UserIdBody(invitee.getId()))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE_PENDING_INVITATION"));
    }

    @Test
    void invitationIsRejectedOncePendingCapacityWouldExceedTwenty() throws Exception {
        UserEntity owner = createUser();
        TripEntity trip = createTrip(owner);
        // owner already occupies 1 seat; fill 19 more with pending invites.
        for (int i = 0; i < 19; i++) {
            UserEntity invitee = createUser();
            mockMvc.perform(post("/api/trips/{tripId}/invitations", trip.getId())
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken(owner))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(new UserIdBody(invitee.getId()))))
                    .andExpect(status().isOk());
        }

        UserEntity oneTooMany = createUser();
        mockMvc.perform(post("/api/trips/{tripId}/invitations", trip.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UserIdBody(oneTooMany.getId()))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("TRIP_MEMBER_CAPACITY_EXCEEDED"));
    }

    @Test
    void expiredInvitationCannotBeAcceptedAndIsMarkedExpired() throws Exception {
        UserEntity owner = createUser();
        UserEntity invitee = createUser();
        TripEntity trip = createTrip(owner);
        Instant past = Instant.now().minusSeconds(60);
        TripInvitationEntity invitation = tripInvitationRepository.save(
                TripInvitationEntity.create(trip.getId(), invitee.getId(), owner.getId(), past.minus(TripInvitationEntity.VALIDITY).minusSeconds(1))
        );

        mockMvc.perform(post("/api/invitations/{id}/accept", invitation.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken(invitee)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVITATION_EXPIRED"));

        assertThat(tripInvitationRepository.findById(invitation.getId()).orElseThrow().getStatus())
                .isEqualTo(InvitationStatus.EXPIRED);
    }

    @Test
    void onlyOwnerCanSendInvitationsAndOnlyInviteeCanAcceptOrDecline() throws Exception {
        UserEntity owner = createUser();
        UserEntity member = createUser();
        UserEntity invitee = createUser();
        UserEntity someoneElse = createUser();
        TripEntity trip = createTrip(owner);
        tripMemberRepository.save(TripMemberEntity.member(trip, member, Instant.now()));

        mockMvc.perform(post("/api/trips/{tripId}/invitations", trip.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken(member))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UserIdBody(invitee.getId()))))
                .andExpect(status().isForbidden());

        TripInvitationEntity invitation = tripInvitationRepository.save(
                TripInvitationEntity.create(trip.getId(), invitee.getId(), owner.getId(), Instant.now())
        );

        mockMvc.perform(post("/api/invitations/{id}/accept", invitation.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken(someoneElse)))
                .andExpect(status().isForbidden());
    }

    private TripEntity createTrip(UserEntity owner) {
        Instant now = Instant.now();
        TripEntity trip = tripRepository.save(TripEntity.create(
                "Invitation test trip",
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
                "invite-" + suffix + "@example.com",
                "invite-" + suffix + "@example.com",
                "invite-user-" + suffix,
                true,
                now
        ));
    }

    private String accessToken(UserEntity user) {
        return jwtTokenProvider.issueAccessToken(user.getId(), UserRole.USER, Instant.now()).value();
    }

    private record UserIdBody(Long inviteeUserId) {
    }

    private record EmailBody(String inviteeEmail) {
    }
}
