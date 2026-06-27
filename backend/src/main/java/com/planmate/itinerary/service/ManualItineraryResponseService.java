package com.planmate.itinerary.service;

import com.planmate.itinerary.dto.AiItineraryResponse;
import com.planmate.itinerary.entity.ItineraryDayEntity;
import com.planmate.itinerary.entity.ItineraryEntity;
import com.planmate.itinerary.entity.ItineraryGenerationEntity;
import com.planmate.itinerary.entity.ItineraryGenerationStatus;
import com.planmate.itinerary.entity.ItineraryItemEntity;
import com.planmate.itinerary.entity.PlaceCandidateEntity;
import com.planmate.itinerary.exception.ItineraryErrorCode;
import com.planmate.itinerary.exception.ItineraryException;
import com.planmate.itinerary.repository.ItineraryDayRepository;
import com.planmate.itinerary.repository.ItineraryGenerationRepository;
import com.planmate.itinerary.repository.ItineraryItemRepository;
import com.planmate.itinerary.repository.ItineraryRepository;
import com.planmate.itinerary.repository.PlaceCandidateRepository;
import com.planmate.trip.entity.TripEntity;
import com.planmate.trip.exception.TripNotFoundException;
import com.planmate.trip.repository.TripRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ManualItineraryResponseService {

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");

    private final TripRepository tripRepository;
    private final ItineraryGenerationRepository generationRepository;
    private final PlaceCandidateRepository placeCandidateRepository;
    private final ItineraryRepository itineraryRepository;
    private final ItineraryDayRepository itineraryDayRepository;
    private final ItineraryItemRepository itineraryItemRepository;
    private final Clock clock;

    public ManualItineraryResponseService(
            TripRepository tripRepository,
            ItineraryGenerationRepository generationRepository,
            PlaceCandidateRepository placeCandidateRepository,
            ItineraryRepository itineraryRepository,
            ItineraryDayRepository itineraryDayRepository,
            ItineraryItemRepository itineraryItemRepository,
            Clock clock
    ) {
        this.tripRepository = tripRepository;
        this.generationRepository = generationRepository;
        this.placeCandidateRepository = placeCandidateRepository;
        this.itineraryRepository = itineraryRepository;
        this.itineraryDayRepository = itineraryDayRepository;
        this.itineraryItemRepository = itineraryItemRepository;
        this.clock = clock;
    }

    @Transactional
    public void submit(Long userId, Long tripId, Long generationId, AiItineraryResponse response) {
        TripEntity trip = tripRepository.findAccessibleTrip(tripId, userId)
                .orElseThrow(TripNotFoundException::new);
        ItineraryGenerationEntity generation = generationRepository.findWithTripById(generationId)
                .orElseThrow(() -> new ItineraryException(ItineraryErrorCode.GENERATION_NOT_FOUND));
        if (!generation.getTrip().getId().equals(trip.getId())) {
            throw new ItineraryException(ItineraryErrorCode.GENERATION_NOT_FOUND);
        }
        if (generation.getStatus() != ItineraryGenerationStatus.READY_FOR_PLANNING) {
            throw new ItineraryException(ItineraryErrorCode.GENERATION_NOT_READY);
        }
        if (response == null || !generation.getId().toString().equals(response.generationId())) {
            throw invalid("generationId does not match.");
        }

        List<PlaceCandidateEntity> candidates = placeCandidateRepository.findByGeneration_IdOrderByRankAsc(generationId);
        Map<String, PlaceCandidateEntity> candidateByPlaceId = new HashMap<>();
        for (PlaceCandidateEntity candidate : candidates) {
            candidateByPlaceId.put(candidate.getPlaceId(), candidate);
        }

        validateResponse(trip, response, candidateByPlaceId);

        Instant now = Instant.now(clock);
        generation.markValidating(now);
        ItineraryEntity itinerary = itineraryRepository.save(ItineraryEntity.create(trip, generation, response.summary(), now));
        for (AiItineraryResponse.Day responseDay : response.days()) {
            ItineraryDayEntity day = itineraryDayRepository.save(ItineraryDayEntity.create(
                    itinerary,
                    responseDay.day(),
                    responseDay.date()
            ));
            for (AiItineraryResponse.Item responseItem : responseDay.items()) {
                PlaceCandidateEntity candidate = candidateByPlaceId.get(responseItem.placeId());
                itineraryItemRepository.save(ItineraryItemEntity.create(
                        day,
                        responseItem.sequence(),
                        candidate,
                        parseTime(responseItem.startTime()),
                        responseItem.durationMinutes(),
                        responseItem.reason()
                ));
            }
        }
        generation.markCompleted(now);
    }

    private void validateResponse(
            TripEntity trip,
            AiItineraryResponse response,
            Map<String, PlaceCandidateEntity> candidateByPlaceId
    ) {
        if (response.summary() != null && response.summary().length() > 500) {
            throw invalid("summary is too long.");
        }
        if (response.days() == null || response.days().isEmpty()) {
            throw invalid("days is required.");
        }

        Set<Integer> days = new HashSet<>();
        Set<String> usedPlaceIds = new HashSet<>();
        for (AiItineraryResponse.Day day : response.days()) {
            validateDay(trip, day, days, candidateByPlaceId, usedPlaceIds);
        }
    }

    private void validateDay(
            TripEntity trip,
            AiItineraryResponse.Day day,
            Set<Integer> days,
            Map<String, PlaceCandidateEntity> candidateByPlaceId,
            Set<String> usedPlaceIds
    ) {
        if (day.day() < 1 || !days.add(day.day())) {
            throw invalid("day is duplicated or invalid.");
        }
        LocalDate expectedDate = trip.getStartDate().plusDays(day.day() - 1L);
        if (!expectedDate.equals(day.date()) || day.date().isBefore(trip.getStartDate()) || day.date().isAfter(trip.getEndDate())) {
            throw invalid("day and date do not match the trip date range.");
        }
        if (day.items() == null || day.items().isEmpty()) {
            throw invalid("day items are required.");
        }

        Set<Integer> sequences = new HashSet<>();
        for (AiItineraryResponse.Item item : day.items()) {
            validateItem(item, sequences, candidateByPlaceId, usedPlaceIds);
        }
    }

    private void validateItem(
            AiItineraryResponse.Item item,
            Set<Integer> sequences,
            Map<String, PlaceCandidateEntity> candidateByPlaceId,
            Set<String> usedPlaceIds
    ) {
        if (item.sequence() < 1 || !sequences.add(item.sequence())) {
            throw invalid("sequence is duplicated or invalid.");
        }
        PlaceCandidateEntity candidate = candidateByPlaceId.get(item.placeId());
        if (candidate == null) {
            throw invalid("placeId is not registered in this generation.");
        }
        if (!candidate.getName().equals(item.placeName())) {
            throw invalid("placeName does not match the registered candidate.");
        }
        if (!usedPlaceIds.add(item.placeId())) {
            throw invalid("placeId is repeated too often.");
        }
        parseTime(item.startTime());
        if (item.durationMinutes() <= 0) {
            throw invalid("durationMinutes must be positive.");
        }
        if (item.reason() != null && item.reason().length() > 500) {
            throw invalid("reason is too long.");
        }
    }

    private LocalTime parseTime(String value) {
        try {
            return LocalTime.parse(value, TIME_FORMATTER);
        } catch (DateTimeParseException | NullPointerException exception) {
            throw invalid("startTime must use HH:mm format.");
        }
    }

    private ItineraryException invalid(String message) {
        return new ItineraryException(ItineraryErrorCode.INVALID_AI_RESPONSE, message);
    }
}
