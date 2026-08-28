package com.planmate.trip.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.planmate.trip.api.TripPlanningSnapshot;
import com.planmate.trip.domain.AccommodationMode;
import com.planmate.trip.domain.AvoidCondition;
import com.planmate.trip.domain.BudgetItem;
import com.planmate.trip.domain.BudgetLevel;
import com.planmate.trip.domain.ChildAgeGroup;
import com.planmate.trip.domain.CompanionType;
import com.planmate.trip.domain.CurrencyCode;
import com.planmate.trip.domain.MustVisitPlaceSnapshot;
import com.planmate.trip.domain.TransportMode;
import com.planmate.trip.domain.TravelPace;
import com.planmate.trip.domain.TripInterest;
import com.planmate.trip.entity.TripEntity;
import com.planmate.trip.entity.TripPlanningProfileEntity;
import com.planmate.trip.exception.TripNotFoundException;
import com.planmate.trip.repository.TripPlanningProfileRepository;
import com.planmate.trip.repository.TripRepository;
import java.lang.reflect.RecordComponent;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TripPlanningSnapshotQueryServiceTest {

    @Mock
    private TripRepository tripRepository;

    @Mock
    private TripPlanningProfileRepository tripPlanningProfileRepository;

    @Mock
    private TripEntity trip;

    @Mock
    private TripPlanningProfileEntity profile;

    private TripPlanningSnapshotQueryService service;

    @BeforeEach
    void setUp() {
        service = new TripPlanningSnapshotQueryService(tripRepository, tripPlanningProfileRepository);
    }

    @Test
    void checkAccessibleReturnsWhenTripIsAccessible() {
        given(tripRepository.findAccessibleTrip(45L, 7L)).willReturn(Optional.of(trip));

        service.checkAccessible(7L, 45L);

        verify(tripRepository).findAccessibleTrip(45L, 7L);
    }

    @Test
    void checkAccessibleThrowsTripNotFoundWhenTripIsNotAccessible() {
        given(tripRepository.findAccessibleTrip(45L, 7L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.checkAccessible(7L, 45L))
                .isInstanceOf(TripNotFoundException.class);
    }

    @Test
    void findByTripIdMapsAllPlanningFieldsToSnapshot() {
        given(tripPlanningProfileRepository.findWithTripByTrip_Id(45L)).willReturn(Optional.of(profile));
        givenTrip();
        givenProfile();

        TripPlanningSnapshot snapshot = service.findByTripId(45L).orElseThrow();

        assertThat(snapshot.tripId()).isEqualTo(45L);
        assertThat(snapshot.startDate()).isEqualTo(LocalDate.of(2026, 4, 1));
        assertThat(snapshot.endDate()).isEqualTo(LocalDate.of(2026, 4, 3));
        assertThat(snapshot.destination()).satisfies(destination -> {
            assertThat(destination.placeId()).isEqualTo("place-kyoto");
            assertThat(destination.displayName()).isEqualTo("Kyoto");
            assertThat(destination.formattedAddress()).isEqualTo("Kyoto, Japan");
            assertThat(destination.latitude()).isEqualTo(35.0);
            assertThat(destination.longitude()).isEqualTo(135.0);
            assertThat(destination.viewport().lowLatitude()).isEqualTo(34.8);
            assertThat(destination.types()).containsExactly("locality", "political");
            assertThat(destination.primaryType()).isEqualTo("locality");
        });
        assertThat(snapshot.companion()).satisfies(companion -> {
            assertThat(companion.companionCount()).isEqualTo(3);
            assertThat(companion.companionType()).isEqualTo("FAMILY");
            assertThat(companion.hasChildren()).isTrue();
            assertThat(companion.childAgeGroup()).isEqualTo("ELEMENTARY");
            assertThat(companion.hasSeniors()).isTrue();
        });
        assertThat(snapshot.budget()).satisfies(budget -> {
            assertThat(budget.currencyCode()).isEqualTo("KRW");
            assertThat(budget.amount()).isEqualTo(1_000_000L);
            assertThat(budget.level()).isEqualTo("BALANCED");
            assertThat(budget.includedItems()).containsExactly("LODGING", "FOOD");
        });
        assertThat(snapshot.preference().travelPace()).isEqualTo("BALANCED");
        assertThat(snapshot.preference().interests()).containsExactly("FOOD", "SIGHTSEEING");
        assertThat(snapshot.transportation().primaryMode()).isEqualTo("PUBLIC_TRANSIT");
        assertThat(snapshot.transportation().secondaryModes()).containsExactly("WALK");
        assertThat(snapshot.accommodation()).satisfies(accommodation -> {
            assertThat(accommodation.accommodationMode()).isEqualTo("PLACE_SEARCH");
            assertThat(accommodation.preferredArea()).isNull();
            assertThat(accommodation.placeId()).isEqualTo("hotel-place");
            assertThat(accommodation.latitude()).isEqualTo(35.1);
            assertThat(accommodation.types()).containsExactly("lodging");
        });
        assertThat(snapshot.dailyStartTime()).isEqualTo(LocalTime.of(8, 0));
        assertThat(snapshot.dailyEndTime()).isEqualTo(LocalTime.of(20, 0));
        assertThat(snapshot.mustVisitPlaces())
                .extracting(TripPlanningSnapshot.MustVisitPlace::placeId)
                .containsExactly("must-1");
        assertThat(snapshot.avoidConditions()).containsExactly("LONG_WALK");
        assertThat(snapshot.freeRequest()).isEqualTo("Keep lunch flexible.");
    }

    @Test
    void findByTripIdReturnsEmptyWhenPlanningProfileIsMissing() {
        given(tripPlanningProfileRepository.findWithTripByTrip_Id(45L)).willReturn(Optional.empty());

        assertThat(service.findByTripId(45L)).isEmpty();
    }

    @Test
    void findByTripIdHandlesNullableAccommodationAndEmptyMustVisitPlaces() {
        given(tripPlanningProfileRepository.findWithTripByTrip_Id(45L)).willReturn(Optional.of(profile));
        givenTrip();
        givenProfile();
        given(profile.getAccommodationMode()).willReturn(AccommodationMode.UNDECIDED);
        given(profile.getAccommodationArea()).willReturn(null);
        given(profile.getAccommodationName()).willReturn(null);
        given(profile.getAccommodationPlaceId()).willReturn(null);
        given(profile.getAccommodationFormattedAddress()).willReturn(null);
        given(profile.getAccommodationLatitude()).willReturn(null);
        given(profile.getAccommodationLongitude()).willReturn(null);
        given(profile.getAccommodationTypes()).willReturn(null);
        given(profile.getAccommodationPrimaryType()).willReturn(null);
        given(profile.getMustVisitPlaces()).willReturn(List.of());

        TripPlanningSnapshot snapshot = service.findByTripId(45L).orElseThrow();

        assertThat(snapshot.accommodation().placeId()).isNull();
        assertThat(snapshot.accommodation().types()).isEmpty();
        assertThat(snapshot.mustVisitPlaces()).isEmpty();
    }

    @Test
    void snapshotListFieldsAreImmutableCopies() {
        List<String> destinationTypes = new ArrayList<>(List.of("locality"));
        List<String> interests = new ArrayList<>(List.of("FOOD"));
        TripPlanningSnapshot snapshot = new TripPlanningSnapshot(
                45L,
                LocalDate.of(2026, 4, 1),
                LocalDate.of(2026, 4, 3),
                new TripPlanningSnapshot.Destination("place", "Kyoto", "address", 35.0, 135.0, null, destinationTypes, "locality"),
                new TripPlanningSnapshot.Companion(2, "FRIENDS", false, 0, null, false, 0),
                new TripPlanningSnapshot.Budget("KRW", 1000L, "BALANCED", List.of("FOOD")),
                new TripPlanningSnapshot.Preference("BALANCED", interests),
                new TripPlanningSnapshot.Transportation("WALK", List.of()),
                new TripPlanningSnapshot.Accommodation("UNDECIDED", null, null, null, null, null, null, null, null, null, null),
                LocalTime.of(8, 0),
                LocalTime.of(20, 0),
                null,
                null,
                null
        );

        destinationTypes.add("political");
        interests.add("CAFE");

        assertThat(snapshot.destination().types()).containsExactly("locality");
        assertThat(snapshot.preference().interests()).containsExactly("FOOD");
        assertThat(snapshot.mustVisitPlaces()).isEmpty();
        assertThat(snapshot.avoidConditions()).isEmpty();
        assertThatThrownBy(() -> snapshot.preference().interests().add("CAFE"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void snapshotDoesNotExposePersistenceTypes() {
        assertThat(recordComponentTypes(TripPlanningSnapshot.class))
                .noneMatch(type -> type.startsWith("com.planmate.trip.entity"))
                .noneMatch(type -> type.startsWith("com.planmate.trip.repository"));
    }

    private List<String> recordComponentTypes(Class<?> type) {
        return Stream.concat(
                        Arrays.stream(type.getRecordComponents()).map(RecordComponent::getType),
                        Arrays.stream(type.getDeclaredClasses())
                                .filter(Class::isRecord)
                                .flatMap(nested -> Arrays.stream(nested.getRecordComponents()).map(RecordComponent::getType))
                )
                .map(Class::getName)
                .toList();
    }

    private void givenTrip() {
        given(profile.getTrip()).willReturn(trip);
        given(trip.getId()).willReturn(45L);
        given(trip.getStartDate()).willReturn(LocalDate.of(2026, 4, 1));
        given(trip.getEndDate()).willReturn(LocalDate.of(2026, 4, 3));
        given(trip.getDestinationPlaceId()).willReturn("place-kyoto");
        given(trip.getDestination()).willReturn("Kyoto");
        given(trip.getDestinationFormattedAddress()).willReturn("Kyoto, Japan");
        given(trip.getDestinationLatitude()).willReturn(35.0);
        given(trip.getDestinationLongitude()).willReturn(135.0);
        given(trip.getDestinationViewportLowLatitude()).willReturn(34.8);
        given(trip.getDestinationViewportLowLongitude()).willReturn(134.8);
        given(trip.getDestinationViewportHighLatitude()).willReturn(35.2);
        given(trip.getDestinationViewportHighLongitude()).willReturn(135.2);
        given(trip.getDestinationTypes()).willReturn(List.of("locality", "political"));
        given(trip.getDestinationPrimaryType()).willReturn("locality");
    }

    private void givenProfile() {
        given(profile.getCompanionCount()).willReturn(3);
        given(profile.getCompanionType()).willReturn(CompanionType.FAMILY);
        given(profile.isHasChildren()).willReturn(true);
        given(profile.getChildCount()).willReturn(1);
        given(profile.getChildAgeGroup()).willReturn(ChildAgeGroup.ELEMENTARY);
        given(profile.isHasSeniors()).willReturn(true);
        given(profile.getSeniorCount()).willReturn(1);
        given(profile.getCurrencyCode()).willReturn(CurrencyCode.KRW);
        given(profile.getBudgetAmount()).willReturn(1_000_000L);
        given(profile.getBudgetLevel()).willReturn(BudgetLevel.BALANCED);
        given(profile.getIncludedBudgetItems()).willReturn(List.of(BudgetItem.LODGING, BudgetItem.FOOD));
        given(profile.getTravelPace()).willReturn(TravelPace.BALANCED);
        given(profile.getInterests()).willReturn(List.of(TripInterest.FOOD, TripInterest.SIGHTSEEING));
        given(profile.getPrimaryTransportMode()).willReturn(TransportMode.PUBLIC_TRANSIT);
        given(profile.getSecondaryTransportModes()).willReturn(List.of(TransportMode.WALK));
        given(profile.getAccommodationMode()).willReturn(AccommodationMode.PLACE_SEARCH);
        given(profile.getAccommodationArea()).willReturn(null);
        given(profile.getAccommodationName()).willReturn("Hotel");
        given(profile.getAccommodationPlaceId()).willReturn("hotel-place");
        given(profile.getAccommodationFormattedAddress()).willReturn("Hotel address");
        given(profile.getAccommodationLatitude()).willReturn(35.1);
        given(profile.getAccommodationLongitude()).willReturn(135.1);
        given(profile.getAccommodationTypes()).willReturn(List.of("lodging"));
        given(profile.getAccommodationPrimaryType()).willReturn("lodging");
        given(profile.getCheckInTime()).willReturn(LocalTime.of(15, 0));
        given(profile.getCheckOutTime()).willReturn(LocalTime.of(11, 0));
        given(profile.getDailyStartTime()).willReturn(LocalTime.of(8, 0));
        given(profile.getDailyEndTime()).willReturn(LocalTime.of(20, 0));
        given(profile.getMustVisitPlaces()).willReturn(List.of(new MustVisitPlaceSnapshot(
                "must-1",
                "Kiyomizu",
                "Kiyomizu address",
                35.0,
                135.0,
                List.of("tourist_attraction"),
                "tourist_attraction"
        )));
        given(profile.getAvoidConditions()).willReturn(List.of(AvoidCondition.LONG_WALK));
        given(profile.getFreeRequest()).willReturn("Keep lunch flexible.");
    }
}
