package com.planmate.trip.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.planmate.auth.security.JwtTokenProvider;
import com.planmate.itinerary.entity.ItineraryDayEntity;
import com.planmate.itinerary.entity.ItineraryEntity;
import com.planmate.itinerary.entity.ItineraryGenerationEntity;
import com.planmate.itinerary.entity.ItineraryItemCreatedSource;
import com.planmate.itinerary.entity.ItineraryItemEntity;
import com.planmate.itinerary.repository.ItineraryDayRepository;
import com.planmate.itinerary.repository.ItineraryGenerationRepository;
import com.planmate.itinerary.repository.ItineraryItemRepository;
import com.planmate.itinerary.repository.ItineraryRepository;
import com.planmate.place.api.GeoPoint;
import com.planmate.place.api.GeoViewport;
import com.planmate.place.api.PlaceAutocompleteQuery;
import com.planmate.place.api.PlaceDetailsResolver;
import com.planmate.place.api.PlaceDisplayReader;
import com.planmate.place.api.PlaceTextSearcher;
import com.planmate.place.api.ResolvedPlace;
import com.planmate.trip.domain.TripInterest;
import com.planmate.trip.entity.TripEntity;
import com.planmate.trip.entity.TripMemberRole;
import com.planmate.trip.repository.TripMemberRepository;
import com.planmate.trip.repository.TripPlanningProfileRepository;
import com.planmate.trip.repository.TripRepository;
import com.planmate.user.domain.UserRole;
import com.planmate.user.entity.UserEntity;
import com.planmate.user.repository.UserRepository;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
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
    private TripPlanningProfileRepository tripPlanningProfileRepository;

    @Autowired
    private TripRepository tripRepository;

    @Autowired
    private ItineraryGenerationRepository itineraryGenerationRepository;

    @Autowired
    private ItineraryRepository itineraryRepository;

    @Autowired
    private ItineraryDayRepository itineraryDayRepository;

    @Autowired
    private ItineraryItemRepository itineraryItemRepository;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @MockitoBean(extraInterfaces = {
            PlaceAutocompleteQuery.class,
            PlaceDisplayReader.class,
            PlaceTextSearcher.class
    })
    private PlaceDetailsResolver placeDetailsResolver;

    @BeforeEach
    void setUp() {
        given(placeDetailsResolver.resolve(anyString(), any()))
                .willAnswer(invocation -> resolvedDestination(invocation.getArgument(0)));
    }

    @Test
    void createTripCreatesOwnerMembershipAndPlanningProfile() throws Exception {
        UserEntity user = createUser();
        String accessToken = accessToken(user);
        LocalDate startDate = LocalDate.now().plusDays(10);
        LocalDate endDate = startDate.plusDays(2);

        MvcResult result = mockMvc.perform(post("/api/trips")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(tripRequestJson("Kyoto autumn", "place-kyoto", startDate, endDate)))
                .andExpect(status().isCreated())
                .andExpect(header().string(HttpHeaders.LOCATION, org.hamcrest.Matchers.startsWith("/api/trips/")))
                .andExpect(jsonPath("$.title").value("Kyoto autumn"))
                .andExpect(jsonPath("$.destination").value("Resolved place-kyoto"))
                .andExpect(jsonPath("$.destinationPlaceId").value("place-kyoto"))
                .andExpect(jsonPath("$.status").value("UPCOMING"))
                .andExpect(jsonPath("$.memberCount").value(1))
                .andReturn();

        JsonNode response = objectMapper.readTree(result.getResponse().getContentAsString());
        Long tripId = response.get("id").asLong();

        assertThat(tripMemberRepository.findByUser_IdOrderByTrip_CreatedAtDesc(user.getId()))
                .hasSize(1)
                .first()
                .extracting(member -> member.getRole())
                .isEqualTo(TripMemberRole.OWNER);

        assertThat(tripPlanningProfileRepository.findByTrip_Id(tripId))
                .isPresent()
                .get()
                .satisfies(profile -> {
                    assertThat(profile.getCompanionCount()).isEqualTo(3);
                    assertThat(profile.getInterests()).contains(TripInterest.FOOD, TripInterest.SIGHTSEEING);
                    assertThat(profile.getMustVisitPlaces())
                            .extracting("placeId")
                            .containsExactly("place-kiyomizu");
                    assertThat(profile.getMustVisitPlaces())
                            .extracting("name")
                            .containsExactly("Resolved place-kiyomizu");
                    assertThat(profile.getDailyStartTime()).isEqualTo(LocalTime.of(8, 0));
                    assertThat(profile.getDailyEndTime()).isEqualTo(LocalTime.of(20, 0));
                });
    }

    @Test
    void createTripStoresSelectedAccommodationSnapshotFromGoogleDetails() throws Exception {
        UserEntity user = createUser();
        String accessToken = accessToken(user);
        LocalDate startDate = LocalDate.now().plusDays(10);
        LocalDate endDate = startDate.plusDays(1);

        MvcResult result = mockMvc.perform(post("/api/trips")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(tripRequestJson(
                                "Hotel based trip",
                                "place-fukuoka",
                                startDate,
                                endDate,
                                selectedAccommodationJson("general-building-place"),
                                schedulePreferenceJson(null, null)
                        )))
                .andExpect(status().isCreated())
                .andReturn();

        Long tripId = objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();

        assertThat(tripPlanningProfileRepository.findByTrip_Id(tripId))
                .isPresent()
                .get()
                .satisfies(profile -> {
                    assertThat(profile.getAccommodationArea()).isNull();
                    assertThat(profile.getAccommodationPlaceId()).isEqualTo("general-building-place");
                    assertThat(profile.getAccommodationName()).isEqualTo("Resolved general-building-place");
                    assertThat(profile.getAccommodationFormattedAddress()).isEqualTo("Resolved address");
                    assertThat(profile.getAccommodationLatitude()).isEqualTo(35.0);
                    assertThat(profile.getAccommodationLongitude()).isEqualTo(135.0);
                    assertThat(profile.getAccommodationTypes()).contains("locality", "political");
                    assertThat(profile.getAccommodationPrimaryType()).isEqualTo("locality");
                    assertThat(profile.getCheckInTime()).isNull();
                    assertThat(profile.getCheckOutTime()).isNull();
                });
    }

    @Test
    void createTripRejectsSelectedAccommodationWithoutPlaceId() throws Exception {
        UserEntity user = createUser();
        String accessToken = accessToken(user);
        LocalDate startDate = LocalDate.now().plusDays(10);

        mockMvc.perform(post("/api/trips")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(tripRequestJson(
                                "Invalid accommodation",
                                "place-seoul",
                                startDate,
                                startDate.plusDays(1),
                                selectedAccommodationJson(""),
                                schedulePreferenceJson(null, null)
                        )))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void createTripRejectsAccommodationWithoutLocation() throws Exception {
        given(placeDetailsResolver.resolve(eq("no-location-place"), any()))
                .willReturn(resolvedDestinationWithoutLocation("no-location-place"));
        UserEntity user = createUser();
        String accessToken = accessToken(user);
        LocalDate startDate = LocalDate.now().plusDays(10);

        mockMvc.perform(post("/api/trips")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(tripRequestJson(
                                "No location accommodation",
                                "place-seoul",
                                startDate,
                                startDate.plusDays(1),
                                selectedAccommodationJson("no-location-place"),
                                schedulePreferenceJson(null, null)
                        )))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_TRIP_REQUEST"));
    }

    @Test
    void createTripStoresCustomDailyScheduleWindow() throws Exception {
        UserEntity user = createUser();
        String accessToken = accessToken(user);
        LocalDate startDate = LocalDate.now().plusDays(10);

        MvcResult result = mockMvc.perform(post("/api/trips")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(tripRequestJson(
                                "Custom window",
                                "place-osaka",
                                startDate,
                                startDate.plusDays(1),
                                undecidedAccommodationJson(),
                                schedulePreferenceJson("09:30", "21:15")
                        )))
                .andExpect(status().isCreated())
                .andReturn();

        Long tripId = objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();
        assertThat(tripPlanningProfileRepository.findByTrip_Id(tripId))
                .isPresent()
                .get()
                .satisfies(profile -> {
                    assertThat(profile.getDailyStartTime()).isEqualTo(LocalTime.of(9, 30));
                    assertThat(profile.getDailyEndTime()).isEqualTo(LocalTime.of(21, 15));
                });
    }

    @Test
    void createTripAppliesDefaultOnlyForMissingScheduleSide() throws Exception {
        UserEntity user = createUser();
        String accessToken = accessToken(user);
        LocalDate startDate = LocalDate.now().plusDays(10);

        MvcResult result = mockMvc.perform(post("/api/trips")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(tripRequestJson(
                                "Partial window",
                                "place-osaka",
                                startDate,
                                startDate.plusDays(1),
                                undecidedAccommodationJson(),
                                schedulePreferenceJson("09:00", null)
                        )))
                .andExpect(status().isCreated())
                .andReturn();

        Long tripId = objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();
        assertThat(tripPlanningProfileRepository.findByTrip_Id(tripId))
                .isPresent()
                .get()
                .satisfies(profile -> {
                    assertThat(profile.getDailyStartTime()).isEqualTo(LocalTime.of(9, 0));
                    assertThat(profile.getDailyEndTime()).isEqualTo(LocalTime.of(20, 0));
                });
    }

    @Test
    void createTripRejectsInvalidScheduleWindow() throws Exception {
        UserEntity user = createUser();
        String accessToken = accessToken(user);
        LocalDate startDate = LocalDate.now().plusDays(10);

        mockMvc.perform(post("/api/trips")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(tripRequestJson(
                                "Invalid window",
                                "place-osaka",
                                startDate,
                                startDate.plusDays(1),
                                undecidedAccommodationJson(),
                                schedulePreferenceJson("20:00", "20:00")
                        )))
                .andExpect(status().isBadRequest());
    }

    @Test
    void listMineReturnsOnlyMyTrips() throws Exception {
        UserEntity me = createUser();
        UserEntity other = createUser();
        String myToken = accessToken(me);
        String otherToken = accessToken(other);

        createTrip(myToken, "My trip", "Seoul", LocalDate.now().plusDays(1), LocalDate.now().plusDays(3));
        createTrip(otherToken, "Other trip", "Busan", LocalDate.now().plusDays(5), LocalDate.now().plusDays(6));

        mockMvc.perform(get("/api/trips")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + myToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].title").value("My trip"))
                .andExpect(jsonPath("$[0].memberCount").value(1));
    }

    @Test
    void getDetailReturnsTripProfileMembersAndItineraries() throws Exception {
        UserEntity user = createUser();
        String accessToken = accessToken(user);
        String tripId = createTrip(
                accessToken,
                "Detail trip",
                "Jeju",
                LocalDate.now().minusDays(1),
                LocalDate.now().plusDays(1)
        );

        mockMvc.perform(get("/api/trips/{tripId}", tripId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(tripId))
                .andExpect(jsonPath("$.title").value("Detail trip"))
                .andExpect(jsonPath("$.destinationPlaceId").value("place-Jeju"))
                .andExpect(jsonPath("$.destinationInfo.displayName").value("Resolved place-Jeju"))
                .andExpect(jsonPath("$.planningProfile.companionCount").value(3))
                .andExpect(jsonPath("$.planningProfile.interests[0]").value("FOOD"))
                .andExpect(jsonPath("$.itineraries.length()").value(0))
                .andExpect(jsonPath("$.status").value("PLANNING"))
                .andExpect(jsonPath("$.memberCount").value(1))
                .andExpect(jsonPath("$.members.length()").value(1))
                .andExpect(jsonPath("$.members[0].userId").value(user.getId()))
                .andExpect(jsonPath("$.members[0].role").value("OWNER"));
    }

    @Test
    void getDetailReturnsLatestItineraryOnly() throws Exception {
        UserEntity user = createUser();
        String accessToken = accessToken(user);
        String tripId = createTrip(
                accessToken,
                "Latest itinerary trip",
                "Tokyo",
                LocalDate.now().plusDays(3),
                LocalDate.now().plusDays(5)
        );
        TripEntity trip = tripRepository.findById(Long.valueOf(tripId)).orElseThrow();
        ItineraryEntity oldItinerary = createItinerary(trip, Instant.parse("2026-08-01T00:00:00Z"), "old-place");
        ItineraryEntity latestItinerary = createItinerary(trip, Instant.parse("2026-08-02T00:00:00Z"), "new-place");
        Long latestItineraryId = latestItinerary.getId();
        Long latestGenerationId = latestItinerary.getGeneration().getId();

        assertThat(itineraryRepository.findByTripIdOrderByCreatedAtDesc(Long.valueOf(tripId)))
                .extracting(ItineraryEntity::getId)
                .containsExactly(latestItineraryId, oldItinerary.getId());
        assertThat(itineraryRepository.findFirstByTripIdOrderByCreatedAtDesc(Long.valueOf(tripId)))
                .isPresent()
                .get()
                .extracting(ItineraryEntity::getId)
                .isEqualTo(latestItineraryId);
        assertThat(itineraryGenerationRepository.findFirstByTripIdOrderByCreatedAtDesc(Long.valueOf(tripId)))
                .isPresent()
                .get()
                .extracting(ItineraryGenerationEntity::getId)
                .isEqualTo(latestGenerationId);

        entityManager.flush();
        entityManager.clear();

        mockMvc.perform(get("/api/trips/{tripId}", tripId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.itineraries.length()").value(1))
                .andExpect(jsonPath("$.itineraries[0].id").value(latestItineraryId.intValue()))
                .andExpect(jsonPath("$.itineraries[0].generationId").value(latestGenerationId.intValue()))
                .andExpect(jsonPath("$.itineraries[0].days.length()").value(1))
                .andExpect(jsonPath("$.itineraries[0].days[0].day").value(1))
                .andExpect(jsonPath("$.itineraries[0].days[0].items.length()").value(1))
                .andExpect(jsonPath("$.itineraries[0].days[0].items[0].placeId").value("new-place"))
                .andExpect(jsonPath("$.itineraries[0].days[0].items[0].createdSource").value("MANUAL_EDIT"));
    }

    @Test
    void getDetailReturnsNotFoundForNonMember() throws Exception {
        UserEntity owner = createUser();
        UserEntity other = createUser();
        String tripId = createTrip(
                accessToken(owner),
                "Private trip",
                "Sokcho",
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
                        .content(tripRequestJson(
                                "Invalid trip",
                                "place-seoul",
                                LocalDate.of(2026, 8, 10),
                                LocalDate.of(2026, 8, 1)
                        )))
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
                        .content(tripRequestJson(title, "place-" + destination, startDate, endDate)))
                .andExpect(status().isCreated())
                .andReturn();

        JsonNode response = objectMapper.readTree(result.getResponse().getContentAsString());
        return response.get("id").asText();
    }

    private ItineraryEntity createItinerary(TripEntity trip, Instant createdAt, String placeId) {
        ItineraryGenerationEntity generation = itineraryGenerationRepository.save(
                ItineraryGenerationEntity.create(trip.getId(), "test", createdAt)
        );
        generation.markCollecting(createdAt);
        generation.markReady(createdAt);
        generation.markCompleted(createdAt);
        ItineraryEntity itinerary = itineraryRepository.save(
                ItineraryEntity.create(generation, createdAt)
        );
        ItineraryDayEntity day = itineraryDayRepository.save(
                ItineraryDayEntity.create(itinerary, 1, trip.getStartDate())
        );
        itineraryItemRepository.save(ItineraryItemEntity.create(
                day,
                1,
                placeId,
                LocalTime.of(9, 0),
                60,
                ItineraryItemCreatedSource.MANUAL_EDIT
        ));
        return itinerary;
    }

    private String tripRequestJson(
            String title,
            String destinationPlaceId,
            LocalDate startDate,
            LocalDate endDate
    ) {
        return tripRequestJson(
                title,
                destinationPlaceId,
                startDate,
                endDate,
                undecidedAccommodationJson(),
                schedulePreferenceJson(null, null)
        );
    }

    private String tripRequestJson(
            String title,
            String destinationPlaceId,
            LocalDate startDate,
            LocalDate endDate,
            String accommodationJson,
            String schedulePreferenceJson
    ) {
        return """
                {
                  "title": "%s",
                  "destinationPlaceId": "%s",
                  "startDate": "%s",
                  "endDate": "%s",
                  "companion": {
                    "count": 3,
                    "type": "FRIENDS",
                    "hasChildren": false,
                    "childCount": 0,
                    "childAgeGroup": null,
                    "hasSeniors": false,
                    "seniorCount": 0
                  },
                  "budget": {
                    "currencyCode": "KRW",
                    "amount": 1000000,
                    "level": "BALANCED",
                    "includedItems": ["LODGING", "TRANSPORT", "FOOD"]
                  },
                  "preferences": {
                    "travelPace": "BALANCED",
                    "interests": ["FOOD", "SIGHTSEEING", "CAFE"]
                  },
                  "transportation": {
                    "primaryMode": "PUBLIC_TRANSIT",
                    "secondaryModes": ["WALK"]
                  },
                  "accommodation": %s,
                  "schedulePreference": %s,
                  "additionalRequest": {
                    "mustVisitPlaceIds": ["place-kiyomizu"],
                    "avoidConditions": ["LONG_WALK"],
                    "freeRequest": "Keep lunch flexible."
                  }
                }
                """.formatted(title, destinationPlaceId, startDate, endDate, accommodationJson, schedulePreferenceJson);
    }

    private String undecidedAccommodationJson() {
        return """
                {
                  "mode": "UNDECIDED",
                  "preferredArea": "TRANSIT",
                  "placeId": null,
                  "checkInTime": null,
                  "checkOutTime": null
                }
                """;
    }

    private String selectedAccommodationJson(String placeId) {
        String jsonValue = placeId == null ? "null" : "\"" + placeId + "\"";
        return """
                {
                  "mode": "PLACE_SEARCH",
                  "preferredArea": null,
                  "placeId": %s,
                  "checkInTime": null,
                  "checkOutTime": null
                }
                """.formatted(jsonValue);
    }

    private String schedulePreferenceJson(String dailyStartTime, String dailyEndTime) {
        String startValue = dailyStartTime == null ? "null" : "\"" + dailyStartTime + "\"";
        String endValue = dailyEndTime == null ? "null" : "\"" + dailyEndTime + "\"";
        return """
                {
                  "dailyStartTime": %s,
                  "dailyEndTime": %s
                }
                """.formatted(startValue, endValue);
    }

    private ResolvedPlace resolvedDestination(String placeId) {
        return new ResolvedPlace(
                placeId,
                "Resolved " + placeId,
                "Resolved address",
                new GeoPoint(35.0, 135.0),
                new GeoViewport(new GeoPoint(34.8, 134.8), new GeoPoint(35.2, 135.2)),
                List.of("locality", "political"),
                "locality"
        );
    }

    private ResolvedPlace resolvedDestinationWithoutLocation(String placeId) {
        return new ResolvedPlace(
                placeId,
                "Resolved " + placeId,
                "Resolved address",
                null,
                null,
                List.of("establishment"),
                "establishment"
        );
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
