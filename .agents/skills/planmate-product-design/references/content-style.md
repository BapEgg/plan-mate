# PlanMate Korean content style

Read this reference for UI labels, onboarding, marketing copy, empty states, status text, validation, and errors.

## Voice

- Calm, practical, and travel-aware.
- Speak to the traveler in plain Korean and sentence case.
- Prefer a concrete outcome over a clever slogan.
- Use one term for one concept across buttons, headings, progress labels, and messages.

## Preferred framing

- `여행 조건을 입력하세요` → `누구와 어떤 속도로 여행할지 알려주세요`
- `실행 가능한 일정` → `날짜별 방문 순서와 시간`
- `AI가 최적화합니다` → `입력한 이동수단과 여행 속도를 일정에 반영합니다`
- `제출` or `계속` → name the result, such as `여행 조건 저장`, `장소 선택 완료`, or `일정 만들기`
- `오류가 발생했습니다` → say what failed and provide the next safe action.

## Words to avoid unless proven and necessary

- 완벽한, 최적의, 혁신적인, 마법처럼, 단 몇 초 만에
- AI 기반, AI-powered, AI-assisted as decorative eyebrows or repeated marketing filler
- 실행 가능한, 솔루션, 프로세스, 요청 JSON, handoff in ordinary traveler-facing UI

Developer-only tools may use precise implementation terms when they are clearly separated from ordinary traveler UI.

## AI language

- `AI 초안` is appropriate when it identifies provenance.
- Progress text should describe observable work: `장소 후보를 찾고 있어요`, `일정 조건을 확인하고 있어요`, `검증한 일정을 저장했어요`.
- Do not personify the system or imply human judgment unless the experience genuinely provides it.

## Authentication

- Returning-user copy should focus on resuming saved trips.
- Signup copy should explain the immediate next step; keep email verification guidance near signup, not in the main value proposition.
- Keep provider naming consistent: `Google`, `네이버`, `카카오`.
- A field label labels. A placeholder should add a useful example or be omitted instead of repeating the label.

## States

- Empty states invite one meaningful action.
- Errors identify the affected task and the recovery action without apology or blame.
- Success messages use the same action vocabulary as the control that triggered them.
