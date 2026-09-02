package com.planmate.proposal.service;

import com.planmate.itinerary.dto.ItineraryPlaceDisplayView;
import com.planmate.itinerary.entity.ItineraryDayEntity;
import com.planmate.itinerary.entity.ItineraryEntity;
import com.planmate.itinerary.entity.ItineraryItemEntity;
import com.planmate.itinerary.repository.ItineraryRepository;
import com.planmate.itinerary.route.RouteTravelTimePort.RoutePoint;
import com.planmate.itinerary.route.kakao.KakaoDrivingRouteProvider;
import com.planmate.itinerary.service.PlaceDisplayResolver;
import com.planmate.proposal.api.event.ItineraryProposalChangedEvent;
import com.planmate.proposal.dto.CreateItineraryProposalRequest;
import com.planmate.proposal.dto.ItineraryProposalResponse;
import com.planmate.proposal.entity.ItineraryProposalEntity;
import com.planmate.proposal.entity.ItineraryProposalStatus;
import com.planmate.proposal.exception.ProposalErrorCode;
import com.planmate.proposal.exception.ProposalException;
import com.planmate.proposal.repository.ItineraryProposalRepository;
import com.planmate.trip.api.TripAccessChecker;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class ItineraryProposalService {

    private static final List<ItineraryProposalStatus> ACTIVE_STATUSES = List.of(
            ItineraryProposalStatus.READY,
            ItineraryProposalStatus.VOTE_OPEN
    );

    private final TripAccessChecker tripAccessChecker;
    private final ItineraryRepository itineraryRepository;
    private final ItineraryProposalRepository proposalRepository;
    private final PlaceDisplayResolver placeDisplayResolver;
    private final KakaoDrivingRouteProvider routeProvider;
    private final Clock clock;
    private final ApplicationEventPublisher eventPublisher;

    public ItineraryProposalService(
            TripAccessChecker tripAccessChecker,
            ItineraryRepository itineraryRepository,
            ItineraryProposalRepository proposalRepository,
            PlaceDisplayResolver placeDisplayResolver,
            KakaoDrivingRouteProvider routeProvider,
            Clock clock,
            ApplicationEventPublisher eventPublisher
    ) {
        this.tripAccessChecker = tripAccessChecker;
        this.itineraryRepository = itineraryRepository;
        this.proposalRepository = proposalRepository;
        this.placeDisplayResolver = placeDisplayResolver;
        this.routeProvider = routeProvider;
        this.clock = clock;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public ItineraryProposalResponse create(
            Long userId,
            Long tripId,
            CreateItineraryProposalRequest request
    ) {
        tripAccessChecker.checkAccessible(userId, tripId);
        ItineraryEntity current = itineraryRepository.findCurrentByTripId(tripId)
                .orElseThrow(() -> new ProposalException(ProposalErrorCode.INVALID_PROPOSAL));
        requireMatchingBase(current, request.baseItineraryId(), request.baseItineraryVersion());

        ItineraryDayEntity day = current.getDays().stream()
                .filter(candidate -> candidate.getDay() == request.dayNumber())
                .findFirst()
                .orElseThrow(() -> new ProposalException(ProposalErrorCode.INVALID_PROPOSAL));
        List<ItineraryItemEntity> items = day.getItems().stream()
                .sorted(Comparator.comparingInt(ItineraryItemEntity::getSequence))
                .toList();
        int targetIndex = indexOf(items, request.targetItemId());
        ItineraryItemEntity target = items.get(targetIndex);

        String replacementPlaceId = request.replacementPlaceId().trim();
        boolean timeChanged = !target.getStartTime().equals(request.replacementStartTime())
                || target.getDurationMinutes() != request.replacementDurationMinutes();
        if (target.getPlaceId().equals(replacementPlaceId) && !timeChanged) {
            throw new ProposalException(ProposalErrorCode.INVALID_PROPOSAL);
        }
        if (items.stream().anyMatch(item -> !item.getId().equals(target.getId())
                && item.getPlaceId().equals(replacementPlaceId))) {
            throw new ProposalException(ProposalErrorCode.INVALID_PROPOSAL);
        }

        ItineraryItemEntity previous = targetIndex > 0 ? items.get(targetIndex - 1) : null;
        ItineraryItemEntity next = targetIndex + 1 < items.size() ? items.get(targetIndex + 1) : null;
        Map<String, ItineraryPlaceDisplayView> displays = resolveDisplays(previous, replacementPlaceId, next);
        ItineraryPlaceDisplayView replacement = displays.get(replacementPlaceId);
        if (!hasLocation(replacement)) {
            throw new ProposalException(ProposalErrorCode.PROPOSAL_PLACE_UNRESOLVED);
        }
        verifyRoute(previous, replacement, displays);
        verifyRoute(replacement, next, displays);

        String fingerprint = fingerprint(
                current.getId(), request.dayNumber(), target.getId(), replacementPlaceId,
                request.replacementStartTime().toString(), request.replacementDurationMinutes()
        );
        return proposalRepository
                .findByTripIdAndCanonicalFingerprintAndStatusIn(tripId, fingerprint, ACTIVE_STATUSES)
                .map(ItineraryProposalResponse::from)
                .orElseGet(() -> {
                    Instant now = Instant.now(clock);
                    ItineraryProposalEntity saved = proposalRepository.save(ItineraryProposalEntity.replaceItem(
                            tripId,
                            current.getId(),
                            current.getVersion(),
                            userId,
                            request.dayNumber(),
                            target.getId(),
                            replacementPlaceId,
                            replacement.displayName(),
                            request.replacementStartTime(),
                            request.replacementDurationMinutes(),
                            fingerprint,
                            now
                    ));
                    eventPublisher.publishEvent(new ItineraryProposalChangedEvent(
                            tripId, saved.getId(), saved.getStatus().name()
                    ));
                    return ItineraryProposalResponse.from(saved);
                });
    }

    @Transactional(readOnly = true)
    public List<ItineraryProposalResponse> list(Long userId, Long tripId) {
        tripAccessChecker.checkAccessible(userId, tripId);
        return proposalRepository.findByTripIdOrderByCreatedAtDescIdDesc(tripId).stream()
                .map(ItineraryProposalResponse::from)
                .toList();
    }

    private void requireMatchingBase(ItineraryEntity current, Long baseId, int baseVersion) {
        if (!current.getId().equals(baseId) || current.getVersion() != baseVersion) {
            throw new ProposalException(ProposalErrorCode.STALE_BASE_VERSION);
        }
    }

    private int indexOf(List<ItineraryItemEntity> items, Long targetItemId) {
        for (int index = 0; index < items.size(); index++) {
            if (items.get(index).getId().equals(targetItemId)) return index;
        }
        throw new ProposalException(ProposalErrorCode.STALE_BASE_VERSION);
    }

    private Map<String, ItineraryPlaceDisplayView> resolveDisplays(
            ItineraryItemEntity previous,
            String replacementPlaceId,
            ItineraryItemEntity next
    ) {
        List<String> placeIds = new ArrayList<>();
        if (previous != null) placeIds.add(previous.getPlaceId());
        placeIds.add(replacementPlaceId);
        if (next != null) placeIds.add(next.getPlaceId());
        return placeDisplayResolver.resolveListViews(placeIds);
    }

    private void verifyRoute(
            ItineraryItemEntity originItem,
            ItineraryPlaceDisplayView destination,
            Map<String, ItineraryPlaceDisplayView> displays
    ) {
        if (originItem == null) return;
        ItineraryPlaceDisplayView origin = displays.get(originItem.getPlaceId());
        verifyRoute(origin, destination);
    }

    private void verifyRoute(
            ItineraryPlaceDisplayView origin,
            ItineraryItemEntity destinationItem,
            Map<String, ItineraryPlaceDisplayView> displays
    ) {
        if (destinationItem == null) return;
        ItineraryPlaceDisplayView destination = displays.get(destinationItem.getPlaceId());
        verifyRoute(origin, destination);
    }

    private void verifyRoute(ItineraryPlaceDisplayView origin, ItineraryPlaceDisplayView destination) {
        if (!hasLocation(origin) || !hasLocation(destination)) {
            throw new ProposalException(ProposalErrorCode.PROPOSAL_PLACE_UNRESOLVED);
        }
        boolean found = routeProvider.findDetailedRoute(
                new RoutePoint(origin.location().latitude(), origin.location().longitude()),
                new RoutePoint(destination.location().latitude(), destination.location().longitude())
        ).isPresent();
        if (!found) {
            throw new ProposalException(ProposalErrorCode.PROPOSAL_ROUTE_NOT_FOUND);
        }
    }

    private boolean hasLocation(ItineraryPlaceDisplayView display) {
        return display != null && display.resolved() && display.location() != null;
    }

    private String fingerprint(Object... values) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            String joined = java.util.Arrays.stream(values).map(String::valueOf)
                    .reduce((left, right) -> left + "|" + right).orElse("");
            return HexFormat.of().formatHex(digest.digest(joined.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 must be available", exception);
        }
    }
}
