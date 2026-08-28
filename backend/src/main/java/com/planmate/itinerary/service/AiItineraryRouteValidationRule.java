package com.planmate.itinerary.service;

import com.planmate.itinerary.api.validation.ValidationIssue;
import com.planmate.itinerary.api.validation.ValidationIssueCode;
import com.planmate.itinerary.api.validation.ValidationTarget;
import com.planmate.itinerary.domain.GenerationCandidateSnapshot;
import com.planmate.itinerary.domain.GenerationInputSnapshot;
import com.planmate.itinerary.exception.ItineraryErrorCode;
import com.planmate.itinerary.exception.ItineraryException;
import com.planmate.itinerary.route.RouteTravelTimePort;
import com.planmate.itinerary.route.RouteTravelTimePort.RoutePoint;
import com.planmate.itinerary.route.RouteTravelTimePort.RouteTravelTime;
import com.planmate.itinerary.route.RouteTravelTimePort.TravelMode;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class AiItineraryRouteValidationRule {

    private static final Comparator<ValidatedDraftItem> ITEM_ORDER = Comparator
            .comparingInt(ValidatedDraftItem::day)
            .thenComparingInt(ValidatedDraftItem::sequence)
            .thenComparingInt(ValidatedDraftItem::itemIndex);

    private final RouteTravelTimePort routeTravelTimePort;

    public AiItineraryRouteValidationRule(RouteTravelTimePort routeTravelTimePort) {
        this.routeTravelTimePort = routeTravelTimePort;
    }

    public Result validate(
            GenerationInputSnapshot inputSnapshot,
            List<GenerationCandidateSnapshot> candidates,
            List<ValidatedDraftItem> items
    ) {
        String primaryMode = inputSnapshot.transportation().primaryMode();
        if ("PUBLIC_TRANSIT".equals(primaryMode)) {
            return new Result(List.of(), List.of(ValidationIssue.of(
                    ValidationIssueCode.ADJACENT_ROUTE_NOT_VERIFIED,
                    "transportation.primaryMode",
                    null,
                    null,
                    null
            )));
        }

        TravelMode travelMode = travelMode(primaryMode);
        Map<String, GenerationCandidateSnapshot> candidatesByPlaceId = candidatesByPlaceId(candidates);
        Map<Integer, List<ValidatedDraftItem>> itemsByDay = itemsByDay(items);
        List<ValidationIssue> errors = new ArrayList<>();
        for (List<ValidatedDraftItem> dayItems : itemsByDay.values()) {
            for (int index = 1; index < dayItems.size(); index++) {
                ValidatedDraftItem previous = dayItems.get(index - 1);
                ValidatedDraftItem next = dayItems.get(index);
                if (next.startMinute() < previous.endMinute()) {
                    errors.add(sequenceTimeOrderIssue(previous, next));
                    continue;
                }
                if (previous.placeId().equals(next.placeId())) {
                    continue;
                }
                GenerationCandidateSnapshot origin = candidatesByPlaceId.get(previous.placeId());
                GenerationCandidateSnapshot destination = candidatesByPlaceId.get(next.placeId());
                if (origin == null || destination == null) {
                    continue;
                }
                Optional<RouteTravelTime> route = routeTravelTimePort.findRoute(
                        point(origin),
                        point(destination),
                        travelMode
                );
                long availableSeconds = (next.startMinute() - previous.endMinute()) * 60L;
                if (route.isEmpty()) {
                    errors.add(routeNotFoundIssue(previous, next));
                } else if (route.get().duration().compareTo(Duration.ofSeconds(availableSeconds)) > 0) {
                    errors.add(insufficientTravelTimeIssue(previous, next, availableSeconds, route.get().duration()));
                }
            }
        }
        return new Result(errors, List.of());
    }

    private TravelMode travelMode(String primaryMode) {
        return switch (primaryMode) {
            case "WALK" -> TravelMode.WALK;
            case "BIKE" -> TravelMode.BICYCLE;
            case "RENTAL_CAR", "TAXI", "TOUR" -> TravelMode.DRIVE;
            default -> throw new ItineraryException(ItineraryErrorCode.ROUTE_TRANSPORT_MODE_UNSUPPORTED);
        };
    }

    private Map<String, GenerationCandidateSnapshot> candidatesByPlaceId(
            List<GenerationCandidateSnapshot> candidates
    ) {
        Map<String, GenerationCandidateSnapshot> result = new HashMap<>();
        for (GenerationCandidateSnapshot candidate : candidates) {
            result.put(candidate.placeId().trim(), candidate);
        }
        return result;
    }

    private Map<Integer, List<ValidatedDraftItem>> itemsByDay(List<ValidatedDraftItem> items) {
        Map<Integer, List<ValidatedDraftItem>> result = new LinkedHashMap<>();
        items.stream().sorted(ITEM_ORDER).forEach(item -> result
                .computeIfAbsent(item.day(), ignored -> new ArrayList<>())
                .add(item));
        return result;
    }

    private RoutePoint point(GenerationCandidateSnapshot candidate) {
        if (!candidate.hasLocation()) {
            throw new ItineraryException(ItineraryErrorCode.GENERATION_CANDIDATE_LOCATION_INVALID);
        }
        return new RoutePoint(candidate.location().latitude(), candidate.location().longitude());
    }

    private ValidationIssue sequenceTimeOrderIssue(ValidatedDraftItem previous, ValidatedDraftItem next) {
        return issue(
                ValidationIssueCode.ITEM_SEQUENCE_TIME_ORDER_INVALID,
                "Sequence " + next.sequence() + " starts before sequence " + previous.sequence() + " ends.",
                previous,
                next,
                next.path() + ".startTime"
        );
    }

    private ValidationIssue routeNotFoundIssue(ValidatedDraftItem previous, ValidatedDraftItem next) {
        return issue(
                ValidationIssueCode.ADJACENT_ROUTE_NOT_FOUND,
                "No route was found from sequence " + previous.sequence() + " to sequence " + next.sequence() + ".",
                previous,
                next,
                next.path() + ".placeId"
        );
    }

    private ValidationIssue insufficientTravelTimeIssue(
            ValidatedDraftItem previous,
            ValidatedDraftItem next,
            long availableSeconds,
            Duration requiredDuration
    ) {
        return issue(
                ValidationIssueCode.INSUFFICIENT_TRAVEL_TIME,
                "Available gap is " + availableSeconds + " seconds but route requires "
                        + formatSeconds(requiredDuration) + " seconds.",
                previous,
                next,
                next.path() + ".startTime"
        );
    }

    private String formatSeconds(Duration duration) {
        long seconds = duration.getSeconds();
        return duration.getNano() == 0
                ? Long.toString(seconds)
                : seconds + "." + String.format("%09d", duration.getNano()).replaceFirst("0+$", "");
    }

    private ValidationIssue issue(
            ValidationIssueCode code,
            String message,
            ValidatedDraftItem previous,
            ValidatedDraftItem next,
            String path
    ) {
        return new ValidationIssue(
                code,
                message,
                path,
                next.day(),
                next.sequence(),
                next.placeId(),
                List.of(new ValidationTarget(
                        previous.path(),
                        previous.day(),
                        previous.sequence(),
                        previous.placeId()
                ))
        );
    }

    public record Result(
            List<ValidationIssue> errors,
            List<ValidationIssue> unverifiedConditions
    ) {
        public Result {
            errors = List.copyOf(errors);
            unverifiedConditions = List.copyOf(unverifiedConditions);
        }
    }
}
