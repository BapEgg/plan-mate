# ADR-0004: Outbox dispatcher and broker delivery topology

- 상태: 채택 (범위 한정 — multi-instance fan-out은 명시적으로 후속)
- 결정일: 2026-08-31
- 관련 spec: `docs/collaboration-workspace-spec.md` §4.4, §10.1 (C3), §10.5, §15
- 소비 package: WP-B/D가 두 번째 realtime event type을 추가할 때 reconciliation을 활성화

## Context

`infra/compose.local.yaml`과 `infra/debezium/application.properties`를 확인한 결과, 이
프로젝트는 이미 `outbox_events` table에 대해 Postgres 논리 복제(`wal_level=logical`)를
켜두었고 Debezium Server가 이를 RabbitMQ로 relay하고 있다. 다만 현재 라우팅은 **정적**이다:
모든 outbox 행이 고정 exchange `planmate.itinerary`, routing key
`itinerary.generation.requested`로만 나가며, 이는 일정 생성 worker를 깨우는 **명령 경로**다.

realtime STOMP fan-out(`ItineraryGenerationRealtimeSubscriber` 등)은 이 파이프라인과
무관하게, 같은 트랜잭션의 `AFTER_COMMIT` 리스너가 `SimpMessagingTemplate`으로 직접
broadcast하는 별도 경로다. 이 경로는 같은 프로세스에만 유효하고, 프로세스가 커밋과
AFTER_COMMIT 사이에 죽으면 event가 유실된다. dispatcher/retry/checkpoint가 없다.

## Decision

두 경로를 명확히 분리하고 각각의 책임을 문서화한다.

**명령 경로(기존, 변경 없음).** outbox → Debezium → RabbitMQ →
`itinerary.generation.requested` → generation worker. WP-A는 이 설정을 건드리지 않는다.

**realtime fan-out 경로(오늘 상태 유지 + 복구 가능성만 추가).** 같은 인스턴스 배포에서는
`AFTER_COMMIT` + `SimpMessagingTemplate` 직접 publish를 계속 사용한다. crash 복구를 위해
`outbox_events`에 `dispatched_at TIMESTAMPTZ NULL`, `dispatch_attempts INT NOT NULL DEFAULT 0`를
추가한다. `AFTER_COMMIT` 리스너가 성공적으로 publish하면 같은 트랜잭션 밖에서
`dispatched_at`을 채운다(별도의 짧은 트랜잭션 — realtime publish 자체는 DB 트랜잭션이 아니므로
publish 성공 여부와 무관하게 outbox row는 이미 커밋되어 있다).

`OutboxReconciliationScheduler`를 `OutboxRetentionScheduler`와 같은 패턴으로 추가하되
**기본 비활성화**한다(`app.outbox.reconciliation.enabled=false`). 이 스케줄러가 켜지면
`dispatched_at IS NULL AND created_at < now() - lease`인 행을 찾아 재publish를 시도한다.
WP-A는 이 스케줄러를 만들고 unit test로 로직을 검증하지만, 기본값을 켠 상태로 배포하지
않는다 — 현재 realtime event가 하나뿐이고 REST snapshot이 authoritative이므로(spec §4.4),
재publish 없이도 클라이언트가 재연결 시 REST로 gap을 복구할 수 있어 지금 당장 필요하지
않기 때문이다.

**multi-instance fan-out은 명시적으로 후속(deferred)으로 남긴다.** instance가 여러 대가
되면 한 instance의 AFTER_COMMIT publish가 다른 instance에 연결된 client에게 도달하지
못한다. 이 문제는 Debezium이 이미 relay하고 있는 outbox 흐름을 realtime에도 재사용해(예:
`route.by.field=event_type`로 event type별 라우팅을 만들고, 각 instance가 RabbitMQ에서
자신의 exchange를 구독해 로컬 `SimpMessagingTemplate`으로 재발행) 해결할 수 있지만, WP-A는
이를 구현하지 않는다. spec §15 게이트가 "다중 instance 전달 보장 주장·복구 불가능 event
payload"를 결정 전 금지 항목으로 명시하므로, 실제로 두 번째 instance가 필요해지는 시점에
별도 ADR로 확정한다.

## Rejected alternatives

**지금 바로 전체 broker relay 구현.** 현재 단일 인스턴스 배포(`infra/compose.local.yaml`에
backend 인스턴스가 하나뿐)에서 필요하지 않은 복잡도를 추가하는 것이며, spec §10.6 "범위
팽창 방지"에 위배된다.

**Debezium 라우팅을 지금 event_type별로 일반화.** 인프라 설정(`debezium/application.properties`)
변경은 이미 동작 중인 명령 경로(itinerary generation worker)에 영향을 줄 위험이 있다. 이
변경이 실제로 필요해지는 시점(두 번째 realtime event type이 생기는 WP-B/D)에 별도로 검증하며
진행한다.

## Backfill

`V25__add_outbox_dispatch_tracking.sql`은 두 컬럼만 추가한다. 기존 행은
`dispatch_attempts=0`, `dispatched_at=NULL`로 남는다 — 과거 event를 소급 재생시키지
않는다(retention 정책이 이미 오래된 행을 정리하므로 안전).

## Rollback

```sql
ALTER TABLE outbox_events DROP COLUMN dispatch_attempts;
ALTER TABLE outbox_events DROP COLUMN dispatched_at;
```

## Tests

- `OutboxReconciliationScheduler`가 기본적으로 비활성 상태임을 확인하는 설정 test
- `dispatched_at`/`dispatch_attempts` 갱신 repository 메서드 unit test
- 기존 `ItineraryGenerationRealtimeSubscriber` 회귀(변경 없음, 기존 테스트 유지)
