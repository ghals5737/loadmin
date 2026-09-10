# 설계안 — 요청 파라미터/바디 템플릿 (Phase 2-1)

상태: 구현 완료 · 작성 2026-08-19 / 반영 2026-08-20

## 1. 문제

`LoadTestSpec.path`는 고정 문자열이고, `LoadTestEngine.runVirtualUser()`가 매 요청 그대로 사용한다.
`/api/users/{id}`를 테스트하면 모든 VU가 테스트 내내 같은 `id` 하나만 호출한다.

그 결과 DB 버퍼풀·JPA 캐시·앱 레벨 캐시가 전부 히트해서 p95가 실제보다 낙관적으로 나온다.
부하테스트 도구로서 신뢰도를 깎는 지점이므로 Phase 2에서 가장 먼저 해결한다.

## 2. 목표 / 비목표

목표
- 경로·쿼리·바디에 요청마다 다른 값을 넣을 수 있는 템플릿
- `@LoadTest` 어노테이션이 기본 템플릿을 들고 다님 (UI 프리필)
- UI에서 렌더 결과 미리보기
- 템플릿이 `@LoadTest` 대상이 아닌 엔드포인트로 새는 것을 구조적으로 차단

비목표 (이번 범위 밖)
- 헤더 템플릿, CSV/파일 데이터 피드
- 핸들러 시그니처/DTO 자동 추론
- 응답값 추출 후 다음 요청에 사용 (correlation), 시나리오 체이닝

## 3. 템플릿 문법

`${생성기}` 또는 `${생성기(인자)}`. `$$`는 리터럴 `$`. 플레이스홀더는 첫 `}`에서 끝난다.
경로 패턴의 `{id}`와 겹치지 않도록 `$` 접두를 쓴다.

| 표현 | 결과 | 비고 |
|---|---|---|
| `${int(1,20)}` | 1~20 랜덤 정수 | 양끝 포함, min ≤ max |
| `${cycle(1,500)}` | 같은 범위를 순서대로 돌며 순환 | 2026-09-10 추가. 범위를 균등하게 덮음 |
| `${seq}` / `${seq(1000)}` | 1부터 / 1000부터 증가 | run 전체가 공유하는 AtomicLong |
| `${uuid}` | 랜덤 UUID v4 | |
| `${alpha(8)}` | `[a-z0-9]` 8자 | 길이 1~256 |
| `${pick(seoul\|busan\|jeju)}` | 목록 중 하나 | 리터럴에 `)`, `\|` 불가 |
| `${now}` | epoch millis | |

예시
```
경로   /api/users/${int(1,20)}
쿼리   /api/search?q=${pick(shoes|bags)}&page=${int(1,5)}
바디   {"orderNo":"${uuid}","qty":${int(1,5)},"city":"${pick(seoul|busan)}"}
```

파싱 실패(닫히지 않은 `${`, 미지원 생성기, 잘못된 인자)는 `IllegalArgumentException` →
run 시작 API가 400으로 되돌려준다. 앱 부팅은 절대 깨뜨리지 않는다.

## 4. 보안 모델

현재 `LoadminRunController`는 "요청된 concrete path가 endpoint의 매핑 패턴에 매치되는가"를 검사해서
`@LoadTest`가 안 붙은 엔드포인트로 부하를 쏘지 못하게 막는다. 템플릿이 들어오면 이 가드가 뚫릴 수 있다.

3중 방어로 간다.

1. **모드 분리** — `ValueTemplate.Mode.PATH` / `Mode.BODY`
   PATH 모드에서 *생성된 값*은 ``/ ? # & = % ; \``, 공백, 제어문자를 포함할 수 없다.
   (`%`는 `%2F` 같은 인코딩 우회, `;`는 matrix parameter를 막기 위함.)
   `int` `seq` `uuid` `alpha` `now`는 구조적으로 이미 만족한다. `pick`의 리터럴만 compile 시점에 검증한다.
   (템플릿의 *리터럴 텍스트*는 사용자가 직접 쓴 것이므로 제한하지 않는다. 규칙은 "생성값만 제한".)
2. **compile 시점 거부** — 위반하는 `pick` 리터럴은 파싱 단계에서 예외.
3. **start 시점 샘플 검증** — run 시작 전 경로 템플릿을 20회 렌더해서 전부 매핑 패턴에 매치되는지 확인.
   하나라도 어긋나면 400. (1·2가 뚫려도 여기서 걸린다.)

BODY 모드는 문자 제한 없음.

## 5. 쿼리 스트링

지금은 쿼리 스트링을 쓸 수 없다. `PathContainer.parsePath("/api/x?a=1")`이 `?a=1`까지 세그먼트로 보기 때문에
패턴 매칭에서 탈락한다.

변경: 입력 경로를 첫 `?`에서 잘라 **앞부분만** 패턴 매칭 대상으로 쓰고, 전체 문자열을 WebClient에 넘긴다.
쿼리 구간의 생성값도 PATH 모드 규칙을 그대로 적용한다(값이 `&`/`=`를 만들어 파라미터를 주입하지 못하게).

## 6. core API

신규 `io.github.ghals5737.loadmin.core.template.ValueTemplate`

```java
public final class ValueTemplate {
    public enum Mode { PATH, BODY }

    public static ValueTemplate compile(String source, Mode mode); // IllegalArgumentException
    public String render();
    public boolean dynamic();
    public String source();
}
```

- compile은 run당 1회, render는 요청마다. 내부는 `Supplier<String>[]` 배열 + `StringBuilder` 1개.
- 플레이스홀더가 없으면 `render()`는 `source`를 그대로 반환 (할당 0).
- 상태를 가진 생성기(`seq`)는 인스턴스가 소유 → run 안의 모든 VU가 하나의 카운터를 공유.
- 난수는 `ThreadLocalRandom` → VU 스레드 간 경합 없음.
- 렌더 비용은 요청당 수백 ns 수준으로, HTTP 왕복 대비 측정에 영향 없음.

## 7. 변경되는 기존 타입

**`LoadTest`** — 기본 템플릿 속성 추가 (둘 다 기본값 빈 문자열, 기존 사용처 그대로 컴파일됨)
```java
public @interface LoadTest {
    String path() default "";   // 비면 UI가 매핑 패턴을 그대로 채움 (현재 동작)
    String body() default "";
}
```

**`LoadTestEndpoint`** — `pathTemplate`, `bodyTemplate` 필드 추가.
스캐너가 어노테이션 값을 읽어 `/loadmin/api/endpoints` 응답에 실어 보내고 UI가 프리필한다.
스캐너는 템플릿을 검증하지 않는다(부팅을 깨뜨리지 않기 위해). 잘못된 템플릿은 run 시작 시 400으로 드러난다.

**`LoadTestSpec`** — `path` → `pathTemplate`, `body` → `bodyTemplate` 로 개명.
`RunView`가 spec을 그대로 UI에 노출하므로 `index.html`의 `view.spec.path` 참조도 함께 수정.

**`LoadTestEngine`** — `start()`에서 두 템플릿을 compile(실패 시 기존 validate와 같은 경로로 400),
`runVirtualUser()` 루프 안에서 매 요청 `render()`.

## 8. REST 변경

- `POST /loadmin/api/runs` — 필드 이름(`path`, `body`)은 그대로 두고 값에 템플릿을 허용.
  검증 = compile + 20샘플 패턴 매칭.
- 신규 `POST /loadmin/api/templates/preview`
  요청 `{pathPattern, path, body}` → 응답 `{paths: [3개], bodies: [3개]}`, 실패 시 400 `{error}`.
  생성기가 서버(Java)에 있으므로 미리보기도 서버가 렌더한다.

## 9. UI

경로 입력과 바디 입력 아래에 "Sample requests" 3줄을 붙인다.
입력 300ms debounce → preview API 호출 → 렌더 결과 또는 에러 메시지 표시.
템플릿을 쓰지 않으면 지금과 완전히 동일하게 동작한다.

## 10. 테스트

이 프로젝트에는 아직 테스트가 없다. `ValueTemplate`은 순수 로직이라 여기서부터 시작하기 좋다.
`loadmin-core`에 test source set을 신설하고(JUnit 5, 버전은 spring-boot BOM) `ValueTemplateTest`를 붙인다.

커버 케이스: 정적 템플릿 무변경 / 각 생성기 범위 / `seq` 증가·스레드 안전 / `$$` 이스케이프 /
닫히지 않은 `${` / 미지원 생성기 / `int(5,1)` 역범위 / PATH 모드에서 `pick` 리터럴의 `/` 거부 /
BODY 모드에서는 허용 / 렌더 20회가 전부 패턴 매칭.

## 11. 작업 순서

1. core — `ValueTemplate` + 단위 테스트 (테스트 인프라 포함)
2. core — `LoadTest` 속성, `LoadTestEndpoint`, `LoadTestEndpointScanner`
3. core — `LoadTestSpec`, `LoadTestEngine`
4. starter — `LoadminRunController` 검증 + preview API
5. ui — 미리보기 + `spec.path` 참조 수정
6. demo — `@LoadTest`에 템플릿 예시를 넣고 실제 구동 확인 (`/api/users/${int(1,20)}` 로 캐시 히트가 깨지는지)

## 12. 열린 항목

- 스캔 시점에 어노테이션 템플릿을 compile 해보고 잘못된 것은 endpoint 응답에 `warning`으로 실어 보낼지 → 후순위
- `pick` 리터럴에 `)` `|` 를 넣어야 하는 요구가 생기면 이스케이프 문법 필요 → 실사용에서 나오면 그때
