package com.planmate.tripdetail.application;

import com.planmate.itinerary.api.ItineraryReadModel;
import com.planmate.itinerary.api.LatestItineraryReader;
import com.planmate.trip.api.TripDetailTrip;
import com.planmate.trip.api.TripDetailTripReader;
import com.planmate.tripdetail.dto.TripDetailResponse;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class TripDetailQueryFacade {

    private final TripDetailTripReader tripReader;
    private final LatestItineraryReader itineraryReader;

    public TripDetailQueryFacade(
            TripDetailTripReader tripReader,
            LatestItineraryReader itineraryReader
    ) {
        this.tripReader = tripReader;
        this.itineraryReader = itineraryReader;
    }

    public TripDetailResponse getDetail(Long userId, Long tripId) {
        TripDetailTrip trip = tripReader.getAccessibleTrip(userId, tripId);
        List<TripDetailResponse.Itinerary> itineraries = itineraryReader.findLatestByTripId(trip.id())
                .map(this::toItineraryResponse)
                .map(List::of)
                .orElseGet(List::of);

        return new TripDetailResponse(
                trip.id().toString(),
                trip.title(),
                trip.destination(),
                trip.destinationPlaceId(),
                trip.startDate(),
                trip.endDate(),
                trip.status(),
                trip.memberCount(),
                trip.createdAt(),
                trip.members(),
                trip.destinationInfo(),
                trip.planningProfile(),
                itineraries
        );
    }

    private TripDetailResponse.Itinerary toItineraryResponse(ItineraryReadModel itinerary) {
        return new TripDetailResponse.Itinerary(
                itinerary.id(),
                itinerary.generationId(),
                itinerary.createdAt(),
                itinerary.days().stream()
                        .map(this::toDayResponse)
                        .toList()
        );
    }

    private TripDetailResponse.Day toDayResponse(ItineraryReadModel.Day day) {
        return new TripDetailResponse.Day(
                day.id(),
                day.day(),
                day.date(),
                day.items().stream()
                        .map(this::toItemResponse)
                        .toList()
        );
    }

    private TripDetailResponse.Item toItemResponse(ItineraryReadModel.Item item) {
        return new TripDetailResponse.Item(
                item.id(),
                item.sequence(),
                item.placeId(),
                item.startTime(),
                item.durationMinutes(),
                item.createdSource()
        );
    }
}
