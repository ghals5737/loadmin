# 설계안 — 시나리오 (Phase 3-3)

상태: 구현 완료 · 2026-09-10

## 1. 문제

실제 트래픽은 엔드포인트 하나가 아니다. 로그인 → 목록 → 상세처럼 이어지는 흐름이고,
그 흐름 안에서만 드러나는 문제가 있다 (커넥션 점유가 겹친다든지, 특정 단계만 느리다든지).
지금까지 loadmin은 한 번에 엔드포인트 하나만 때릴 수 있었다.

## 2. 핵심 결정 — 단일 엔드포인트도 "1단계 시나리오"다

별도 경로를 만들지 않고 `LoadTestSpec`을 단계 목록으로 바꿨다.

```java
record LoadTestSpec(List<Step> steps, int concurrency, int durationSeconds)
record Step(String name, String httpMethod, String pathPattern, String pathTemplate, String bodyTemplate)
```

덕분에 엔진·히스토리·비교·export·UI가 전부 한 가지 모양만 다룬다.
분기가 늘지 않는다. 단일 엔드포인트 run은 `LoadTestSpec.single(...)`이 만든다.

REST API는 두 모양을 다 받는다. `steps` 배열이 오면 시나리오,
평평한 `httpMethod`/`pathPattern`/`path`가 오면 1단계짜리로 취급한다.
기존 호출과 문서의 curl 예제가 그대로 동작한다.

## 3. 실행 방식

가상 사용자 하나가 1단계부터 마지막 단계까지 순서대로 호출하고, 다시 처음으로 돌아간다
(closed loop는 그대로).

**실패한 단계가 뒤를 건너뛰지 않는다.** 로그인이 500을 뱉어도 다음 호출은 계속 나간다.
"로그인이 실패하는 상황에서 뒤 호출들은 어떻게 되나"가 부하테스트에서 보고 싶은 그림이기 때문이다.

측정은 단계별로 따로 한다. 전체 요약만 있으면 어느 단계가 느린지 묻힌다.

## 4. 단계별 지표

`RunView.StepSummary`로 단계마다 requests / errors / p50 / p95 / p99를 낸다.
**초 단위 타임라인은 단계별로 만들지 않는다** — 차트가 단계 수만큼 늘어나면 읽히지 않고,
"어느 단계가 문제냐"는 질문에는 요약으로 충분하다.

## 5. 비교 대상 판정

`targetKey()` = `"GET /api/hello > GET /api/users/{id} > POST /api/orders"`.
같은 엔드포인트를 같은 순서로 밟은 run끼리만 자동 비교한다(부하 조건이 같아야 하는 규칙은 그대로).

## 6. export

- **k6**: 단계마다 `group('이름', () => { ... })`. iteration 하나가 시나리오 한 바퀴다.
- **Gatling**: `scenario(...).exec(http("1단계")...).exec(http("2단계")...)` 체인.

단일 단계일 때는 예전처럼 평평한 코드를 낸다 — 불필요한 group으로 감싸지 않는다.

## 7. 검증

데모 앱에 3단계 시나리오(`/api/hello` → `/api/users/${int(1,20)}` → `POST /api/orders`)를
6 users / 6s로 실행:

| 단계 | requests | p50 | p95 |
|---|---|---|---|
| 1. GET /api/hello | 370 | 33ms | 52ms |
| 2. GET /api/users/{id} | 366 | 30ms | 43ms |
| 3. place order | 366 | 35ms | 52ms |

단계별 호출 수가 고르게 나오는 것이 "iteration마다 전 단계를 한 번씩" 돈다는 증거다.

같은 시나리오를 k6로 export해서 실제 실행: 324 iterations × 3 요청 = **972 checks 전부 통과**.

UI에서도 엔드포인트를 골라 단계를 추가하고 실행하는 흐름을 확인했다.

## 8. 깨지는 것

`LoadTestSpec`의 JSON 모양이 바뀌었다. **0.1.0에서 저장된 히스토리 파일은 읽히지 않는다.**
저장소가 읽을 수 없는 파일을 건너뛰도록 이미 만들어져 있어서 목록이 깨지지는 않고,
경고 로그만 남기고 무시한다. 버전을 0.2.0으로 올린 이유다.

## 9. 비목표

- 응답에서 값을 뽑아 다음 요청에 쓰는 correlation (로그인 토큰 등) — 별도 과제
- 단계별 가중치·확률 분기
- 단계 사이 think time
