# PlanMate 포트폴리오용 장애 테스트 이미지 목록

포트폴리오 저장소로 전달할 이미지는 실험별 폴더로 분리한다. Grafana 내부 스크롤이나 잘린 범례가 있는 화면은 사용하지 않는다.

## 장애 테스트 01 — Debezium 중단 후 재시작

1. `01-장애범위-Debezium만중단.png`
   - API와 RabbitMQ는 정상이고 Debezium만 정상→중단→정상으로 바뀐 증거
2. `02-복구과정-요청10건.png`
   - 전달 대기 10건이 감소하고 후보 수집 완료가 10건으로 증가한 증거

## 장애 테스트 02 — ACK 전 Worker 종료

1. `01-장애범위-Worker-DOWN-UP.png`
   - API·RabbitMQ는 정상, Worker만 DOWN→UP이며 전체 실험 시각이 모두 보임
2. `02-Queue복구-Ready10에서0.png`
   - 재큐잉 Ready 최대 10건과 최종 0건, 범례 전체 표시
3. `03-기대값-실제값-PASS.png`
   - Deliver 11, ACK 10, 재전달 1, SKIP 1, READY 10/10 판정표와 계산식
4. `04-재전달1-SKIP1.png`
   - 복구 Worker가 받은 원본 9건·재전달 1건·SKIP 1건 Metric
5. `05-왜PASS인가-복구원리.png`
   - ACK 전 종료부터 재큐잉·재전달·멱등 처리까지 5단계 설명

### 권장 본문 배치

본문에는 `01 장애 범위 → 02 Queue 복구 → 03 판정표` 순으로 사용한다. 재전달 세부 설명이 필요할 때 `04 Metric → 05 복구 원리`를 이어 붙인다.

## 장애 테스트 03 — CDC Offset 불일치와 과거 이벤트 재전달

1. `01-장애범위-실험CDC-DOWN-UP.png`
   - 운영 API·RabbitMQ·운영 Debezium은 정상이고, 실험 CDC Detector만 정상→중단→정상으로 바뀐 증거
2. `02-Slot-Offset-불일치-탐지복구.png`
   - Debezium의 과거 Offset `0/19409D0`과 PostgreSQL의 최신 slot `0/1940B90` 불일치, 차단, 복원 과정
3. `03-기대값-실제값-PASS.png`
   - Offset 탐지·복구, 재전달 10건, SKIP 10건, READY 10/10, 유실·중복·DLQ 증가 0 판정표
4. `04-과거이벤트10-SKIP10.png`
   - 같은 Generation 10건의 RabbitMQ publish·deliver·ACK 10/10/10과 Candidate 추가 저장 0
5. `05-왜PASS인가-복구원리.png`
   - 조용한 유실 차단, 검증된 Offset 복원, 멱등 SKIP, 교차 검증 설명

### 권장 본문 배치

본문에는 `01 장애 범위 → 02 Offset/slot 복구 → 03 판정표`를 우선 사용한다. 재전달 멱등성을 기술적으로 설명할 때 `04 SKIP 결과 → 05 복구 원리`를 추가한다.

실험 3 이미지는 모두 1280×720이며, ①~④ 제목 크기를 동일하게 유지했다. 짧은 상태 구간에 글자를 강제로 표시하지 않고 시간 범위를 조정해 `정상/중단/정상` 글자가 잘리지 않도록 검수했다.

## 장애 테스트 04 — Retry 분류와 DLQ 격리

1. `01-장애범위-인프라정상-Worker분리실행.png`
   - API·RabbitMQ·Debezium은 정상 유지, 실험 Worker만 4-A와 4-B 두 구간으로 분리 실행
2. `02-Retryable-시도30-Retry20.png`
   - Retryable 10건의 실패 시도 30회와 실제 Retry 20회 Metric
3. `03-Retryable-기대값-실제값.png`
   - Retryable 요청·시도·Retry·FAILED·실패 코드·DLQ 기대값과 실제값
4. `04-NonRetryable-시도10-Retry0.png`
   - Non-Retryable 10건의 실패 시도 10회와 실제 Retry 0회 Metric
5. `05-NonRetryable-기대값-실제값.png`
   - Non-Retryable 요청·시도·Retry·FAILED·실패 코드·DLQ 기대값과 실제값
6. `06-DLQ-0에서10에서20.png`
   - 전용 DLQ가 4-A 종료 때 10건, 4-B 종료 때 20건으로 증가하고 Main Queue가 0건으로 감소한 시계열
7. `07-최종판정-증거교차검증.png`
   - 로그·Metric·DB·RabbitMQ의 수치 일치와 운영 DLQ 167→167 판정표

### 권장 본문 배치

본문에는 `01 장애 범위 → 02 Retryable → 04 Non-Retryable → 06 DLQ → 07 최종 판정` 순서를 권장한다. 숫자 계산을 자세히 설명할 때 03과 05를 각 그래프 뒤에 추가한다.

실험 4 이미지도 모두 1280×720이고 ①~⑤ 제목 크기가 동일하다. `중단/정상`, 긴 실패 코드, 기대값·실제값 표, 범례가 잘리지 않는지 직접 검수했다.

## 장애 테스트 05 — Stale Generation 복구와 Fencing

1. `01-장애범위-A정지-B복구.png`
   - API·RabbitMQ·Debezium은 정상이고 Worker A가 진행을 멈춘 뒤 Worker B가 복구를 맡은 실험 범위
2. `02-WorkerA-Claim1-Unacked10-Lease만료10.png`
   - Worker A의 초기 Claim Version 1 10건, Queue Unacked 10건, Lease 만료 10건
3. `03-WorkerA-기대값-실제값.png`
   - Worker A가 만들어야 할 Stale 조건의 기대값과 실제값 비교
4. `04-WorkerB-Recovery10-Claim2-성공10.png`
   - Scheduler 재발행 10건, Worker B의 Claim Version 2 획득과 성공 처리 10건
5. `05-WorkerB-기대값-실제값.png`
   - Recovery Publish·새 Claim·후보 수집 완료의 기대값과 실제값 비교
6. `06-Fencing10-SKIP10.png`
   - 늦게 돌아온 Worker A의 오래된 결과 10건을 모두 차단하고 SKIP한 증거
7. `07-최종판정-복구10-중복0.png`
   - READY 10/10, Candidate 1,200행, 중복 0행, 전용 DLQ 0건의 최종 교차검증

### 권장 본문 배치

본문에는 `01 장애 범위 → 02 Worker A Stale → 04 Worker B 복구 → 06 Fencing → 07 최종 판정` 순서를 권장한다. 기대값과 실제값을 자세히 보여줄 때 03과 05를 해당 그래프 뒤에 추가한다.

실험 5 이미지도 모두 1280×720이며 ①~⑤ 제목 크기를 동일하게 유지했다. 모든 상태명, 긴 범례, 기대값·실제값 표가 잘리지 않는지 직접 검수했다.

## 장애 테스트 06 — RabbitMQ 중단 중 CDC 발행 실패와 복구

1. `00-실험개요-장애범위-관측타임라인.png`
   - 검증 질문, 한 문장 결론, RabbitMQ 장애 범위와 실제 시각을 한 화면에 배치한 첫 화면
2. `01-장애범위-RabbitMQ만중단.png`
   - API는 계속 정상이고 RabbitMQ만 정상→중단→정상으로 바뀐 증거
3. `01-관측타임라인-중단-복구-PASS.png`
   - 중단, Outbox 10건 저장, 재기동, READY 10/10, 최종 PASS의 실제 KST 시각
4. `02-요청상태-전달대기10-완료10.png`
   - 전달 대기 최대 10건이 0으로 감소하고 후보 수집 완료가 10건으로 증가한 증거
5. `03-중단중-기대값-실제값.png`
   - 중단 중 Outbox 10건, Debezium Sink 오류 54회, 전달 위치 정지, READY 0건 판정표
6. `04-Offset-Slot-전달위치보호.png`
   - DB WAL은 움직였지만 Debezium Offset과 Replication Slot은 중단 중 유지된 유실 방지 근거
7. `05-복구전달-발행10-전달10-ACK10.png`
   - RabbitMQ 복구 후 publish·deliver·ACK·Audit 이벤트가 모두 10건으로 일치한 그래프
8. `06-복구후-기대값-실제값.png`
   - READY 10/10, Candidate 1,200행, RabbitMQ Health 5.416초, 전체 완료 15.905초 판정표
9. `07-최종판정-완료10-유실0-중복0.png`
   - DB·Audit Queue·RabbitMQ 교차검증과 유실 0, 중복 0, DLQ 증가 0의 최종 PASS
10. `08-읽는순서-용어설명.png`
    - 처음 보는 채용담당자를 위한 ①~⑤ 질문과 Outbox·Offset/Slot·ACK 쉬운 설명

### 권장 본문 배치

본문에는 `00 실험 개요 → 01 장애 범위 → 03 중단 중 판정 → 04 전달 위치 보호 → 05 복구 전달 → 07 최종 판정` 순서를 권장한다. 자세한 시간과 용어가 필요할 때 `01 관측 타임라인`과 `08 읽는 순서`를 보조 자료로 사용한다.

실험 6은 ①~⑤를 모두 같은 패널 제목 크기로 통일했다. ③과 ⑤ 내부에 중복으로 들어가 있던 큰 번호 제목도 제거했으며, 상태명·시간축·범례·기대값·실제값·결론이 잘리지 않는지 개별 이미지를 직접 검수했다.

## 장애 테스트 07 — RabbitMQ 전달 후 DB 선점 전 Worker 종료

1. `00-실험개요-장애범위-관측타임라인.png`
   - 검증 질문, 한 문장 결론, Worker 장애 범위와 실제 사건 시각을 한 화면에 배치한 첫 화면
2. `01-장애범위-WorkerA중단-WorkerB복구.png`
   - API는 정상 유지되고 Worker만 정상→중단→정상으로 전환된 증거
3. `01-관측타임라인-전달-재큐잉-PASS.png`
   - 10건 요청, claim 전 종료, 재큐잉, Worker B 재기동, 최종 PASS의 실제 KST 시각
4. `02-종료직전-전달1-선점0.png`
   - 종료 직전 delivery 1·unacked 1이지만 DB claim·Candidate·READY는 모두 0인 장애 경계 증거
5. `03-재큐잉-Ready9에서10.png`
   - Worker A 종료 후 ACK되지 않은 1건이 반환되어 Queue ready가 최대 10건이 된 시계열
6. `03-재큐잉-기대값-실제값.png`
   - 종료 전후 Queue 9/1→10/0, 재큐잉 4.563초, redelivery 1건 판정표
7. `04-재기동-재전달1-최초선점10.png`
   - Worker B의 재전달 1, 최초 claim 10, 처리 성공 10, SKIP 0 Metric
8. `04-재기동후-기대값-실제값.png`
   - publish/deliver/ACK 10/11/10, Health 11.955초, 전체 완료 14.074초 판정표
9. `05-최종판정-완료10-유실0-중복0.png`
   - DB·RabbitMQ·Worker 로그 교차검증과 유실 0, 중복 0, DLQ 증가 0의 최종 PASS
10. `06-읽는순서-용어설명.png`
    - 채용담당자를 위한 ①~⑤ 질문과 unacked·claim·Debezium 역할의 쉬운 설명

### 권장 본문 배치

본문에는 `00 실험 개요 → 01 장애 범위 → 02 종료 직전 경계 → 03 재큐잉 → 04 재기동 처리 → 05 최종 판정` 순서를 권장한다. 상세 시각과 용어 설명이 필요할 때 `01 관측 타임라인`과 `06 읽는 순서`를 보조 자료로 사용한다.

실험 7의 전체 개요는 1280×880, 단일 패널 9장은 1280×800이다. ①~⑤ 제목 크기를 동일하게 유지했고 `정상/중단/정상`, 오른쪽 끝 시간 눈금 `00:30:50`, 긴 범례 4개, 기대값·실제값 표의 마지막 행, 최종 결론과 용어 문단이 잘리지 않는지 모두 직접 검수했다. Prometheus가 놓친 약 5초의 `unacked=1`은 그래프에 꾸미지 않고 RabbitMQ Management API 원본 스냅샷으로 보완했다고 이미지 안에 명시했다.

이미지는 모두 실제 Grafana 실험 화면이다. 정량값은 각 Run의 `result.json`과 DB·RabbitMQ 원본 증거를 기준으로 한다.
