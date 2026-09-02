package com.planmate.realtime.presence;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.planmate.auth.security.JwtTokenProvider;
import com.planmate.realtime.RealtimeSessionRegistry;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class PresenceControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired UserRepository userRepository;
    @Autowired TripRepository tripRepository;
    @Autowired TripMemberRepository memberRepository;
    @Autowired RealtimeSessionRegistry sessionRegistry;
    @Autowired JwtTokenProvider tokenProvider;

    @Test
    void snapshotReflectsAuthenticatedTripSubscriptionsOnly() throws Exception {
        UserEntity owner = user();
        UserEntity member = user();
        Instant now = Instant.now();
        TripEntity trip = tripRepository.save(TripEntity.create(
                "presence", "거제", "geoje", "거제", 34.8, 128.6,
                null, null, null, null, List.of("locality"), "locality",
                LocalDate.now().plusDays(1), LocalDate.now().plusDays(2), owner, now
        ));
        memberRepository.save(TripMemberEntity.owner(trip, owner, now));
        memberRepository.save(TripMemberEntity.member(trip, member, now));
        sessionRegistry.registerSubscription("presence-owner-" + trip.getId(), owner.getId(), trip.getId());

        mockMvc.perform(get("/api/trips/{tripId}/presence", trip.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.members[?(@.memberId == " + owner.getId() + ")].status").value("ONLINE"))
                .andExpect(jsonPath("$.members[?(@.memberId == " + member.getId() + ")].status").value("OFFLINE"));
    }

    private UserEntity user() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        return userRepository.save(UserEntity.createOauthUser(
                suffix + "@example.com", suffix + "@example.com", "사용자" + suffix, true, Instant.now()
        ));
    }

    private String token(UserEntity user) {
        return tokenProvider.issueAccessToken(user.getId(), UserRole.USER, Instant.now()).value();
    }
}
