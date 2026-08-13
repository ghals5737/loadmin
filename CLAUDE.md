# loadmin — 프로젝트 브리프

Spring 부하테스트 오픈소스. Claude Code용 컨텍스트 문서. 이 문서 기준으로 프로젝트를 시작한다.

## 1. 컨셉

**"API에 어노테이션을 달면, Swagger UI처럼 부하테스트 웹페이지가 자동으로 열리는 스프링 부트 스타터"**

- 사용자는 컨트롤러 핸들러에 `@LoadTest` 어노테이션만 붙인다
- 앱을 띄우면 `/loadmin` 경로에 부하테스트 UI가 자동 노출된다
- UI에서 동시 사용자 수 / duration을 설정하고 부하를 쏜다
- 결과 화면에 latency 그래프 + **서버 내부 메트릭 오버레이**를 함께 보여준다

## 2. 핵심 차별화 (이게 존재 이유)

기존 부하테스트 툴(k6, Gatling, nGrinder, JMeter)은 전부 외부 프로세스라 **클라이언트 측 지표(RPS, latency)만 보인다.** loadmin은 앱 안에 살고 있으므로:

- Tomcat 스레드풀 사용률
- HikariCP 커넥션 풀 상태
- GC pause
- (확장) 느린 쿼리

를 Micrometer/Actuator에서 긁어 **latency 그래프에 오버레이**할 수 있다.

포지셔닝: "부하를 쏘는 툴"이 아니라 **"부하 맞는 동안 서버 내부를 보여주는 툴"**.

참고: Swagger try-it-out은 단발 호출이라 경쟁 아님. JMeter/k6의 OpenAPI import는 이미 존재하므로 "스펙에서 부하테스트 생성"만으로는 차별화가 안 됨. 서버 내부 관측 결합이 핵심.

## 3. 알려진 함정과 대응 (설계 제약)

### 3-1. 같은 JVM에서 부하를 쏘면 측정이 오염된다
부하 생성기가 서버와 같은 프로세스/머신에서 돌면 CPU·스레드·GC를 서로 뺏어먹어 숫자가 왜곡된다. 대응:

- **간이 모드(MVP)**: 격리된 전용 스레드풀 + 별도 WebClient로 자기 자신에게 요청. "로컬/개발용 간이 부하테스트"임을 문서에 명시
- **진지 모드(후순위)**: 어노테이션 메타데이터로 k6/Gatling 스크립트를 자동 생성해서 외부에서 쏘게 하고, loadmin UI는 서버 측 메트릭 관측 담당

### 3-2. 프로덕션에서 열리면 셀프 DDoS 버튼이다
- `loadmin.enabled=true` **명시적 opt-in이 아니면 빈 등록 자체를 안 함** (기본 disabled)
- 문서에 프로덕션 사용 경고 명시

## 4. 아키텍처

멀티모듈 Gradle 프로젝트 (springdoc-openapi 구조 참고):

```
loadmin/
├── loadmin-core/                 # @LoadTest 어노테이션, 부하 엔진, 메트릭 수집
├── loadmin-ui/                   # 정적 웹 리소스 (부하테스트 UI)
└── loadmin-spring-boot-starter/  # auto-configuration
```

동작 방식:
1. `RequestMappingHandlerMapping`에서 `@LoadTest` 붙은 핸들러를 런타임 스캔
2. `/loadmin` 경로에 UI 서빙 (auto-configuration으로 등록)
3. UI → 백엔드 API로 테스트 설정 전달 (동시 사용자 수, duration, 파라미터 값)
4. 격리된 스레드풀에서 WebClient로 대상 API에 요청 발사
5. 실행 중 Micrometer 레지스트리에서 스레드풀/커넥션풀/GC 메트릭을 주기적으로 샘플링
6. 결과: latency 백분위(p50/p95/p99) 그래프 + 서버 메트릭 오버레이

기술 스택: Java 17+ / Spring Boot 3.x / Gradle 멀티모듈 / Micrometer / (UI는 vanilla JS 또는 경량 프레임워크 — 스타터에 번들되므로 빌드 산출물이 가벼워야 함)

배포: Maven Central, groupId `io.github.<깃헙아이디>` — artifact 좌표에 자동으로 소유자가 남으므로 이름 충돌 걱정 없음. GitHub에 동명 레포(drasill/loadmin, 스타 2개, loadavg 래퍼)가 있으나 도메인이 완전히 다르고 방치 상태라 무시.

## 5. 로드맵

### Phase 0 — 컨셉 증명 (첫 커밋 목표)
- [ ] Gradle 멀티모듈 스캐폴딩
- [ ] `@LoadTest` 어노테이션 정의
- [ ] 어노테이션 붙은 엔드포인트를 런타임 스캔해서 `/loadmin`에 목록만 뿌리기
- 이것만 되면 "스웨거처럼 열린다" 컨셉 증명 완료

### Phase 1 — MVP
- [ ] auto-configuration + `loadmin.enabled` opt-in 가드
- [ ] UI에서 동시 사용자 수 / duration 설정
- [ ] 격리 스레드풀 + WebClient 기반 부하 실행 엔진
- [ ] latency 결과 (p50/p95/p99, RPS, 에러율)
- [ ] Actuator/Micrometer 메트릭 오버레이 (스레드풀, HikariCP, GC 최소 3종)

### Phase 2 — 완성도
- [ ] 요청 파라미터/바디 템플릿 (랜덤 값 생성 전략)
- [ ] 테스트 히스토리 저장/비교
- [ ] README + 사용 예제 데모 앱
- [ ] Maven Central 배포 파이프라인

### Phase 3 — 확장 (후순위)
- [ ] k6/Gatling 스크립트 export (진지 모드)
- [ ] 느린 쿼리 캡처 연동
- [ ] 시나리오 (여러 API 순차 호출)

## 6. 지금 당장 할 일

1. GitHub에 `loadmin` 레포 생성 (public)
2. Phase 0 스캐폴딩부터 시작
3. 커밋 컨벤션/라이선스(Apache 2.0 권장) 초기 세팅
