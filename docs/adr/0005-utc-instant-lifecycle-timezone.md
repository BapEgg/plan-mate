# ADR-0005: UTC instant and trip lifecycle timezone

- 상태: 채택
- 결정일: 2026-08-31
- 관련 spec: `docs/collaboration-workspace-spec.md` §4.3, §9.6, §10.1 (C4)
- 소비 package: WP-D (chat cutoff), WP-E (vote deadline), WP-F (TRANSIT)

## Context

`itinerary_items`는 `LocalTime`만 저장하고, `trips`는 timezone 정보가 없다. spec §4.3은
"서버·DB의 deadline과 권한 판단 기준은 UTC instant, 화면은 장소의 IANA timezone 기준 현지
시각"을 요구한다. 지금 chat/vote/edit-lock이 아직 없으므로 강제할 대상은 없지만, WP-D/E가
공통 계산 방식을 다시 설계하지 않도록 timezone 값과 계산 계약을 지금 고정해야 한다(spec
§15 게이트: "timezone/lifecycle ADR"이 WP-A 결정 항목).

## Decision

`trips`에 `timezone VARCHAR(64) NOT NULL DEFAULT 'Asia/Seoul'`(IANA zone id)를 추가한다.
기존 모든 trip은 `Asia/Seoul`로 백필한다 — 실제 데이터(거제 가족여행 등 현재 유일한 실사용
trip)가 전부 국내 여행이므로 추측이 아니라 사실에 맞는 기본값이다. 향후 해외 목적지 trip
생성 시 목적지 정보(Google Places)에서 timezone을 조회해 채우는 것은 WP-C/F 범위다.

pure 함수 `TripLifecycleClock`을 추가한다:

```java
TripLifecycleState resolve(Instant nowUtc, String ianaZoneId, LocalDate startDate, LocalDate endDate)
```

반환값은 `UPCOMING` | `ONGOING` | `COMPLETED`이며, 경계는 목적지 timezone 기준 자정이다
(예: 여행 종료일 당일 23:59:59 KST까지는 `ONGOING`). 이 함수가 향후 다음 계산의 유일한
근거가 된다:

- chat: 종료일 당일까지 쓰기 가능, 다음 날부터 읽기 전용(spec §4.3)
- itinerary edit lock: 시작하지 않은 현재/미래 item만 변경 가능
- vote deadline: 24시간 마감을 서버 시각(UTC) 기준으로 계산

device timezone/clock은 이 계산에 절대 입력되지 않는다 — 함수 시그니처 자체가 `nowUtc`
(서버 `Instant.now()`)만 받는다.

`itinerary_items.start_time`은 `LocalTime`으로 유지한다. TRANSIT/timezone 기반 잠금이
실제로 구현되는 spec §9의 migration 순서 6번 단계 전까지는 이 컬럼을 건드릴 소비자가 없다.

## Rejected alternatives

**지금 `itinerary_items`에 UTC instant 컬럼을 미리 추가.** 소비자가 없는 상태에서 컬럼을
추가하면 TRANSIT/해외 목적지 요구사항이 구체화될 때(spec §9 6단계) 스키마를 다시 바꿔야 할
가능성이 높다. 추측성 선반영은 지양한다.

**trip 전체가 아니라 장소별 timezone을 지금 저장.** spec §4.3은 "장거리 TRANSIT는 출발·도착
장소, timezone과 UTC instant를 한 item에 가진다"고 명시하지만, 이는 TRANSIT 저장 모델(spec
§9 6단계)의 일부다. WP-A 시점에는 trip 단위 단일 timezone으로 충분하고(국내 일정만
존재), 장소별 timezone은 TRANSIT 구현 시점에 함께 도입한다.

## Backfill

`V24__add_trip_timezone.sql`이 모든 기존 trip을 `Asia/Seoul`로 백필한다(컬럼 자체
`DEFAULT 'Asia/Seoul'`이므로 신규 행은 자동, 기존 행은 명시적 `UPDATE`로 동일하게 채운다).

## Rollback

```sql
ALTER TABLE trips DROP COLUMN timezone;
```

## Tests

- `TripLifecycleClock` unit test: 경계 시각(자정 직전/직후, KST 기준) 및 device timezone이
  결과에 영향을 주지 않음을 증명
- 마이그레이션 회귀 테스트: trip `1530`의 `timezone`이 `Asia/Seoul`로 백필되었는지 확인
