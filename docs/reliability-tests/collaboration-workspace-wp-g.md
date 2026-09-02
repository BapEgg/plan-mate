# Collaboration Workspace WP-G 검증 기록

- 검증일: 2026-09-02
- 브랜치/기준 커밋: `codex/finish-trip-detail-demo` / `695db1a`
- 범위: WP-E 투표·적용, WP-F 전체/부분 재생성, WP-G 통합·반응형·접근성·세션 안정화
- 원칙: 자동 검증, 브라우저 확인, 실제 인프라 수동 검증을 구분하며 확인하지 않은 항목을 통과로 표시하지 않는다.

## 이번 검증에서 보완한 결함

1. 토큰 재발급은 성공했지만 재시도한 API가 다시 `401`을 반환하면 private token을 지우고 `SESSION_EXPIRED`를 한 번만 발행하도록 수정했다.
2. NARROW 화면에서 지도 pane만 선택했을 때 내부 지도가 고정 폭으로 남던 문제를 부모와 함께 `width: 100%`로 보정했다.
3. 지도에 불필요한 screen reader application mode를 강제하지 않도록 `region`으로 변경했다.
4. workspace pane switcher와 채팅/투표 tab에 Arrow, Home, End, roving focus를 적용했다.
5. 일정 DAY 선택기는 불완전한 tab 패턴 대신 `aria-pressed` toggle group으로 정리했다.
6. 일정 편집 dialog의 제목에 포커스된 상태에서 `Shift+Tab`을 누르면 dialog 밖으로 빠질 수 있던 focus trap을 수정했다.
7. 투표 마감, 동률, 멤버 제거 후 유권자 재계산, 중복 적용 방지를 service test로 고정했다.

## 자동 검증 결과

| 영역 | 결과 | 근거 |
| --- | --- | --- |
| Backend 전체 회귀 | PASS | 107 reports, 456 tests, failure/error/skipped 0 |
| Frontend unit/component | PASS | 11 files, 54 tests |
| Frontend lint | PASS | ESLint exit 0 |
| Frontend production build | PASS | Vite build exit 0 |
| WP-E 투표 적용 | PASS | 통과 후 1회 적용, 동률 유지, 멤버 제거 재계산, 참여 부족 마감 |
| WP-F fixed anchor | PASS | 고정 장소 변경 거절, 30분 이내 허용, 범위 초과 거절 |
| Session refresh 실패 | PASS | refresh 실패와 refresh 뒤 재시도 `401` 모두 token 제거 및 만료 event 검증 |
| Route provider 오류 | PASS | Google/Kakao provider·network·key·quota 오류 매핑과 기존 route 보존 경계 검증 |
| Redis 오류 매핑 | PASS | refresh token store가 Redis failure를 서비스 예외로 변환 |
| RabbitMQ 정책 | PARTIAL | DLQ routing과 listener 예외 no-requeue는 검증, 실제 broker 중단·복구는 미실행 |
| Fixture production 격리 | PASS | profile과 feature flag가 함께 켜진 경우에만 fixture component 등록 |

실행 명령:

```powershell
backend\gradlew.bat test
node node_modules\eslint\bin\eslint.js .
node node_modules\vitest\vitest.mjs run
node node_modules\vite\bin\vite.js build
```

## 브라우저 확인 결과

| 화면/상태 | 결과 | 확인 내용 |
| --- | --- | --- |
| WIDE 1440×1000 | PASS | 일정·지도·채팅 3열, header와 선택 상태 유지 |
| MEDIUM 1024×900 | PASS | 일정+지도 2열과 하단 pane switcher |
| NARROW 390×844 | PASS | 일정/지도/대화 단일 pane 전환, 지도 폭 회귀 수정 |
| 720px layout pressure | PASS | `scrollWidth === clientWidth`, 수평 page overflow 없음 |
| Dialog keyboard | PASS | 제목 autofocus 뒤 `Shift+Tab`이 마지막 dialog action으로 순환 |
| Pane/tab keyboard | PASS | Arrow/Home/End 이동과 선택 tab focus 확인 |
| Console | PARTIAL | runtime error 없음. Google legacy `Marker` deprecation warning은 후속 기술부채 |

720px 검사는 1440px 화면의 200% 확대와 유사한 레이아웃 압력 확인이며 실제 브라우저 zoom 검사는 아니다.

## 출시 전 남은 수동 gate

다음 항목은 로컬 단일 mock 화면이나 unit test만으로 실제 운영 조건을 증명할 수 없어 아직 PASS가 아니다.

1. `local1`, `local2` 두 실제 계정으로 채팅, unread, 투표, apply, 강퇴 후 열린 REST/STOMP 차단을 연속 수행한다.
2. Redis와 RabbitMQ process를 실제로 중단하고 재연결, 복구 안내, durable REST/DB snapshot 복원을 확인한다.
3. Kakao/Google provider를 실제 timeout/5xx 상태로 만들고 current 일정·marker·chat이 계속 사용 가능한지 확인한다.
4. 브라우저 실제 200% zoom, NVDA 등 실제 screen reader, 모바일 가상 키보드 환경을 확인한다.
5. 빈 DB와 기존 V21 데이터베이스 양쪽에서 V35/V36 migration 및 fixture `1530/1415/505` 보존을 별도로 확인한다.
6. WP-G 13개 통합 시나리오는 위 인프라·두 계정 검증이 끝난 뒤 최종 완료로 닫는다.

## 현재 판정

코드 단위 회귀, production build, 핵심 반응형과 keyboard 회귀는 통과했다. WP-G는 **자동 안정화 완료, 실제 인프라·두 계정 E2E 대기** 상태다. 실제 장애 주입과 사용자 시나리오를 수행하기 전에는 release 완료로 표기하지 않는다.
