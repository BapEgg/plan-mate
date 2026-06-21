package com.planmate.trip.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.planmate.auth.security.JwtTokenProvider;
import com.planmate.place.service.GooglePlacesService;
import com.planmate.trip.entity.TripMemberRole;
import com.planmate.trip.repository.TripMemberRepository;
import com.planmate.user.domain.UserRole;
import com.planmate.user.entity.UserEntity;
import com.planmate.user.repository.UserRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class TripControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TripMemberRepository tripMemberRepository;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @MockitoBean
    private GooglePlacesService googlePlacesService;

    @Test
    void createTripCreatesOwnerMembership() throws Exception {
        UserEntity user = createUser();
        String accessToken = accessToken(user);
        LocalDate startDate = LocalDate.now().plusDays(10);
        LocalDate endDate = startDate.plusDays(2);

        mockMvc.perform(post("/api/trips")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "강릉 2박 3일",
                                  "destination": "강릉",
                                  "destinationPlaceId": "place-gangneung",
                                  "startDate": "%s",
                                  "endDate": "%s"
                                }
                                """.formatted(startDate, endDate)))
                .andExpect(status().isCreated())
                .andExpect(header().string(HttpHeaders.LOCATION, org.hamcrest.Matchers.startsWith("/api/trips/")))
                .andExpect(jsonPath("$.title").value("강릉 2박 3일"))
                .andExpect(jsonPath("$.destination").value("강릉"))
                .andExpect(jsonPath("$.destinationPlaceId").value("place-gangneung"))
                .andExpect(jsonPath("$.status").value("UPCOMING"))
                .andExpect(jsonPath("$.memberCount").value(1));

        assertThat(tripMemberRepository.findByUser_IdOrderByTrip_CreatedAtDesc(user.getId()))
                .hasSize(1)
                .first()
                .extracting(member -> member.getRole())
                .isEqualTo(TripMemberRole.OWNER);
    }

    @Test
    void listMineReturnsOnlyMyTrips() throws Exception {
        UserEntity me = createUser();
        UserEntity other = createUser();
        String myToken = accessToken(me);
        String otherToken = accessToken(other);

        createTrip(myToken, "내 여행", "서울", LocalDate.now().plusDays(1), LocalDate.now().plusDays(3));
        createTrip(otherToken, "다른 사용자 여행", "부산", LocalDate.now().plusDays(5), LocalDate.now().plusDays(6));

        mockMvc.perform(get("/api/trips")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + myToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].title").value("내 여행"))
                .andExpect(jsonPath("$[0].memberCount").value(1));
    }

    @Test
    void getDetailReturnsTripAndMembers() throws Exception {
        UserEntity user = createUser();
        String accessToken = accessToken(user);
        String tripId = createTrip(
                accessToken,
                "상세 여행",
                "제주",
                LocalDate.now().minusDays(1),
                LocalDate.now().plusDays(1)
        );

        mockMvc.perform(get("/api/trips/{tripId}", tripId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(tripId))
                .andExpect(jsonPath("$.title").value("상세 여행"))
                .andExpect(jsonPath("$.destinationPlaceId").value("place-제주"))
                .andExpect(jsonPath("$.status").value("PLANNING"))
                .andExpect(jsonPath("$.memberCount").value(1))
                .andExpect(jsonPath("$.members.length()").value(1))
                .andExpect(jsonPath("$.members[0].userId").value(user.getId()))
                .andExpect(jsonPath("$.members[0].nickname").value(user.getNickname()))
                .andExpect(jsonPath("$.members[0].role").value("OWNER"));
    }

    @Test
    void getDetailReturnsNotFoundForNonMember() throws Exception {
        UserEntity owner = createUser();
        UserEntity other = createUser();
        String tripId = createTrip(
                accessToken(owner),
                "비공개 여행",
                "속초",
                LocalDate.now().plusDays(1),
                LocalDate.now().plusDays(2)
        );

        mockMvc.perform(get("/api/trips/{tripId}", tripId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken(other)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("TRIP_NOT_FOUND"));
    }

    @Test
    void createTripRejectsInvalidDateRange() throws Exception {
        UserEntity user = createUser();
        String accessToken = accessToken(user);

        mockMvc.perform(post("/api/trips")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "잘못된 여행",
                                  "destination": "서울",
                                  "destinationPlaceId": "place-seoul",
                                  "startDate": "2026-08-10",
                                  "endDate": "2026-08-01"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    private String createTrip(
            String accessToken,
            String title,
            String destination,
            LocalDate startDate,
            LocalDate endDate
    ) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/trips")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "%s",
                                  "destination": "%s",
                                  "destinationPlaceId": "place-%s",
                                  "startDate": "%s",
                                  "endDate": "%s"
                                }
                                """.formatted(title, destination, destination, startDate, endDate)))
                .andExpect(status().isCreated())
                .andReturn();

        JsonNode response = objectMapper.readTree(result.getResponse().getContentAsString());
        return response.get("id").asText();
    }

    private UserEntity createUser() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        Instant now = Instant.now();
        return userRepository.save(UserEntity.createOauthUser(
                "trip-" + suffix + "@example.com",
                "trip-" + suffix + "@example.com",
                "trip-user-" + suffix,
                true,
                now
        ));
    }

    private String accessToken(UserEntity user) {
        return jwtTokenProvider.issueAccessToken(user.getId(), UserRole.USER, Instant.now()).value();
    }

}
