# 검증 제외 Run — 자동 탐지 조건 수정 전 기록

이 Run은 실제 Debezium 보호 동작과 10건 재전달 자체는 발생했지만, 자동화가 시작 로그의 `offset mismatch strategy` 설명을 실제 오류로 너무 일찍 인식해 불일치 탐지 시간을 `0.620초`로 잘못 기록했다.

- 상태: **SUPERSEDED — 포트폴리오 사용 금지**
- 제외 사유: 실제 오류가 아닌 설명 로그를 타이머 종료 조건으로 사용
- 실제 오류 로그: 이후 `Last recorded offset is no longer available on the server`가 발생
- 수정 내용: 실제 WARN/ERROR 문장만 탐지하도록 정규식을 제한
- 공식 결과: `../cdc-offset-mismatch-replay-20260828-220606/README.md`

이 디렉터리는 검증 과정과 자동화 개선 이력을 남기기 위해 삭제하지 않는다. `result.json`의 기존 `PASS`와 `mismatchDetectionSeconds=0.620`은 공식 결과로 인용하지 않는다.
