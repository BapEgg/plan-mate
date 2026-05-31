# PlanMate Local Infrastructure

Docker Compose 기반 로컬 인프라 구성이다.

## Services

- PostgreSQL 17: `localhost:5432`
- Redis 8: `localhost:6379`
- Nginx: `localhost:8088`

Nginx는 로컬에서 실행 중인 애플리케이션으로 프록시한다.

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

`backend/.env`는 `backend/.env.example`을 기준으로 생성한다.

## Commands

저장소 루트에서 실행한다.

```bash
docker compose --env-file backend/.env -f backend/compose.yaml config
docker compose --env-file backend/.env -f backend/compose.yaml up -d
docker compose --env-file backend/.env -f backend/compose.yaml ps
docker compose --env-file backend/.env -f backend/compose.yaml down
```

프론트엔드 개발 서버는 별도로 실행한다.

```bash
cd frontend
npm install
npm run dev
```

백엔드는 별도로 실행한다.

```bash
cd backend
./gradlew bootRun
```

Windows PowerShell에서는 Gradle wrapper와 npm 명령을 다음처럼 실행할 수 있다.

```powershell
.\gradlew.bat bootRun
npm.cmd run dev
```
