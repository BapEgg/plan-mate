package com.planmate.place.controller;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.planmate.auth.security.JwtTokenProvider;
import com.planmate.place.dto.PlaceAutocompleteItemResponse;
import com.planmate.place.dto.PlaceAutocompleteResponse;
import com.planmate.place.service.GooglePlacesService;
import com.planmate.user.domain.UserRole;
import com.planmate.user.entity.UserEntity;
import com.planmate.user.repository.UserRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class PlaceControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @MockitoBean
    private GooglePlacesService googlePlacesService;

    @Test
    void autocompleteReturnsNormalizedPlaces() throws Exception {
        UserEntity user = createUser();
        String accessToken = accessToken(user);
        given(googlePlacesService.autocomplete(eq("강릉"), eq("ko")))
                .willReturn(new PlaceAutocompleteResponse(List.of(
                        new PlaceAutocompleteItemResponse(
                                "place-gangneung",
                                "강릉",
                                "강원특별자치도, 대한민국",
                                "강릉, 강원특별자치도, 대한민국",
                                List.of("locality", "political"),
                                "CITY"
                        )
                )));

        mockMvc.perform(post("/api/places/autocomplete")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "query": "강릉",
                                  "languageCode": "ko"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].placeId").value("place-gangneung"))
                .andExpect(jsonPath("$.items[0].mainText").value("강릉"))
                .andExpect(jsonPath("$.items[0].secondaryText").value("강원특별자치도, 대한민국"))
                .andExpect(jsonPath("$.items[0].displayText").value("강릉, 강원특별자치도, 대한민국"))
                .andExpect(jsonPath("$.items[0].types[0]").value("locality"))
                .andExpect(jsonPath("$.items[0].searchScope").value("CITY"));
    }

    @Test
    void autocompletePlaceInDestinationReturnsDestinationBiasedPlaces() throws Exception {
        UserEntity user = createUser();
        String accessToken = accessToken(user);
        given(googlePlacesService.autocompleteInDestination(eq("후시미이나리"), eq("place-kyoto"), eq("ko")))
                .willReturn(new PlaceAutocompleteResponse(List.of(
                        new PlaceAutocompleteItemResponse(
                                "place-fushimi-inari",
                                "후시미이나리 신사",
                                "교토, 일본",
                                "후시미이나리 신사, 교토, 일본",
                                List.of("tourist_attraction", "point_of_interest", "establishment"),
                                "PLACE"
                        )
                )));

        mockMvc.perform(post("/api/places/destination/autocomplete")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "query": "후시미이나리",
                                  "destinationPlaceId": "place-kyoto",
                                  "languageCode": "ko"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].placeId").value("place-fushimi-inari"))
                .andExpect(jsonPath("$.items[0].mainText").value("후시미이나리 신사"))
                .andExpect(jsonPath("$.items[0].secondaryText").value("교토, 일본"))
                .andExpect(jsonPath("$.items[0].displayText").value("후시미이나리 신사, 교토, 일본"))
                .andExpect(jsonPath("$.items[0].types[0]").value("tourist_attraction"))
                .andExpect(jsonPath("$.items[0].searchScope").value("PLACE"));
    }

    private UserEntity createUser() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        Instant now = Instant.now();
        return userRepository.save(UserEntity.createOauthUser(
                "place-" + suffix + "@example.com",
                "place-" + suffix + "@example.com",
                "place-user-" + suffix,
                true,
                now
        ));
    }

    private String accessToken(UserEntity user) {
        return jwtTokenProvider.issueAccessToken(user.getId(), UserRole.USER, Instant.now()).value();
    }

}
