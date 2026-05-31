# PlanMate Local Infrastructure

Docker Compose 기반 로컬 인프라 구성입니다.

Compose 파일은 `infra/compose.local.yaml`에서 관리합니다. 백엔드와
프론트엔드는 각 프로젝트에서 직접 실행하고, PostgreSQL, Redis, Nginx만
Compose로 실행합니다.

## Services

- PostgreSQL 17: `localhost:5432`
- Redis 8: `localhost:6379`
- Nginx: `localhost:8088`

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
