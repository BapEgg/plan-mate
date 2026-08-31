# ChatGPT 수동 여행 일정 생성 가이드

이 문서는 OpenAI API를 호출하지 않고 ChatGPT에서 일정 JSON을 만든 뒤, PlanMate의 기존 검증·저장 API를 통해 반영하는 개발용 절차다.

## 왜 PlanMate에서 만든 프롬프트를 그대로 사용해야 하나

일정의 `placeId`는 해당 생성 작업에서 Google Places로 수집해 DB에 저장한 후보만 사용할 수 있다. 임의의 장소 ID나 예시 ID를 넣으면 서버 검증을 통과하지 못한다. 따라서 DB에 JSON을 직접 삽입하지 말고 아래 흐름을 사용한다.

1. PlanMate에서 여행 방과 조건을 저장한다.
2. 일정 생성을 시작하고 후보 수집이 끝날 때까지 기다린다.
3. 생성 화면에서 `프롬프트 복사`로 해당 생성 작업의 실제 프롬프트를 가져온다.
4. 아래 실행 지시문 뒤에 복사한 프롬프트 전체를 붙여 ChatGPT에 보낸다.
5. ChatGPT가 반환한 JSON만 PlanMate의 수동 응답 입력란에 붙인다.
6. 먼저 `검증`을 실행하고, 오류가 없을 때만 저장한다.

상세조건 여행과 기본조건 여행은 각각 별도의 방 또는 생성 작업으로 만든다. 두 작업에서 복사되는 요청 JSON이 서로 다르므로 같은 실행 지시문을 사용해도 각각의 조건에 맞는 결과가 나온다.

## 첫 생성용 실행 지시문

아래 문장을 복사하고, 마지막 구분선 아래에 PlanMate에서 복사한 프롬프트 전체를 붙인다.

```text
아래 PlanMate 일정 생성 프롬프트를 정확히 실행해 주세요.

반환 규칙:
1. 답변은 파싱 가능한 JSON 객체 하나만 반환합니다.
2. Markdown 코드 블록, 설명, 인사말, 주석을 넣지 않습니다.
3. 입력에 있는 generationId를 한 글자도 바꾸지 않습니다.
4. 입력의 candidates에 실제로 존재하는 placeId만 사용합니다.
5. 여행 일수만큼 day를 빠짐없이 만들고 day는 1부터 시작합니다.
6. 각 day의 sequence는 1부터 끊김 없이 증가시킵니다.
7. startTime은 24시간제 HH:mm, durationMinutes는 양의 정수로 작성합니다.
8. dailyWindow, 영업시간, 필수 방문지, 제외 조건, 이동 가능 시간을 함께 고려합니다.
9. 응답 객체에는 generationId, days, day, items, sequence, placeId, startTime, durationMinutes 외의 키를 추가하지 않습니다.
10. 출력하기 전에 모든 placeId가 candidates에 있는지, 시간대가 겹치지 않는지 스스로 한 번 검사합니다.

--- PLANMATE PROMPT START ---
[여기에 PlanMate에서 복사한 프롬프트 전체를 붙여 넣기]
--- PLANMATE PROMPT END ---
```

## 기대 JSON 형태

아래 값은 형태 설명용이다. 실제 `generationId`와 `placeId`는 반드시 복사한 PlanMate 프롬프트에 포함된 값을 사용한다.

```json
{
  "generationId": "실제 생성 ID",
  "days": [
    {
      "day": 1,
      "items": [
        {
          "sequence": 1,
          "placeId": "실제 후보 placeId",
          "startTime": "09:00",
          "durationMinutes": 90
        }
      ]
    }
  ]
}
```

## 검증 오류 수정용 지시문

PlanMate 검증 결과에 오류가 있으면 새 일정을 처음부터 만들게 하지 말고, 같은 ChatGPT 대화에서 아래 문장을 사용한다.

```text
아래는 방금 JSON에 대한 PlanMate 검증 결과입니다. 보고된 오류만 수정하되 전체 일정의 일수와 generationId는 유지해 주세요.

수정 규칙:
- 답변은 JSON 객체 하나만 반환합니다.
- candidates에 없는 placeId를 새로 만들지 않습니다.
- 오류가 없는 day와 item은 가능한 한 유지합니다.
- sequence는 각 day마다 1부터 다시 연속되게 정리합니다.
- 시간 충돌과 이동 시간 부족을 함께 해결합니다.
- 허용된 응답 키 외의 키를 추가하지 않습니다.

--- VALIDATION REPORT ---
[PlanMate 검증 결과 붙여 넣기]
--- PREVIOUS JSON ---
[직전 JSON 붙여 넣기]
```

## 개발 중 확인할 것

- 프롬프트와 응답에 실사용자 개인정보를 넣지 않는다.
- JSON 저장 전 항상 PlanMate의 검증 API를 먼저 통과시킨다.
- 후보 장소가 부족해 일정 구성이 불가능하면 임의 장소를 만들지 말고, PlanMate에서 후보 수집 조건을 조정해 새 생성 작업을 만든다.
