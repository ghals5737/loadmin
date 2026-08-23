# Maven Central 배포 — 직접 해야 하는 준비

`io.github.ghals5737:loadmin`을 Maven Central에 올리기 위해 **계정·키가 필요한 작업**만 모았다.
빌드 스크립트와 GitHub Actions 워크플로는 이 준비가 끝난 뒤에 붙인다.

> 절차는 Central Portal(central.sonatype.com) 기준. 2025년 6월 30일자로 기존 OSSRH(s01.oss.sonatype.org)는
> 종료됐으므로 옛날 블로그의 `nexus-staging-maven-plugin` 방식은 따라가지 말 것.
> 화면 문구가 바뀌었으면 사이트 안내를 우선한다.

---

## 1. Central Portal 계정과 네임스페이스 검증

1. https://central.sonatype.com 에 GitHub 계정으로 로그인
2. **Namespaces → Add Namespace** 에서 `io.github.ghals5737` 등록
3. Portal이 랜덤 코드(예: `a1b2c3d4e5`)를 준다 → **GitHub에 그 이름 그대로 public 레포를 만든다**
   - `https://github.com/ghals5737/a1b2c3d4e5`
   - 빈 레포여도 된다. 이게 "이 GitHub 계정이 내 것"이라는 증명이다
4. Portal에서 **Verify Namespace** 클릭 → 검증되면 임시 레포는 지워도 된다

검증이 끝나면 `io.github.ghals5737.*` 아래 모든 artifact를 이 계정으로 올릴 수 있다.

## 2. Publishing User Token 발급

Portal → 우상단 계정 → **View Account → Generate User Token**

`username` / `password` 형태의 문자열 쌍이 나온다. **로그인 비밀번호가 아니라 이 토큰**을 배포에 쓴다.
한 번만 표시되므로 비밀번호 관리자에 저장할 것.

## 3. GPG 서명 키

Central은 모든 artifact에 서명을 요구한다.

```bash
brew install gnupg
```

```bash
gpg --full-generate-key
```

- 종류: RSA and RSA
- 길이: **4096**
- 만료: 2년 권장 (무기한도 가능하지만 만료 갱신이 안전하다)
- 이름/이메일: **POM에 들어갈 값과 같은 걸 쓰는 게 깔끔하다** (4-③ 결정 항목 참고)
- 패스프레이즈: 반드시 설정하고 비밀번호 관리자에 저장

키 ID 확인:

```bash
gpg --list-secret-keys --keyid-format=long
```

`sec rsa4096/ABCD1234EF567890` 에서 `/` 뒤 16자리가 키 ID다.

**공개키를 keyserver에 올린다.** 이걸 빼먹으면 배포 검증에서 떨어진다.

```bash
gpg --keyserver keyserver.ubuntu.com --send-keys ABCD1234EF567890
```

CI에서 서명하려면 개인키를 base64로 내보내 시크릿에 넣는다:

```bash
gpg --export-secret-keys ABCD1234EF567890 | base64 | pbcopy
```

> ⚠️ 개인키와 패스프레이즈는 절대 레포에 커밋하지 말 것.
> 파일로 내보냈다면 사용 후 `rm`으로 지우고, 백업은 비밀번호 관리자에만 둔다.

## 4. GitHub Secrets 등록

레포 → **Settings → Secrets and variables → Actions → New repository secret**

| 이름 | 값 |
|------|-----|
| `CENTRAL_USERNAME` | 2번에서 받은 토큰 username |
| `CENTRAL_PASSWORD` | 2번에서 받은 토큰 password |
| `GPG_PRIVATE_KEY` | 3번의 base64 개인키 전체 |
| `GPG_PASSPHRASE` | 3번의 패스프레이즈 |

## 5. 로컬에서 시험 배포하려면

`~/.gradle/gradle.properties` (레포 밖이다 — 커밋될 일이 없다):

```properties
mavenCentralUsername=<토큰 username>
mavenCentralPassword=<토큰 password>
signing.keyId=ABCD1234EF567890
signing.password=<패스프레이즈>
signing.secretKeyRingFile=/Users/hbrc/.gnupg/secring.gpg
```

GnuPG 2.1+ 는 `secring.gpg`를 더 안 만든다. 필요하면:

```bash
gpg --export-secret-keys ABCD1234EF567890 > ~/.gnupg/secring.gpg
```

---

## 내가 처리할 것 (준비 끝나면)

- `maven-publish` + `signing` 플러그인 설정, sources/javadoc jar 생성
- POM 필수 메타데이터: name, description, url, license, developers, scm
- 배포 대상은 `loadmin-core` / `loadmin-ui` / `loadmin-spring-boot-starter` 3개
  (`loadmin-demo`는 제외)
- 태그 푸시(`v0.1.0`)로 도는 GitHub Actions 워크플로
- `SNAPSHOT` 버전은 Central에 못 올리므로 릴리스 버전 처리

## 시작 전에 결정해줘야 하는 것 4가지

**① 첫 버전 번호** — `0.1.0`을 제안한다. Phase 2까지 기능이 있고 API가 굳지 않았으니 0.x가 맞다.

**② POM `developers`에 넣을 이름과 이메일**
POM은 **영구 공개**된다. Maven Central은 한 번 올라간 버전을 지울 수 없다.
개인 이메일(`hhm2hbrc@gmail.com`)을 그대로 노출할지, GitHub noreply 주소를 쓸지 정해야 한다.
GitHub이 제공하는 `<숫자>+ghals5737@users.noreply.github.com` 형태를 쓰면 스팸 노출을 피할 수 있다.
(Settings → Emails 에서 확인 가능)

**③ GPG 키의 이름/이메일** — ②와 같은 값으로 맞추는 걸 권한다. 키도 keyserver에서 공개 조회된다.

**④ 배포 트리거** — 태그 푸시 자동 배포 vs GitHub Actions 수동 실행(`workflow_dispatch`).
첫 배포는 수동을 권한다. 태그를 잘못 밀어서 되돌릴 수 없는 버전이 올라가는 사고를 막을 수 있다.

---

## 릴리스하는 법 (파이프라인 구축 완료)

버전은 루트 `build.gradle.kts`의 `version`이 기준이고, 태그와 값이 다르면 워크플로가 먼저 실패한다.

```bash
git tag v0.1.0
```

```bash
git push origin v0.1.0
```

태그가 올라가면 `.github/workflows/publish.yml`이:

1. 태그와 프로젝트 버전이 같은지 확인
2. `./gradlew build` — 테스트까지 통과해야 진행
3. GPG 개인키를 정규화하고(armored / base64 어느 형태든 처리) 서명
4. `loadmin-core`, `loadmin-ui`, `loadmin-spring-boot-starter` 3개를 Central Portal에 업로드

업로드 후 https://central.sonatype.com 의 **Deployments** 에서 상태가 `VALIDATED`가 되면,
내용을 확인하고 **Publish** 를 누른다. 여기부터는 되돌릴 수 없다.

Portal에서 누르는 것도 생략하고 싶으면 `build.gradle.kts`의
`publishToMavenCentral(automaticRelease = false)` 를 `true` 로 바꾸면 된다.
검증만 통과하면 그대로 공개된다.

수동 실행이 필요하면 Actions 탭 → Publish to Maven Central → **Run workflow**.

다음 버전을 낼 때는 `build.gradle.kts`의 `version`을 올리고 커밋한 뒤 같은 태그 절차를 밟는다.

## 되돌릴 수 없다는 점만 기억할 것

Maven Central에 **published 된 버전은 삭제·수정이 불가능하다.** 잘못 올리면 새 버전을 올리는 수밖에 없다.
그래서 Portal에서 업로드 후 상태가 `VALIDATED`로 뜬 다음, **Publish 버튼을 누르기 전에 내용을 확인**하는 절차가 있다.
파이프라인도 자동 publish까지 가지 않고 Portal에서 확인 후 누르는 형태로 만들 생각이다.
