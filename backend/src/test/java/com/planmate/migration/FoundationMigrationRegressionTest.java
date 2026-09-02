package com.planmate.migration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.planmate.itinerary.entity.ItineraryEntity;
import com.planmate.itinerary.repository.ItineraryGenerationRepository;
import com.planmate.itinerary.repository.ItineraryRepository;
import com.planmate.trip.entity.MembershipStatus;
import com.planmate.trip.entity.TripEntity;
import com.planmate.trip.entity.TripMemberEntity;
import com.planmate.trip.entity.TripMemberRole;
import com.planmate.trip.repository.TripMemberRepository;
import com.planmate.trip.repository.TripRepository;
import java.util.List;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

/**
 * WP-A §9: V22~V25 migration/backfill이 기존 baseline(trip 1530, generation 1415,
 * itinerary 505, member 2588/2623/2624)을 보존하는지 확인한다. 이 데이터는 로컬 개발
 * Postgres(`infra/compose.local.yaml`)에 실제로 존재하는 dev fixture다 — 이 fixture가 없는
 * 환경(예: 빈 CI DB)에서는 조용히 skip한다.
 */
@SpringBootTest
class FoundationMigrationRegressionTest {

    private static final Long TRIP_ID = 1530L;
    private static final Long GENERATION_ID = 1415L;
    private static final Long ITINERARY_ID = 505L;
    private static final Map<Long, TripMemberRole> EXPECTED_MEMBERS = Map.of(
            2588L, TripMemberRole.OWNER,
            2623L, TripMemberRole.MEMBER,
            2624L, TripMemberRole.MEMBER
    );

    @Autowired
    private TripRepository tripRepository;

    @Autowired
    private TripMemberRepository tripMemberRepository;

    @Autowired
    private ItineraryRepository itineraryRepository;

    @Autowired
    private ItineraryGenerationRepository itineraryGenerationRepository;

    @Test
    void tripBaselineSurvivesForeignFoundationMigration() {
        TripEntity trip = tripRepository.findById(TRIP_ID).orElse(null);
        assumeTrue(trip != null, "trip 1530 dev fixture is not present in this database; skipping");

        assertThat(trip.getDestination()).isEqualTo("거제시");
        assertThat(itineraryGenerationRepository.findById(GENERATION_ID)).isPresent();
        assertThat(itineraryRepository.findById(ITINERARY_ID)).isPresent();

        // ADR-0002: 원본 505는 보존되어야 한다. 다만 이 로컬 fixture로 revision E2E를 수행한 뒤에는
        // current pointer가 505를 조상으로 둔 최신 revision을 가리키는 것이 정상이다.
        ItineraryEntity current = itineraryRepository.findCurrentByTripId(TRIP_ID).orElseThrow();
        assertThat(current.getId()).isEqualTo(trip.getCurrentItineraryId());
        assertThat(current.getVersion()).isGreaterThanOrEqualTo(1);
        assertThat(hasAncestorOrSelf(current, ITINERARY_ID)).isTrue();

        // ADR-0005: 기존 국내 trip은 Asia/Seoul로 백필되어야 한다.
        assertThat(trip.getTimezone()).isEqualTo("Asia/Seoul");

        // ADR-0001: 기존 세 멤버는 모두 ACTIVE interval로 백필되어야 한다.
        List<TripMemberEntity> members = tripMemberRepository.findByTrip_IdAndStatusOrderByCreatedAtAsc(
                TRIP_ID, MembershipStatus.ACTIVE
        );
        assertThat(members).hasSize(EXPECTED_MEMBERS.size());
        for (TripMemberEntity member : members) {
            assertThat(member.isActive()).isTrue();
            assertThat(member.getRole()).isEqualTo(EXPECTED_MEMBERS.get(member.getUser().getId()));
        }
    }

    @Test
    @Transactional
    void itineraryDayAndItemCountsAreUnchanged() {
        ItineraryEntity itinerary = itineraryRepository.findById(ITINERARY_ID).orElse(null);
        assumeTrue(itinerary != null, "itinerary 505 dev fixture is not present in this database; skipping");

        assertThat(itinerary.getDays()).hasSize(4);
        assertThat(itinerary.getDays().stream().mapToInt(day -> day.getItems().size()).sum()).isEqualTo(23);
        assertThat(itinerary.getDays())
                .extracting(day -> day.getItems().size())
                .containsExactlyInAnyOrder(5, 5, 7, 6);
    }

    private boolean hasAncestorOrSelf(ItineraryEntity itinerary, Long expectedAncestorId) {
        Set<Long> visited = new HashSet<>();
        ItineraryEntity cursor = itinerary;
        while (cursor != null && cursor.getId() != null && visited.add(cursor.getId())) {
            if (cursor.getId().equals(expectedAncestorId)) {
                return true;
            }
            Long parentId = cursor.getBaseItineraryId();
            cursor = parentId == null ? null : itineraryRepository.findById(parentId).orElse(null);
        }
        return false;
    }
}
