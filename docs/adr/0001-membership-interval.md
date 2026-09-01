# ADR-0001: Membership interval and single ACTIVE OWNER

- 상태: 채택
- 결정일: 2026-08-31
- 관련 spec: `docs/collaboration-workspace-spec.md` §4.1, §9.1, §10.1 (C1)
- 소비 package: WP-B (초대/내보내기/나가기/방장 이전), WP-D (visibility/unread/presence), C3 (session revocation)

## Context

`trip_members(trip_id, user_id)`는 영구적인 멤버십 한 건만 표현한다. `V6__create_trip_tables.sql`이
`trip_members_trip_user_unique UNIQUE (trip_id, user_id)`를 걸어두어 같은 사용자가 같은
trip에 두 번째 행을 가질 수 없다 — 나가거나 내보내진 뒤 다시 합류하는 것을 별도
구간(interval)으로 표현할 방법이 없다는 뜻이다. "현재 활성 OWNER가 정확히 한 명"이라는
불변식도 DB가 보장하지 않는다(애플리케이션 코드가 trip 생성 시 OWNER 한 명만 만들 뿐).

## Decision

`trip_members`를 새 table로 분리하지 않고 **같은 table을 확장**한다.

추가 컬럼:

- `status` (`ACTIVE` | `LEFT` | `REMOVED`), 기본값 `ACTIVE`
- `joined_at` (기존 `created_at`과 동일 의미로 백필)
- `left_at` (nullable)
- `left_reason` (nullable, `LEFT` | `REMOVED`) — 방장 이전은 interval을 종료하지 않고
  같은 행의 `role`만 바꾸므로 별도 사유값이 필요 없다

나가기/내보내기는 기존 행을 `UPDATE`해 `status`/`left_at`/`left_reason`을 채우고 종료한다.
재가입은 **새 행을 INSERT**한다 — 즉 한 사용자의 여러 membership interval이 `trip_members`에
여러 행으로 append-only하게 쌓인다. 과거 interval 행은 이후 절대 다시 열리지 않는다.

먼저 `trip_members_trip_user_unique`(영구 unique 제약)를 제거한다 — 그렇지 않으면 재가입
시 두 번째 행을 INSERT할 수 없다. 대신 DB 레벨 불변식은 partial unique index 두 개로
강제한다:

```sql
CREATE UNIQUE INDEX trip_members_active_user_unique
    ON trip_members (trip_id, user_id)
    WHERE status = 'ACTIVE';

CREATE UNIQUE INDEX trip_members_active_owner_unique
    ON trip_members (trip_id)
    WHERE status = 'ACTIVE' AND role = 'OWNER';
```

첫 번째 index는 "사용자당 ACTIVE interval은 한 번에 하나"를, 두 번째는 "여행방당 ACTIVE OWNER는
한 명"을 DB가 직접 보장하게 한다. 방장 이전(WP-B)은 같은 transaction 안에서 기존 OWNER 행을
`role=MEMBER`로, 대상 MEMBER 행을 `role=OWNER`로 업데이트해야 하며, 두 번째 index가 원자적으로
전이 도중의 "OWNER 두 명" 상태를 거부한다.

## Rejected alternatives

**별도 `trip_member_intervals` table.** 더 정규화된 모델이지만 현재
`TripDetailTrip.Member`, `TripMembershipQueryService`, `TripAccessChecker` 구현체
(`TripPlanningSnapshotQueryService`), `TripService`(`TripDetailTripReader` 구현체) 등
모든 소비 지점을 join 기반으로 다시 작성해야 한다. WP-B가 병렬로 이 표를 확장해야 하는데,
정규화로 얻는 이득이 in-place 확장으로 이미 표현 가능한 것에 비해 크지 않다고 판단했다.

**즉시 삭제 후 재삽입 없는 단일 행 재사용.** 재가입 시 과거 행의 `status`를 다시 `ACTIVE`로
되돌리는 방식은 "재가입 사용자가 과거 interval 권한을 자동 회복하면 안 된다"(spec §4.1)를
깨뜨릴 위험이 있다 — 과거 `joined_at`이 새 interval의 시작처럼 오인될 수 있다. 새 행 INSERT가
더 안전하다.

## Backfill

`V22__add_trip_member_lifecycle.sql`은 기존 모든 행을 `status='ACTIVE'`,
`joined_at=created_at`으로 백필한다. 데이터 손실이나 행 삭제가 없는 순수 additive 변경이다.

## Rollback

컬럼/인덱스 추가만 있는 expand 단계이므로 롤백은 안전하다:

```sql
DROP INDEX IF EXISTS trip_members_active_owner_unique;
DROP INDEX IF EXISTS trip_members_active_user_unique;
ALTER TABLE trip_members DROP COLUMN left_reason;
ALTER TABLE trip_members DROP COLUMN left_at;
ALTER TABLE trip_members DROP COLUMN joined_at;
ALTER TABLE trip_members DROP COLUMN status;
```

이 rollback은 `trip_members_trip_user_unique`를 복원하지 않는다 — 재가입으로 이미 중복
`(trip_id, user_id)` 행이 쌓였다면 그 unique 제약을 그대로 되살릴 수 없다. 되돌려야 한다면
먼저 과거 interval 행을 삭제/병합하는 별도 작업이 필요하다(WP-A 시점에는 재가입 기능이
아직 없으므로 이 문제는 실제로 발생하지 않는다).

## Concurrency

두 partial unique index가 동시성 안전장치다. 동시에 같은 사용자를 두 번 초대 수락하거나
동시에 두 명을 OWNER로 승격하려는 시도는 두 번째 커밋에서 `unique violation`으로 실패한다.
애플리케이션은 이 예외를 `409`로 매핑해야 한다(WP-B에서 구현).

## Tests

- `TripMembershipQueryService`가 `REMOVED`/`LEFT` 행에 대해 `isMember=false`를 반환하는 unit test
- 마이그레이션 회귀 테스트: 실제 trip `1530`의 세 멤버(2588 OWNER, 2623/2624 MEMBER)가
  모두 `status=ACTIVE`로 백필되었는지 확인
- (WP-B에서 추가) 동시 재가입/동시 OWNER 승격이 partial unique index로 거부되는 통합 테스트
