# PlanMate Local Infrastructure

Docker Compose 기반 로컬 인프라 구성입니다.

Compose 파일은 `infra/compose.local.yaml`에서 관리합니다. 백엔드와
프론트엔드는 각 프로젝트에서 직접 실행하고, PostgreSQL, Redis, RabbitMQ,
Nginx, Debezium Server를 Compose로 실행합니다.

## Services

- PostgreSQL 17: `localhost:5432`
- Redis 8: `localhost:6379`
- RabbitMQ: `localhost:5672`
- RabbitMQ Management UI: `localhost:15672`
- Nginx: `localhost:8088`
- Debezium Server: CDC profile에서 실행

Nginx는 로컬에서 실행 중인 애플리케이션으로 프록시합니다.

```text
/          -> http://host.docker.internal:5173
/api/      -> http://host.docker.internal:8080
/actuator/ -> http://host.docker.internal:8080
/ws/       -> http://host.docker.internal:8080
```

## Requirements

- Docker Desktop
- Docker Compose
- `backend/.env`

`backend/.env`는 `backend/.env.example`을 기준으로 생성합니다.

## Commands

저장소 루트에서 실행합니다.

```bash
docker compose --env-file backend/.env -f infra/compose.local.yaml config
docker compose --env-file backend/.env -f infra/compose.local.yaml up -d
docker compose --env-file backend/.env -f infra/compose.local.yaml ps
docker compose --env-file backend/.env -f infra/compose.local.yaml down
```

`infra` 디렉터리에서 실행할 때는 compose 파일 경로를 짧게 씁니다.

```powershell
docker compose -f compose.local.yaml up -d
docker compose -f compose.local.yaml ps
```

RabbitMQ만 실행하려면 다음 명령을 사용합니다.

```powershell
docker compose -f compose.local.yaml up -d rabbitmq
```

RabbitMQ Management UI 기본 로컬 접속 정보:

```text
http://localhost:15672
planmate / planmate
```

프론트엔드 개발 서버는 별도로 실행합니다.

```bash
cd frontend
npm install
npm run dev
```

백엔드는 별도로 실행합니다.

```bash
cd backend
./gradlew bootRun
```

Windows PowerShell에서는 Gradle wrapper와 npm 명령을 다음처럼 실행할 수
있습니다.

```powershell
.\gradlew.bat bootRun
npm.cmd run dev
```

## CDC Outbox

Debezium Server는 `cdc` profile에 묶여 있습니다. 백엔드가 Flyway migration을
먼저 실행해 `public.outbox_events` 테이블을 만든 뒤 Debezium을 시작해야 하기
때문입니다.

권장 로컬 실행 순서:

```powershell
docker compose -f compose.local.yaml up -d postgres redis rabbitmq
```

백엔드는 다음 설정을 켜고 실행합니다.

```text
APP_ITINERARY_GENERATION_WORKER_ENABLED=true
RABBITMQ_HEALTH_ENABLED=true
```

백엔드가 뜨면서 RabbitMQ exchange/queue를 선언하고 Flyway migration을 완료한
뒤 Debezium을 실행합니다.

```powershell
docker compose -f compose.local.yaml --profile cdc up -d debezium
```

기대 흐름:

```text
POST /api/trips/{tripId}/itinerary-generations
-> itinerary_generations insert
-> outbox_events insert
-> Debezium reads public.outbox_events
-> RabbitMQ exchange planmate.itinerary
-> routing key itinerary.generation.requested
-> worker consumes planmate.itinerary.generation.requested
-> generation READY_FOR_PLANNING
```

기존 Postgres 컨테이너가 logical replication 설정 이전에 만들어졌다면 다시
생성합니다.

```powershell
docker compose -f compose.local.yaml down
docker compose -f compose.local.yaml up -d postgres redis rabbitmq
```

## Monitoring

4차 모니터링 구성은 `monitoring` profile로 분리되어 있습니다. Prometheus는 로컬에서
실행 중인 백엔드의 `/actuator/prometheus`와 compose 네트워크의 RabbitMQ, Debezium
metrics endpoint를 수집합니다.

백엔드를 먼저 실행합니다.

```powershell
cd backend
.\gradlew.bat bootRun
```

`infra` 디렉터리에서 Prometheus와 Grafana를 실행합니다.

```powershell
docker compose -f compose.local.yaml --profile monitoring up -d prometheus grafana
```

CDC 흐름까지 같이 확인하려면 `cdc` profile을 함께 켭니다.

```powershell
docker compose -f compose.local.yaml --profile cdc --profile monitoring up -d
```

로컬 접속 정보:

```text
Prometheus: http://localhost:9090
Grafana: http://localhost:3000
Grafana login: planmate / planmate
RabbitMQ metrics: http://localhost:15692/metrics
Debezium health: http://localhost:8083/q/health
Debezium metrics: http://localhost:9404/metrics
Backend metrics: http://localhost:8080/actuator/prometheus
```

Grafana는 `PlanMate / PlanMate Itinerary Monitoring` 대시보드를 자동으로 로드합니다.
Debezium을 `cdc` profile로 실행하지 않은 경우 Prometheus의 `debezium` target이 down으로
보이는 것은 정상입니다. 백엔드를 실행하지 않은 경우에도 `planmate-backend` target은 down으로
표시됩니다.
