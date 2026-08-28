package com.planmate.place.controller;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.planmate.auth.security.JwtTokenProvider;
import com.planmate.place.api.PlaceAutocompleteItem;
import com.planmate.place.api.PlaceAutocompleteQuery;
import com.planmate.place.api.PlaceAutocompleteResult;
import com.planmate.place.api.PlaceDetailsResolver;
import com.planmate.place.api.PlaceDisplayReader;
import com.planmate.place.api.PlaceTextSearcher;
import com.planmate.place.api.exception.InvalidPlaceIdException;
import com.planmate.place.api.exception.PlaceProviderUnavailableException;
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

    @MockitoBean(extraInterfaces = {
            PlaceDetailsResolver.class,
            PlaceDisplayReader.class,
            PlaceTextSearcher.class
    })
    private PlaceAutocompleteQuery placeAutocompleteQuery;

    @Test
    void autocompleteReturnsNormalizedPlaces() throws Exception {
        UserEntity user = createUser();
        String accessToken = accessToken(user);
        given(placeAutocompleteQuery.autocomplete(eq("Gangneung"), eq("ko")))
                .willReturn(new PlaceAutocompleteResult(List.of(
                        new PlaceAutocompleteItem(
                                "place-gangneung",
                                "Gangneung",
                                "Gangwon-do, Korea",
                                "Gangneung, Gangwon-do, Korea",
                                List.of("locality", "political"),
                                "CITY"
                        )
                )));

        mockMvc.perform(post("/api/places/autocomplete")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "query": "Gangneung",
                                  "languageCode": "ko"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].placeId").value("place-gangneung"))
                .andExpect(jsonPath("$.items[0].mainText").value("Gangneung"))
                .andExpect(jsonPath("$.items[0].secondaryText").value("Gangwon-do, Korea"))
                .andExpect(jsonPath("$.items[0].displayText").value("Gangneung, Gangwon-do, Korea"))
                .andExpect(jsonPath("$.items[0].types[0]").value("locality"))
                .andExpect(jsonPath("$.items[0].searchScope").value("CITY"));
    }

    @Test
    void autocompletePlaceInDestinationReturnsDestinationBiasedPlaces() throws Exception {
        UserEntity user = createUser();
        String accessToken = accessToken(user);
        given(placeAutocompleteQuery.autocompleteInDestination(eq("Fushimi Inari"), eq("place-kyoto"), eq("ko")))
                .willReturn(new PlaceAutocompleteResult(List.of(
                        new PlaceAutocompleteItem(
                                "place-fushimi-inari",
                                "Fushimi Inari Shrine",
                                "Kyoto, Japan",
                                "Fushimi Inari Shrine, Kyoto, Japan",
                                List.of("tourist_attraction", "point_of_interest", "establishment"),
                                "PLACE"
                        )
                )));

        mockMvc.perform(post("/api/places/destination/autocomplete")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "query": "Fushimi Inari",
                                  "destinationPlaceId": "place-kyoto",
                                  "languageCode": "ko"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].placeId").value("place-fushimi-inari"))
                .andExpect(jsonPath("$.items[0].mainText").value("Fushimi Inari Shrine"))
                .andExpect(jsonPath("$.items[0].secondaryText").value("Kyoto, Japan"))
                .andExpect(jsonPath("$.items[0].displayText").value("Fushimi Inari Shrine, Kyoto, Japan"))
                .andExpect(jsonPath("$.items[0].types[0]").value("tourist_attraction"))
                .andExpect(jsonPath("$.items[0].searchScope").value("PLACE"));
    }

    @Test
    void autocompleteReturnsInvalidPlaceIdError() throws Exception {
        UserEntity user = createUser();
        String accessToken = accessToken(user);
        given(placeAutocompleteQuery.autocompleteAccommodation(eq("Hotel"), eq("bad-place"), eq("ko")))
                .willThrow(new InvalidPlaceIdException());

        mockMvc.perform(post("/api/places/accommodations/autocomplete")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "query": "Hotel",
                                  "destinationPlaceId": "bad-place",
                                  "languageCode": "ko"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PLACE_ID"))
                .andExpect(jsonPath("$.message").value("Invalid destination place id."));
    }

    @Test
    void autocompleteReturnsProviderUnavailableError() throws Exception {
        UserEntity user = createUser();
        String accessToken = accessToken(user);
        given(placeAutocompleteQuery.autocomplete(eq("Gangneung"), eq("ko")))
                .willThrow(new PlaceProviderUnavailableException());

        mockMvc.perform(post("/api/places/autocomplete")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "query": "Gangneung",
                                  "languageCode": "ko"
                                }
                                """))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("PLACE_PROVIDER_UNAVAILABLE"))
                .andExpect(jsonPath("$.message").value("External place service is unavailable."));
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
