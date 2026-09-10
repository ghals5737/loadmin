# 설계안 — 느린 쿼리 캡처 (Phase 3-2)

상태: 구현 완료 · 2026-09-10

## 1. 문제

메트릭 오버레이는 "HikariCP 커넥션 8개가 계속 물려 있었다"까지 보여준다.
하지만 **무엇이** 물고 있었는지는 말해주지 않는다. 부하테스트에서 p95가 나빠졌을 때
다음 질문은 항상 "어느 쿼리냐"인데, 거기서 끊긴다.

## 2. 어떻게 잡을 것인가

SQL 텍스트를 얻으려면 JDBC 호출을 가로채야 한다. 선택지는 셋이었다.

| 방법 | 판단 |
|---|---|
| datasource-proxy 같은 라이브러리 | 의존성 추가. 스타터가 소비 앱에 라이브러리를 강요하게 됨 |
| 드라이버별 훅 | 드라이버마다 다름. 이식성 없음 |
| **JDK 동적 프록시로 DataSource 래핑** | 의존성 0, 풀 종류 무관. 채택 |

`DataSource.getConnection()` → `Connection.prepareStatement()` → `Statement.execute*()`
세 단계를 프록시로 감싸고 `execute*` 호출만 시간을 잰다.

## 3. 기본값을 off로 둔 이유

이 기능을 켜면 **소비 앱의 DataSource 빈이 프록시로 교체된다.** 부하테스트 도구가
묻지도 않고 할 일은 아니다. `loadmin.slow-query.enabled=true` 명시적 opt-in으로 둔다.

run이 돌고 있지 않을 때는 `getConnection()`에서 volatile 읽기 한 번 하고 원본을 그대로 돌려준다.
Connection·Statement 프록시는 아예 만들어지지 않는다.

## 4. run과 어떻게 연결하나

쿼리는 **애플리케이션의 요청 스레드**에서 실행된다. 부하 생성기 스레드가 아니다.
그래서 "지금 어느 run이 돌고 있나"를 알 방법이 없다.

`SlowQueryRecorder`가 그 참조를 들고 있는다. 엔진이 run 시작 시 `RunListener.started()`로
수집 대상을 꽂고, 종료 시 `finished()`로 뺀다. 그래서 부하가 끝나는 순간 앱의 쿼리는
다시 측정되지 않는다.

이 과정에서 엔진의 완료 콜백(`Consumer<LoadTestRun>`)을 `RunListener`(started/finished)로
일반화했다. 히스토리 저장도 리스너 하나가 됐다. 기존 생성자는 `@Deprecated`로 남겨 두었다.

## 5. 집계

같은 SQL은 하나로 합친다. 정렬 기준은 **총 시간**이다 —
120ms짜리를 50번 부르는 쿼리가 900ms짜리 한 번보다 나쁘다.

- run당 서로 다른 statement는 200개까지만 기억 (무한 증가 방지)
- 화면에는 상위 20개
- 카운터는 `LongAdder`/`AtomicLong`, 앱 요청 스레드에서 락 없이 기록

## 6. 검증에서 드러난 것

데모에 일부러 느린 엔드포인트(`/api/report`, 40000×40 크로스 조인)를 추가했다.
처음엔 **상수 나눗셈**을 썼는데, 첫 호출만 145ms이고 그다음부터 1ms 미만으로 떨어졌다.
H2가 동일 쿼리 결과를 캐싱한 것이다. 나눗수를 요청마다 바꾸도록 파라미터화해서 해결했다.

최종 확인 (8 users / 6s):

| 지표 | 값 |
|---|---|
| p50 / p95 | 314ms / 395ms |
| 잡힌 쿼리 | 153회, 평균 312ms, 최대 371ms, 합계 47.8초 |

p95의 대부분이 그 SQL이라는 게 한 화면에서 읽힌다.

HikariCP 메트릭이 프록시 때문에 깨질까 걱정했으나(Boot의 Hikari 메트릭 바인더가
`instanceof HikariDataSource`를 본다) 실제로는 `hikariActive`/`hikariPending`/`hikariMax`가
그대로 수집됐다.

## 7. 비목표

- 쿼리 계획(EXPLAIN) 수집
- 바인딩 파라미터 값 기록 — 개인정보가 섞일 수 있다
- JPA/Hibernate 레벨 통계 (N+1 감지 등)
