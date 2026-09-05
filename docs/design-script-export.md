# 설계안 — k6 / Gatling 스크립트 export (Phase 3-1)

상태: 구현 완료 · 2026-08-24

## 1. 왜 필요한가

`CLAUDE.md` 3-1에 적어둔 근본 한계다. 부하 생성기가 서버와 같은 JVM에서 돌면 CPU·스레드·GC를
서로 뺏어먹어 숫자가 왜곡된다. 지금까지는 "로컬/개발용 간이 모드"라고 문서에 명시하는 것으로
넘어갔지만, 진지한 측정은 부하를 **밖에서** 쏴야 한다.

이 항목은 그 정면 대응이다. loadmin은 부하를 쏘는 역할을 내려놓고,
**어노테이션이 아는 것(엔드포인트·파라미터 템플릿·부하 조건)을 외부 러너용 스크립트로 내보낸다.**
부하는 k6/Gatling이 밖에서 쏘고, loadmin은 원래 잘하던 서버 내부 관측을 계속한다.

## 2. 핵심 문제 — 템플릿을 다른 언어로 옮기기

`${int(1,20)}`은 지금 Java 람다(`Supplier<String>`)로만 존재한다. 이걸 JS와 Java DSL로
번역하려면 **파싱 결과가 구조로 남아 있어야** 한다.

그래서 `ValueTemplate`을 리팩터링한다.

```java
public sealed interface Part permits Literal, Placeholder
public record Literal(String text)
public record Placeholder(Kind kind, List<String> args)
public enum Kind { INT, SEQ, UUID, ALPHA, PICK, NOW }
```

파싱은 `List<Part>`를 만들고, 렌더용 `Supplier<String>[]`는 그 파트에서 파생시킨다.
파싱 로직은 한 곳에 남고, 익스포터는 `parts()`를 읽어 각 언어로 번역한다.
렌더 성능(요청마다 호출되는 경로)은 그대로 유지한다.

## 3. 번역 표

| loadmin | k6 (JS) | Gatling (Java DSL) |
|---|---|---|
| `${int(1,20)}` | `randomInt(1, 20)` | `ThreadLocalRandom.current().nextInt(1, 21)` |
| `${seq}` | `nextSeq()` (VU별 카운터, 아래 참고) | `SEQ.getAndIncrement()` (AtomicLong) |
| `${uuid}` | `uuid()` | `UUID.randomUUID()` |
| `${alpha(8)}` | `randomAlpha(8)` | `randomAlpha(8)` |
| `${pick(a\|b)}` | `pick(['a','b'])` | `pick("a", "b")` |
| `${now}` | `Date.now()` | `System.currentTimeMillis()` |

**`${seq}`의 의미가 k6에서는 달라진다.** k6는 VU마다 별도 JS 런타임이라 모듈 레벨 변수를
공유할 수 없다. 생성된 스크립트는 `__VU` 로 시작값을 띄워 VU 간 충돌을 피하고
(`start + (__VU - 1) * 1_000_000`), 그 사실을 주석으로 남긴다.
Gatling은 같은 JVM이므로 `AtomicLong`으로 원래 의미가 유지된다.

## 4. 생성물

외부 의존성 없이 그대로 실행되는 것이 목표다. k6 스크립트는 `jslib.k6.io` import를 쓰지 않고
필요한 헬퍼(uuid, randomAlpha 등)를 파일 안에 넣는다.

- 대상 URL은 `BASE_URL` 환경변수(k6) / `-DbaseUrl`(Gatling)로 덮어쓸 수 있게 한다.
  기본값은 export 시점의 앱 주소.
- 부하 조건은 UI에서 설정한 동시 사용자 수·duration을 그대로 옮긴다.
  두 러너 모두 **closed model**로 맞춘다 (loadmin 엔진과 같은 의미:
  k6는 `vus` + `duration`, Gatling은 `constantConcurrentUsers(n).during(s)`).
- 상태 코드 체크를 넣어 에러율이 러너 쪽에서도 잡히게 한다.

## 5. API와 UI

`POST /loadmin/api/export/{format}` (`format` = `k6` | `gatling`)
→ 본문은 run 시작과 같은 형태(httpMethod, pathPattern, path, body, concurrency, durationSeconds)
→ 응답은 스크립트 텍스트.

검증은 run 시작과 **동일한 가드**를 쓴다. `@LoadTest` 엔드포인트가 아니면 400.
export가 우회로가 되면 안 된다.

UI는 설정 화면의 Start 버튼 옆에 `Export k6` / `Export Gatling` 버튼을 두고,
받은 텍스트를 Blob으로 내려받게 한다.

## 6. 검증

이 개발 환경에 k6 v2.0.0과 node가 있으므로, 생성한 스크립트를 **데모 앱에 실제로 실행**해서
확인한다. 문법 통과만이 아니라 요청이 정말 나가고 에러율 0이 나오는지까지 본다.

## 6-1. 실제 검증 결과

- k6 v2.0.0으로 생성 스크립트 두 개를 데모 앱에 실행
  - `GET /api/users/${int(1,20)}`, 20 VU / 8s → 2935 요청, 실패 0, p95 71.6ms
  - `POST /api/orders` (uuid·seq·pick·alpha·now 전부 사용), 10 VU / 5s → 1494 요청, 100% 성공
- Gatling 3.11.5 의존성을 받아 생성된 `LoadminSimulation.java` 컴파일 통과
  (`status().lt(400)`, `injectClosed(constantConcurrentUsers(n).during(s))` 유효 확인)
- UI 버튼 → `POST /loadmin/api/export/k6` 200, 콘솔 에러 없음

## 7. 비목표

- Gatling 프로젝트 스캐폴딩(build.gradle, 디렉토리 구조) 생성 — 단일 Simulation 파일만 낸다
- 시나리오(여러 API 순차 호출) export — Phase 3의 별도 항목
- 헤더·인증 처리 — 지금 엔진도 지원하지 않는다
