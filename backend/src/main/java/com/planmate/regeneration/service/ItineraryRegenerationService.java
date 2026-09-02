package com.planmate.regeneration.service;

import com.planmate.itinerary.api.ItineraryGenerationStatus;
import com.planmate.itinerary.api.RegenerationConstraintProvider;
import com.planmate.itinerary.api.RegenerationResponseHandler;
import com.planmate.itinerary.api.event.ItineraryGenerationStatusChangedEvent;
import com.planmate.itinerary.api.validation.AiItineraryValidationReport;
import com.planmate.itinerary.domain.GenerationCandidateSnapshot;
import com.planmate.itinerary.domain.GenerationInputSnapshot;
import com.planmate.itinerary.dto.AiItineraryDraft;
import com.planmate.itinerary.dto.ItineraryDraftDay;
import com.planmate.itinerary.dto.ItineraryDraftItem;
import com.planmate.itinerary.entity.ItineraryDayEntity;
import com.planmate.itinerary.entity.ItineraryEntity;
import com.planmate.itinerary.entity.ItineraryGenerationEntity;
import com.planmate.itinerary.entity.ItineraryItemCreatedSource;
import com.planmate.itinerary.entity.ItineraryItemEntity;
import com.planmate.itinerary.exception.AiItineraryValidationException;
import com.planmate.itinerary.exception.ItineraryErrorCode;
import com.planmate.itinerary.exception.ItineraryException;
import com.planmate.itinerary.repository.ItineraryDayRepository;
import com.planmate.itinerary.repository.ItineraryGenerationRepository;
import com.planmate.itinerary.repository.ItineraryItemRepository;
import com.planmate.itinerary.repository.ItineraryRepository;
import com.planmate.itinerary.service.AiItineraryDraftValidationService;
import com.planmate.itinerary.service.GenerationCandidateSnapshotStore;
import com.planmate.itinerary.service.GenerationInputSnapshotStore;
import com.planmate.itinerary.service.ItineraryGenerationPersistenceService;
import com.planmate.itinerary.service.ItineraryPromptService;
import com.planmate.regeneration.api.event.ItineraryRegenerationChangedEvent;
import com.planmate.regeneration.dto.CreateItineraryRegenerationRequest;
import com.planmate.regeneration.dto.ItineraryRegenerationResponse;
import com.planmate.regeneration.entity.ItineraryRegenerationEntity;
import com.planmate.regeneration.entity.ItineraryRegenerationStatus;
import com.planmate.regeneration.entity.RegenerationScopeType;
import com.planmate.regeneration.exception.RegenerationErrorCode;
import com.planmate.regeneration.exception.RegenerationException;
import com.planmate.regeneration.repository.ItineraryRegenerationRepository;
import com.planmate.revision.api.event.ItineraryRevisionAppliedEvent;
import com.planmate.trip.api.TripAccessChecker;
import com.planmate.trip.api.TripRoleChecker;
import com.planmate.trip.domain.TripLifecycleClock;
import com.planmate.trip.entity.TripEntity;
import com.planmate.trip.repository.TripRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class ItineraryRegenerationService implements RegenerationResponseHandler, RegenerationConstraintProvider {

    private static final List<ItineraryRegenerationStatus> ACTIVE_STATUSES = List.of(
            ItineraryRegenerationStatus.GENERATING,
            ItineraryRegenerationStatus.READY_FOR_REVIEW
    );

    private final TripAccessChecker tripAccessChecker;
    private final TripRoleChecker tripRoleChecker;
    private final TripRepository tripRepository;
    private final ItineraryRepository itineraryRepository;
    private final ItineraryDayRepository dayRepository;
    private final ItineraryItemRepository itemRepository;
    private final ItineraryGenerationRepository generationRepository;
    private final ItineraryRegenerationRepository regenerationRepository;
    private final ItineraryGenerationPersistenceService generationPersistenceService;
    private final GenerationInputSnapshotStore inputSnapshotStore;
    private final GenerationCandidateSnapshotStore candidateSnapshotStore;
    private final AiItineraryDraftValidationService validationService;
    private final Clock clock;
    private final ApplicationEventPublisher eventPublisher;

    public ItineraryRegenerationService(
            TripAccessChecker tripAccessChecker,
            TripRoleChecker tripRoleChecker,
            TripRepository tripRepository,
            ItineraryRepository itineraryRepository,
            ItineraryDayRepository dayRepository,
            ItineraryItemRepository itemRepository,
            ItineraryGenerationRepository generationRepository,
            ItineraryRegenerationRepository regenerationRepository,
            ItineraryGenerationPersistenceService generationPersistenceService,
            GenerationInputSnapshotStore inputSnapshotStore,
            GenerationCandidateSnapshotStore candidateSnapshotStore,
            AiItineraryDraftValidationService validationService,
            Clock clock,
            ApplicationEventPublisher eventPublisher
    ) {
        this.tripAccessChecker = tripAccessChecker;
        this.tripRoleChecker = tripRoleChecker;
        this.tripRepository = tripRepository;
        this.itineraryRepository = itineraryRepository;
        this.dayRepository = dayRepository;
        this.itemRepository = itemRepository;
        this.generationRepository = generationRepository;
        this.regenerationRepository = regenerationRepository;
        this.generationPersistenceService = generationPersistenceService;
        this.inputSnapshotStore = inputSnapshotStore;
        this.candidateSnapshotStore = candidateSnapshotStore;
        this.validationService = validationService;
        this.clock = clock;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public ItineraryRegenerationResponse create(
            Long ownerId,
            Long tripId,
            CreateItineraryRegenerationRequest request
    ) {
        tripRoleChecker.requireOwner(ownerId, tripId);
        TripEntity trip = tripRepository.findByIdForUpdate(tripId)
                .orElseThrow(() -> new RegenerationException(RegenerationErrorCode.REGENERATION_NOT_FOUND));
        if (regenerationRepository.existsByTripIdAndStatusIn(tripId, ACTIVE_STATUSES)) {
            throw new RegenerationException(RegenerationErrorCode.REGENERATION_ALREADY_ACTIVE);
        }
        ItineraryEntity base = itineraryRepository.findCurrentByTripId(tripId)
                .orElseThrow(() -> new RegenerationException(RegenerationErrorCode.REGENERATION_INVALID_RANGE));
        requireMatchingBase(base, request.baseItineraryId(), request.expectedItineraryVersion());
        enforceTripWindow(trip);

        NormalizedScope scope = normalizeScope(base, request.scope());
        enforceItemWindow(trip, scope.items());
        ItineraryGenerationEntity generation = generationPersistenceService.createGenerationRequest(
                ownerId, tripId, ItineraryPromptService.CURRENT_PROMPT_VERSION
        );
        Instant now = Instant.now(clock);
        ItineraryRegenerationEntity saved = regenerationRepository.save(ItineraryRegenerationEntity.create(
                tripId,
                generation.getId(),
                base.getId(),
                base.getVersion(),
                ownerId,
                scope.type(),
                scope.dayNumber(),
                scope.startItemId(),
                scope.endItemId(),
                scope.fixedItemIds(),
                normalizeText(request.additionalRequest()),
                now
        ));
        publish(saved);
        return toResponse(saved, base);
    }

    @Transactional(readOnly = true)
    public Optional<ItineraryRegenerationResponse> latest(Long userId, Long tripId) {
        tripAccessChecker.checkAccessible(userId, tripId);
        return regenerationRepository.findFirstByTripIdOrderByCreatedAtDescIdDesc(tripId)
                .map(regeneration -> toResponse(regeneration, loadBase(regeneration)));
    }

    @Transactional(readOnly = true)
    public ItineraryRegenerationResponse get(Long userId, Long tripId, Long regenerationId) {
        tripAccessChecker.checkAccessible(userId, tripId);
        ItineraryRegenerationEntity regeneration = regenerationRepository.findById(regenerationId)
                .filter(candidate -> candidate.getTripId().equals(tripId))
                .orElseThrow(() -> new RegenerationException(RegenerationErrorCode.REGENERATION_NOT_FOUND));
        return toResponse(regeneration, loadBase(regeneration));
    }

    @Transactional
    public ItineraryRegenerationResponse reject(Long ownerId, Long tripId, Long regenerationId) {
        tripRoleChecker.requireOwner(ownerId, tripId);
        ItineraryRegenerationEntity regeneration = findForUpdate(tripId, regenerationId);
        if (regeneration.getStatus() == ItineraryRegenerationStatus.REJECTED) {
            return toResponse(regeneration, loadBase(regeneration));
        }
        if (regeneration.getStatus() != ItineraryRegenerationStatus.READY_FOR_REVIEW) {
            throw new RegenerationException(RegenerationErrorCode.REGENERATION_NOT_READY);
        }
        regeneration.markRejected(Instant.now(clock));
        publish(regeneration);
        return toResponse(regeneration, loadBase(regeneration));
    }

    @Transactional
    public ItineraryRegenerationResponse apply(Long ownerId, Long tripId, Long regenerationId) {
        tripRoleChecker.requireOwner(ownerId, tripId);
        TripEntity trip = tripRepository.findByIdForUpdate(tripId)
                .orElseThrow(() -> new RegenerationException(RegenerationErrorCode.REGENERATION_NOT_FOUND));
        ItineraryRegenerationEntity regeneration = findForUpdate(tripId, regenerationId);
        if (regeneration.getStatus() == ItineraryRegenerationStatus.APPLIED
                && regeneration.getAppliedItineraryId() != null) {
            return toResponse(regeneration, loadBase(regeneration));
        }
        if (regeneration.getStatus() != ItineraryRegenerationStatus.READY_FOR_REVIEW
                || regeneration.getDraftPayload() == null) {
            throw new RegenerationException(RegenerationErrorCode.REGENERATION_NOT_READY);
        }
        ItineraryEntity base = loadBase(regeneration);
        requireCurrentBase(trip, regeneration, base);
        enforceTripWindow(trip);

        int nextVersion = itineraryRepository.findMaxVersionByTripId(tripId) + 1;
        ItineraryGenerationEntity generation = generationRepository.findById(regeneration.getGenerationId())
                .orElseThrow(() -> new RegenerationException(RegenerationErrorCode.REGENERATION_NOT_FOUND));
        Instant now = Instant.now(clock);
        ItineraryEntity revision = itineraryRepository.saveAndFlush(ItineraryEntity.createRegenerationRevision(
                generation,
                now,
                nextVersion,
                base.getId(),
                regeneration.getScope() == RegenerationScopeType.FULL ? "AI_FULL_REGENERATION" : "AI_PARTIAL_REGENERATION",
                ownerId
        ));
        persistDraft(revision, base, regeneration.getDraftPayload());
        itemRepository.flush();
        if (tripRepository.updateCurrentItineraryIdIfMatches(tripId, base.getId(), revision.getId()) != 1) {
            throw new RegenerationException(RegenerationErrorCode.REGENERATION_STALE_BASE);
        }
        regeneration.markApplied(revision.getId(), now);
        eventPublisher.publishEvent(new ItineraryRevisionAppliedEvent(
                tripId, revision.getId(), revision.getVersion(), null
        ));
        publish(regeneration);
        return toResponse(regeneration, base);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean handles(Long generationId) {
        return regenerationRepository.findByGenerationId(generationId).isPresent();
    }

    @Override
    @Transactional
    public void submit(Long tripId, Long generationId, AiItineraryDraft draft) {
        ItineraryRegenerationEntity regeneration = regenerationRepository.findByGenerationIdForUpdate(generationId)
                .filter(candidate -> candidate.getTripId().equals(tripId))
                .orElseThrow(() -> new RegenerationException(RegenerationErrorCode.REGENERATION_NOT_FOUND));
        if (regeneration.getStatus() == ItineraryRegenerationStatus.READY_FOR_REVIEW) {
            if (regeneration.getDraftPayload().equals(draft)) return;
            throw new ItineraryException(ItineraryErrorCode.GENERATION_ALREADY_COMPLETED_WITH_DIFFERENT_DRAFT);
        }
        if (regeneration.getStatus() != ItineraryRegenerationStatus.GENERATING) {
            throw new RegenerationException(RegenerationErrorCode.REGENERATION_NOT_READY);
        }
        ItineraryGenerationEntity generation = generationRepository.findWithLockById(generationId)
                .orElseThrow(() -> new ItineraryException(ItineraryErrorCode.GENERATION_NOT_FOUND));
        if (generation.getStatus() != ItineraryGenerationStatus.READY_FOR_PLANNING) {
            throw new ItineraryException(ItineraryErrorCode.GENERATION_NOT_READY);
        }
        GenerationInputSnapshot snapshot = inputSnapshotStore.getRequired(generationId);
        List<GenerationCandidateSnapshot> candidates = candidateSnapshotStore.findAllByGenerationId(generationId);
        validateDraft(generation, snapshot, candidates, draft);

        ItineraryEntity base = loadBase(regeneration);
        if (regeneration.getScope() == RegenerationScopeType.PARTIAL) {
            verifyFixedItems(base, regeneration, draft);
        }
        AiItineraryDraft reviewDraft = regeneration.getScope() == RegenerationScopeType.FULL
                ? canonicalDraft(generationId, draft)
                : mergePartial(generationId, base, regeneration, draft);
        verifyFixedItems(base, regeneration, reviewDraft);

        Instant now = Instant.now(clock);
        ItineraryGenerationStatus previous = generation.getStatus();
        regeneration.markReady(reviewDraft, now);
        generation.markCompleted(now);
        eventPublisher.publishEvent(new ItineraryGenerationStatusChangedEvent(
                tripId, generationId, previous, generation.getStatus(), candidates.size(), null, now
        ));
        publish(regeneration);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Constraint> findByGenerationId(Long generationId) {
        return regenerationRepository.findByGenerationId(generationId).map(regeneration -> {
            ItineraryEntity base = loadBase(regeneration);
            Set<Long> fixedIds = Set.copyOf(regeneration.getFixedItemIds());
            Set<Long> selectedIds = selectedItemIds(base, regeneration);
            List<RegenerationConstraintProvider.Item> currentItems = base.getDays().stream()
                    .sorted(Comparator.comparingInt(ItineraryDayEntity::getDay))
                    .flatMap(day -> day.getItems().stream()
                            .sorted(Comparator.comparingInt(ItineraryItemEntity::getSequence))
                            .map(item -> new RegenerationConstraintProvider.Item(
                                    item.getId(), day.getDay(), item.getSequence(), item.getPlaceId(),
                                    item.getStartTime().toString(), item.getDurationMinutes(),
                                    fixedIds.contains(item.getId()) || !selectedIds.contains(item.getId()) ? "KEEP" : "REPLACE"
                            )))
                    .toList();
            return new Constraint(
                    regeneration.getScope().name(), regeneration.getDayNumber(), regeneration.getStartItemId(),
                    regeneration.getEndItemId(), regeneration.getFixedItemIds(), regeneration.getAdditionalRequest(), currentItems
            );
        });
    }

    @Transactional
    public void markGenerationFailed(Long generationId, String reason) {
        regenerationRepository.findByGenerationIdForUpdate(generationId).ifPresent(regeneration -> {
            regeneration.markFailed(reason, Instant.now(clock));
            publish(regeneration);
        });
    }

    private void validateDraft(
            ItineraryGenerationEntity generation,
            GenerationInputSnapshot snapshot,
            List<GenerationCandidateSnapshot> candidates,
            AiItineraryDraft draft
    ) {
        AiItineraryValidationReport report = validationService.validate(
                generation.getId(), generation.getPromptVersion(), snapshot, candidates, draft
        );
        if (report.hasErrors()) throw new AiItineraryValidationException(report);
    }

    private AiItineraryDraft mergePartial(
            Long generationId,
            ItineraryEntity base,
            ItineraryRegenerationEntity regeneration,
            AiItineraryDraft generated
    ) {
        Map<Integer, ItineraryDraftDay> generatedDays = new HashMap<>();
        for (ItineraryDraftDay day : generated.days()) generatedDays.put(day.day(), day);
        ItineraryDayEntity targetDay = base.getDays().stream()
                .filter(day -> day.getDay() == regeneration.getDayNumber())
                .findFirst()
                .orElseThrow(() -> new RegenerationException(RegenerationErrorCode.REGENERATION_STALE_BASE));
        List<ItineraryItemEntity> targetItems = sortedItems(targetDay);
        int startIndex = indexOf(targetItems, regeneration.getStartItemId());
        int endIndex = indexOf(targetItems, regeneration.getEndItemId());
        Set<Long> fixed = Set.copyOf(regeneration.getFixedItemIds());

        List<ItineraryDraftDay> mergedDays = new ArrayList<>();
        for (ItineraryDayEntity baseDay : base.getDays().stream()
                .sorted(Comparator.comparingInt(ItineraryDayEntity::getDay)).toList()) {
            List<ItineraryDraftItem> mergedItems = new ArrayList<>();
            Map<Integer, ItineraryDraftItem> generatedBySequence = new HashMap<>();
            ItineraryDraftDay generatedDay = generatedDays.get(baseDay.getDay());
            if (generatedDay != null) {
                for (ItineraryDraftItem item : generatedDay.items()) generatedBySequence.put(item.sequence(), item);
            }
            List<ItineraryItemEntity> baseItems = sortedItems(baseDay);
            for (int index = 0; index < baseItems.size(); index++) {
                ItineraryItemEntity baseItem = baseItems.get(index);
                boolean inSelectedRange = baseDay.getDay() == regeneration.getDayNumber()
                        && index >= startIndex && index <= endIndex;
                ItineraryDraftItem proposed = inSelectedRange && !fixed.contains(baseItem.getId())
                        ? generatedBySequence.get(baseItem.getSequence())
                        : null;
                if (inSelectedRange && !fixed.contains(baseItem.getId()) && proposed == null) {
                    throw new RegenerationException(RegenerationErrorCode.REGENERATION_INVALID_RANGE);
                }
                mergedItems.add(proposed == null ? toDraftItem(baseItem) : proposed);
            }
            mergedDays.add(new ItineraryDraftDay(baseDay.getDay(), List.copyOf(mergedItems)));
        }
        return new AiItineraryDraft(generationId.toString(), List.copyOf(mergedDays));
    }

    static void verifyFixedItems(
            ItineraryEntity base,
            ItineraryRegenerationEntity regeneration,
            AiItineraryDraft draft
    ) {
        if (regeneration.getFixedItemIds().isEmpty()) return;
        Map<Integer, Map<Integer, ItineraryDraftItem>> proposed = new HashMap<>();
        for (ItineraryDraftDay day : draft.days()) {
            Map<Integer, ItineraryDraftItem> bySequence = new HashMap<>();
            for (ItineraryDraftItem item : day.items()) bySequence.put(item.sequence(), item);
            proposed.put(day.day(), bySequence);
        }
        for (ItineraryDayEntity day : base.getDays()) {
            for (ItineraryItemEntity item : day.getItems()) {
                if (!regeneration.getFixedItemIds().contains(item.getId())) continue;
                ItineraryDraftItem candidate = proposed.getOrDefault(day.getDay(), Map.of()).get(item.getSequence());
                if (candidate == null
                        || !item.getPlaceId().equals(candidate.placeId())
                        || item.getDurationMinutes() != candidate.durationMinutes()
                        || Math.abs(Duration.between(item.getStartTime(), LocalTime.parse(candidate.startTime())).toMinutes()) > 30) {
                    throw new RegenerationException(RegenerationErrorCode.REGENERATION_FIXED_ITEM_CONFLICT);
                }
            }
        }
    }

    private AiItineraryDraft canonicalDraft(Long generationId, AiItineraryDraft draft) {
        List<ItineraryDraftDay> days = draft.days().stream()
                .sorted(Comparator.comparingInt(ItineraryDraftDay::day))
                .map(day -> new ItineraryDraftDay(day.day(), day.items().stream()
                        .sorted(Comparator.comparingInt(ItineraryDraftItem::sequence)).toList()))
                .toList();
        return new AiItineraryDraft(generationId.toString(), days);
    }

    private void persistDraft(ItineraryEntity revision, ItineraryEntity base, AiItineraryDraft draft) {
        Map<Integer, ItineraryDayEntity> baseDays = new HashMap<>();
        for (ItineraryDayEntity day : base.getDays()) baseDays.put(day.getDay(), day);
        for (ItineraryDraftDay draftDay : draft.days().stream()
                .sorted(Comparator.comparingInt(ItineraryDraftDay::day)).toList()) {
            ItineraryDayEntity baseDay = baseDays.get(draftDay.day());
            if (baseDay == null) throw new RegenerationException(RegenerationErrorCode.REGENERATION_STALE_BASE);
            ItineraryDayEntity savedDay = dayRepository.save(ItineraryDayEntity.create(
                    revision, draftDay.day(), baseDay.getDate()
            ));
            for (ItineraryDraftItem draftItem : draftDay.items().stream()
                    .sorted(Comparator.comparingInt(ItineraryDraftItem::sequence)).toList()) {
                itemRepository.save(ItineraryItemEntity.create(
                        savedDay,
                        draftItem.sequence(),
                        draftItem.placeId(),
                        LocalTime.parse(draftItem.startTime()),
                        draftItem.durationMinutes(),
                        ItineraryItemCreatedSource.AI_REPLACEMENT
                ));
            }
        }
    }

    private ItineraryRegenerationResponse toResponse(ItineraryRegenerationEntity regeneration, ItineraryEntity base) {
        Map<String, String> names = new HashMap<>();
        for (GenerationCandidateSnapshot candidate : candidateSnapshotStore.findAllByGenerationId(regeneration.getGenerationId())) {
            names.put(candidate.placeId(), candidate.displayName());
        }
        Map<Integer, Map<Integer, ItineraryItemEntity>> baseItems = new LinkedHashMap<>();
        Map<Integer, java.time.LocalDate> dates = new HashMap<>();
        for (ItineraryDayEntity day : base.getDays()) {
            dates.put(day.getDay(), day.getDate());
            Map<Integer, ItineraryItemEntity> bySequence = new HashMap<>();
            for (ItineraryItemEntity item : day.getItems()) bySequence.put(item.getSequence(), item);
            baseItems.put(day.getDay(), bySequence);
        }
        Set<Long> fixedIds = Set.copyOf(regeneration.getFixedItemIds());
        List<ItineraryRegenerationResponse.DayComparison> comparisons = regeneration.getDraftPayload() == null
                ? List.of()
                : regeneration.getDraftPayload().days().stream()
                        .sorted(Comparator.comparingInt(ItineraryDraftDay::day))
                        .map(day -> new ItineraryRegenerationResponse.DayComparison(
                                day.day(), dates.get(day.day()), day.items().stream()
                                        .sorted(Comparator.comparingInt(ItineraryDraftItem::sequence))
                                        .map(proposed -> comparison(
                                                baseItems.getOrDefault(day.day(), Map.of()).get(proposed.sequence()),
                                                proposed, names, fixedIds
                                        ))
                                        .toList()
                        ))
                        .toList();
        return new ItineraryRegenerationResponse(
                regeneration.getId(),
                regeneration.getTripId().toString(),
                regeneration.getGenerationId().toString(),
                regeneration.getBaseItineraryId(),
                regeneration.getBaseItineraryVersion(),
                regeneration.getScope(),
                regeneration.getDayNumber(),
                regeneration.getStartItemId(),
                regeneration.getEndItemId(),
                regeneration.getFixedItemIds(),
                regeneration.getStatus(),
                regeneration.getFailureReason(),
                regeneration.getAppliedItineraryId(),
                comparisons,
                regeneration.getCreatedAt(),
                regeneration.getUpdatedAt()
        );
    }

    private ItineraryRegenerationResponse.ItemComparison comparison(
            ItineraryItemEntity original,
            ItineraryDraftItem proposed,
            Map<String, String> names,
            Set<Long> fixedIds
    ) {
        boolean changed = original == null
                || !original.getPlaceId().equals(proposed.placeId())
                || !original.getStartTime().equals(LocalTime.parse(proposed.startTime()))
                || original.getDurationMinutes() != proposed.durationMinutes();
        return new ItineraryRegenerationResponse.ItemComparison(
                proposed.sequence(),
                original == null ? null : original.getId(),
                original == null ? null : original.getPlaceId(),
                original == null ? null : names.get(original.getPlaceId()),
                original == null ? null : original.getStartTime(),
                original == null ? null : original.getDurationMinutes(),
                proposed.placeId(),
                names.get(proposed.placeId()),
                LocalTime.parse(proposed.startTime()),
                proposed.durationMinutes(),
                original != null && fixedIds.contains(original.getId()),
                changed
        );
    }

    private NormalizedScope normalizeScope(ItineraryEntity base, CreateItineraryRegenerationRequest.Scope scope) {
        if (scope.type() == RegenerationScopeType.FULL) {
            if (scope.dayNumber() != null || scope.startItemId() != null || scope.endItemId() != null
                    || !scope.fixedItemIds().isEmpty()) {
                throw new RegenerationException(RegenerationErrorCode.REGENERATION_INVALID_RANGE);
            }
            List<ItineraryItemEntity> allItems = base.getDays().stream().flatMap(day -> day.getItems().stream()).toList();
            return new NormalizedScope(RegenerationScopeType.FULL, null, null, null, List.of(), allItems);
        }
        if (scope.dayNumber() == null || scope.startItemId() == null || scope.endItemId() == null) {
            throw new RegenerationException(RegenerationErrorCode.REGENERATION_INVALID_RANGE);
        }
        ItineraryDayEntity day = base.getDays().stream()
                .filter(candidate -> candidate.getDay() == scope.dayNumber())
                .findFirst()
                .orElseThrow(() -> new RegenerationException(RegenerationErrorCode.REGENERATION_INVALID_RANGE));
        List<ItineraryItemEntity> items = sortedItems(day);
        int first = indexOf(items, scope.startItemId());
        int last = indexOf(items, scope.endItemId());
        int start = Math.min(first, last);
        int end = Math.max(first, last);
        List<ItineraryItemEntity> range = List.copyOf(items.subList(start, end + 1));
        Set<Long> rangeIds = range.stream().map(ItineraryItemEntity::getId).collect(java.util.stream.Collectors.toSet());
        Set<Long> fixedIds = new HashSet<>(scope.fixedItemIds());
        if (fixedIds.size() != scope.fixedItemIds().size() || !rangeIds.containsAll(fixedIds)) {
            throw new RegenerationException(RegenerationErrorCode.REGENERATION_INVALID_RANGE);
        }
        if (fixedIds.size() == range.size()) {
            throw new RegenerationException(RegenerationErrorCode.REGENERATION_NO_REPLACEMENT);
        }
        return new NormalizedScope(
                RegenerationScopeType.PARTIAL,
                scope.dayNumber(),
                items.get(start).getId(),
                items.get(end).getId(),
                List.copyOf(fixedIds),
                range
        );
    }

    private void enforceTripWindow(TripEntity trip) {
        if (TripLifecycleClock.resolve(Instant.now(clock), trip.getTimezone(), trip.getStartDate(), trip.getEndDate())
                == TripLifecycleClock.TripLifecycleState.COMPLETED) {
            throw new RegenerationException(RegenerationErrorCode.REGENERATION_WINDOW_CLOSED);
        }
    }

    private void enforceItemWindow(TripEntity trip, List<ItineraryItemEntity> items) {
        Instant now = Instant.now(clock);
        for (ItineraryItemEntity item : items) {
            Instant startsAt = ZonedDateTime.of(
                    item.getDay().getDate(), item.getStartTime(), ZoneId.of(trip.getTimezone())
            ).toInstant();
            if (!startsAt.isAfter(now)) {
                throw new RegenerationException(RegenerationErrorCode.REGENERATION_WINDOW_CLOSED);
            }
        }
    }

    private void requireMatchingBase(ItineraryEntity base, Long requestedId, int requestedVersion) {
        if (!base.getId().equals(requestedId) || base.getVersion() != requestedVersion) {
            throw new RegenerationException(RegenerationErrorCode.REGENERATION_STALE_BASE);
        }
    }

    private void requireCurrentBase(TripEntity trip, ItineraryRegenerationEntity regeneration, ItineraryEntity base) {
        if (!base.getId().equals(trip.getCurrentItineraryId())
                || base.getVersion() != regeneration.getBaseItineraryVersion()) {
            regeneration.markStale(Instant.now(clock));
            throw new RegenerationException(RegenerationErrorCode.REGENERATION_STALE_BASE);
        }
    }

    private ItineraryRegenerationEntity findForUpdate(Long tripId, Long regenerationId) {
        return regenerationRepository.findByIdAndTripIdForUpdate(regenerationId, tripId)
                .orElseThrow(() -> new RegenerationException(RegenerationErrorCode.REGENERATION_NOT_FOUND));
    }

    private ItineraryEntity loadBase(ItineraryRegenerationEntity regeneration) {
        return itineraryRepository.findById(regeneration.getBaseItineraryId())
                .orElseThrow(() -> new RegenerationException(RegenerationErrorCode.REGENERATION_STALE_BASE));
    }

    private List<ItineraryItemEntity> sortedItems(ItineraryDayEntity day) {
        return day.getItems().stream().sorted(Comparator.comparingInt(ItineraryItemEntity::getSequence)).toList();
    }

    private Set<Long> selectedItemIds(ItineraryEntity base, ItineraryRegenerationEntity regeneration) {
        if (regeneration.getScope() == RegenerationScopeType.FULL) {
            return base.getDays().stream()
                    .flatMap(day -> day.getItems().stream())
                    .map(ItineraryItemEntity::getId)
                    .collect(java.util.stream.Collectors.toSet());
        }
        ItineraryDayEntity day = base.getDays().stream()
                .filter(candidate -> candidate.getDay() == regeneration.getDayNumber())
                .findFirst()
                .orElseThrow(() -> new RegenerationException(RegenerationErrorCode.REGENERATION_STALE_BASE));
        List<ItineraryItemEntity> items = sortedItems(day);
        int start = indexOf(items, regeneration.getStartItemId());
        int end = indexOf(items, regeneration.getEndItemId());
        return items.subList(Math.min(start, end), Math.max(start, end) + 1).stream()
                .map(ItineraryItemEntity::getId)
                .collect(java.util.stream.Collectors.toSet());
    }

    private int indexOf(List<ItineraryItemEntity> items, Long itemId) {
        for (int index = 0; index < items.size(); index++) {
            if (items.get(index).getId().equals(itemId)) return index;
        }
        throw new RegenerationException(RegenerationErrorCode.REGENERATION_INVALID_RANGE);
    }

    private ItineraryDraftItem toDraftItem(ItineraryItemEntity item) {
        return new ItineraryDraftItem(
                item.getSequence(), item.getPlaceId(), item.getStartTime().toString(), item.getDurationMinutes()
        );
    }

    private String normalizeText(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private void publish(ItineraryRegenerationEntity regeneration) {
        eventPublisher.publishEvent(new ItineraryRegenerationChangedEvent(
                regeneration.getTripId(), regeneration.getId(), regeneration.getGenerationId(),
                regeneration.getStatus().name(), regeneration.getAppliedItineraryId()
        ));
    }

    private record NormalizedScope(
            RegenerationScopeType type,
            Integer dayNumber,
            Long startItemId,
            Long endItemId,
            List<Long> fixedItemIds,
            List<ItineraryItemEntity> items
    ) {
    }
}
