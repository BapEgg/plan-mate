# ADR-0002: Immutable itinerary version and current pointer

- 상태: 채택
- 결정일: 2026-08-31
- 관련 spec: `docs/collaboration-workspace-spec.md` §4.2, §9.2, §10.1 (C2)
- 소비 package: WP-E (proposal/vote apply), WP-F (regeneration), WP-C (route snapshot key)

## Context

`itineraries`는 `generation_id`에 `UNIQUE` 제약이 있어 한 generation당 itinerary 한 건만
허용하지만, trip이 "지금 사용자에게 보여줄 하나의 current itinerary"를 명시적으로 표현하지
않는다. 현재 최신 조회는 `findFirstByTripIdOrderByCreatedAtDesc`로 `createdAt desc` 정렬에만
의존한다. `itineraries` 행 자체는 생성 뒤 절대 수정되지 않으므로 "immutable version"이라는
불변식은 이미 사실이지만, "trip이 정확히 하나의 current를 원자적으로 가리킨다"는 불변식은
없다.

## Decision

`trips`에 `current_itinerary_id BIGINT NULL REFERENCES itineraries(id)`를 추가한다. 이
컬럼이 곧 current pointer다. 백필은 기존 `createdAt desc` 선택 로직과 동일한 행을 가리키도록
채워 동작을 바꾸지 않는다.

`itineraries`에는 `version INT NOT NULL DEFAULT 1`을 추가한다. 백필은 trip별로
`created_at ASC` 순서에 rank를 매겨 채운다(현재 trip마다 itinerary가 최대 한 건뿐이므로 모든
기존 행은 `version=1`이 된다). 향후 proposal apply/재생성이 새 itinerary 행을 만들 때
`version = trip의 현재 최대 version + 1`을 같은 transaction에서 계산해 넣는다.

읽기 경로: `ItineraryQueryService.findLatestByTripId`는 이제 `trips.current_itinerary_id`를
따라간다. 포인터가 null인 방어적 상황에서만(백필로 인해 실제로는 발생하지 않아야 함) 기존
`createdAt desc` 조회로 fallback한다.

쓰기 경로(스켈레톤만, WP-A는 호출자를 만들지 않음): `TripRepository`에 조건부 갱신 메서드를
추가한다.

```sql
UPDATE trips
   SET current_itinerary_id = :newItineraryId
 WHERE id = :tripId
   AND current_itinerary_id = :expectedCurrentItineraryId;
```

영향받은 행이 0이면 base가 stale이라는 뜻이며 호출자는 `409`를 반환해야 한다(spec §4.2,
§8). WP-E가 proposal apply/투표 통과/복원에서 이 메서드를 사용한다.

## Rejected alternatives

**`itineraries.is_current BOOLEAN` 플래그.** trip당 정확히 하나의 `is_current=true` 행을
보장하려면 결국 `itineraries(trip_id) WHERE is_current` 같은 partial unique index가
필요해 복잡도가 `trips.current_itinerary_id` 방식과 같거나 더 크다. 게다가 "current를
바꾼다"는 연산이 이전 행 UPDATE + 새 행 UPDATE 두 단계가 되어, `trips`의 단일 FK를
원자적으로 swap하는 것보다 동시성 처리가 어렵다.

**별도 `itinerary_versions` wrapper table.** proposal의 `baseItineraryId`, affected range,
source 등 버전 메타데이터가 필요해지는 시점(WP-E)에 만드는 것이 맞다. 지금은 소비자가 없는
상태에서 새 table을 만들면 스키마가 실제 요구사항 없이 두 번 바뀔 위험이 크다.

## Backfill

`V23__add_trip_current_itinerary_pointer.sql`이 다음을 백필한다:

```sql
UPDATE trips t
   SET current_itinerary_id = i.id
  FROM (
      SELECT DISTINCT ON (trip_id) trip_id, id
        FROM itineraries
       ORDER BY trip_id, created_at DESC
  ) i
 WHERE t.id = i.trip_id;

UPDATE itineraries i
   SET version = ranked.version
  FROM (
      SELECT id, ROW_NUMBER() OVER (PARTITION BY trip_id ORDER BY created_at ASC) AS version
        FROM itineraries
  ) ranked
 WHERE i.id = ranked.id;
```

itinerary가 없는 trip은 `current_itinerary_id`가 `NULL`로 남는다(정상 — `EMPTY_ITINERARY`
workspace state에 대응).

## Rollback

```sql
ALTER TABLE trips DROP COLUMN current_itinerary_id;
ALTER TABLE itineraries DROP COLUMN version;
```

두 컬럼 모두 nullable/defaulted additive 컬럼이라 데이터 손실 없이 되돌릴 수 있다.

## Concurrency

조건부 `UPDATE ... WHERE current_itinerary_id = :expected`가 낙관적 동시성 제어다. 두
proposal이 동시에 같은 base를 적용하려 하면 먼저 커밋한 쪽만 행을 갱신하고, 나중 트랜잭션은
영향받은 행 0건을 관찰해 `409`로 거절한다 — spec §4.2 "투표 통과와 일정 pointer 변경은
중복 실행되지 않도록... conditional update를 사용한다"를 만족한다.

## Tests

- `ItineraryQueryService`가 `createdAt` 역순이 아닌 `current_itinerary_id`를 따라가는지
  증명하는 unit test (두 itinerary를 `createdAt` 역순과 다르게 구성해 포인터가 이긴다는 것을
  확인)
- 마이그레이션 회귀 테스트: trip `1530`의 `current_itinerary_id`가 실제로 `505`로 백필됐는지
  확인
- `TripRepository` 조건부 갱신 메서드의 contract test: 일치하는 expected 값일 때만 갱신되고,
  불일치 시 영향 행 0건을 반환하는지 확인 (WP-E가 재사용할 준비 상태 검증)
