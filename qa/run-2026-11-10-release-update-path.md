# 번개알림 릴리스 업데이트 경로 — 조사 2026-11-10

**범위:** 읽기 전용. 푸시, 태그, 워크플로 실행, 릴리스 생성/수정, 기기 조작, 시크릿 파일·SECRETS.txt 열람 없음.

**시각:** 2026-11-10 13:37 KST  
**저장소:** Gforce10-design/kakao-bungae-alert  
**로컬 클론:** `/workspace/qa-env/src/kakao-bungae-alert` (브랜치 `qa-fixes-20261105`, HEAD `4be137d572a854cdb10772967cf81809b332bb99`)

---

## 1. 커밋이 origin에 있는지

GitHub API와 로컬 `git branch -a --contains` / `git log HEAD --not --remotes=origin`로 확인했다. origin/main은 `a8988b76f13c79d59d40b12a47cba3fc09c66ff9`로, 2026-10-31 이후 커밋이 없다.

| SHA (짧게) | 전체 SHA | origin에 있나 | origin 브랜치/태그 | 비고 |
|---|---|---|---|---|
| e1bd9c0 | e1bd9c095dd8f68c77d9a8d207c46f44dd63721e | **없음** (GET commit 422) | 없음 | 로컬 `qa-fixes-20261105`만. 2026-11-05 21:32 KST |
| b3e6c52 | b3e6c52a9334d95916619678c48a341604f92dd7 | **없음** (GET commit 422) | 없음 | 로컬 `qa-fixes-20261105`만. 2026-11-05 22:00 KST |
| 4be137d | 4be137d572a854cdb10772967cf81809b332bb99 | **없음** (GET commit 422) | 없음 | 로컬 HEAD. 2026-11-05 22:19 KST |
| eea1dce | (4be137d의 조상) | **없음** (어떤 origin ref에도 없음) | 없음 | qafix 시작 커밋. 위 3개와 함께 미푸시 |
| 1d1e017 | 1d1e017c1aec288bc908d903c5ac32c47735c110 | 있음 | `origin/cursor/signed-release-updates-5382`, 태그 `v0.0.1-test1`·`v0.0.1-test2` | 현재 최신 릴리스의 소스 |

**main은 뒤처져 있다.** `origin/main...4be137d`는 main 쪽이 0, HEAD 쪽이 13커밋이다. 13커밋은 PR 스택(#2 `30a8d1d` → #4/#5/#6 → #7 `1d1e017`)과 그 위의 미푸시 4커밋(`eea1dce`, `e1bd9c0`, `b3e6c52`, `4be137d`)이다.

열려 있는 PR (2026-11-10 확인, 하나도 main에 병합되지 않음):

- #2 `cursor/listener-rebind-88cb` → main (`30a8d1d`)
- #3 `cursor/ui-redesign-414e` → #2의 베이스 (`9107294`) — **4be137d 계보에 없음** (옆 가지)
- #4 `cursor/ui-redesign-d1ad` → #2의 베이스 (`00d935e`)
- #5 `cursor/chip-update-palette-498e` → #4 베이스 (`eb77e48`)
- #6 `cursor/editable-keywords-permissions-9099` → #5 베이스 (`de77a97`)
- #7 `cursor/signed-release-updates-5382` → #6 베이스 (`1d1e017`) — **open, 미병합**

최신 코드를 릴리스에 넣으려면:

1. 로컬 `qa-fixes-20261105` (`4be137d`와 미푸시 조상 3개)를 origin에 푸시해야 한다. 지금은 GitHub에 이 커밋 자체가 없다.
2. 그 커밋을 대상으로 릴리스를 만들거나, main에 합친 뒤 릴리스한다. main에 넣으려면 PR 스택 #2→#4→#5→#6→#7에 더해 qafix 4커밋을 올리는 식이다. 한 브랜치 `qa-fixes-20261105`를 main으로 올리는 편이 직선 이력이라 더 단순하다.
3. `release.yml`은 **main에 없다.** 기본 브랜치의 `.github/workflows/`에는 `debug-apk.yml`만 있다 (GitHub contents 404). 파일은 `1d1e017`와 로컬 `4be137d`에만 있다.

---

## 2. 서명 인증서 SHA-256

비교 기준 `release.yml` 85행 `phone_sha`:

`7B49D0522171AADC5DFCBA49955763F396948923B30FAB459C5966D6482E796D`

(아래 값은 콜론 없이 소문자로 통일)

### 2.1 커밋된 debug.keystore

경로: 로컬 클론 및 `1d1e017`/`4be137d` 트리의 `debug.keystore` (blob `bca70ab5…`). 별칭 `androiddebugkey`, 저장소 비밀번호는 Android 디버그 키스토어 기본값 `android` (공개 관례, 시크릿 파일은 열지 않음).

- 주체: `CN=Android Debug, O=Android, C=US`
- SHA-256: `bdae79bf52c4fec75b9c4b3164b76ad408966b7377569b34358191e0a190c8ca`
- SHA-1: `70286ef457c88a8b8796eb8447c2f97d25ea3cda`
- **phone_sha와 불일치**

`origin/main`에는 이 파일이 없다. `30a8d1d`에서 추가됐다.

### 2.2 로컬 디버그 APK (치프가 S25에 올린 것으로 기록된 빌드)

`apksigner verify --print-certs` + `aapt dump badging`. 패키지 이름은 모두 `app.bungaealert`.

| APK | versionCode | versionName | 서명 SHA-256 | 비고 |
|---|---|---|---|---|
| kakao-bungae-alert-qafix-e1bd9c0-debug.apk | 1 | 1.0.0 | `bdae79bf…c8ca` | 설치 로그 2026-11-05 21:35 KST Success |
| kakao-bungae-alert-qafix-b3e6c52-debug.apk | 1 | 1.0.0 | `bdae79bf…c8ca` | 설치 로그 2026-11-05 22:04 KST Success |
| kakao-bungae-alert-qafix-4be137d-debug.apk | 1 | 1.0.0 | `bdae79bf…c8ca` | 설치 로그 2026-11-05 22:24 KST Success (마지막 기록) |
| kakao-bungae-alert-qafix-eea1dce-debug.apk | 1 | 1.0.0 | `bdae79bf…c8ca` | 에뮬레이터 QA에 사용. S25 설치 로그는 없음 |
| kakao-bungae-alert-pr7-1d1e017-debug.apk | 1 | 1.0.0 | `bdae79bf…c8ca` | PR #7 디버그 빌드 |

위 다섯 개 모두 커밋된 debug.keystore와 같다. phone_sha(7b49…)와는 다르다.

설치 로그(`/workspace/qa-env/logs/install-{e1bd9c0,b3e6c52,4be137d}.log`)는 모두 `Performing Streamed Install` / `Success` / `exit 0` 뿐이다. `-r`/`-d`/uninstall 여부, 대상 시리얼은 로그에 없다. 이 턴에는 기기를 조회하지 않았다.

### 2.3 현재 릴리스 APK v0.0.1-test2

공개 자산을 `/workspace/bungae-release-check/bungae-alert-v0.0.1-test2-vc2.apk`로 받았다.

- GitHub asset digest: `sha256:5a720bce6d2d2053cfc22285416804a26eee6044f04cb2ed3ebf40484d5524e8`
- 받은 파일·로컬 사본(`/workspace/qa-env/apks/kakao-bungae-alert-phone-0.0.1-test2.apk`)·동봉 SHA256SUMS가 모두 이 해시와 같다.
- package `app.bungaealert`, versionCode **2**, versionName `0.0.1-test2`, 소스 커밋 `1d1e017`, 릴리스 본문 `signing: release-secret`
- Signer DN: `CN=Android Debug, O=Android, C=US` (디버그용 주체이지만 **키가 다름**)
- 서명 SHA-256: `7b49d0522171aadc5dfcba49955763f396948923b30fab459c5966d6482e796d`
- SHA-1: `aa16c8c9d06b9be554bed8ea19b89d9dd2d7c353`
- **phone_sha와 일치. 커밋된 debug.keystore와는 불일치.**

### 2.4 S25에 지금 깔린 앱의 인증서

**이 턴에 기기 dumpsys/인증서를 읽지 않았다. 라이브 값은 UNKNOWN.**

근거로 추론만 한다.

- 캡틴이 2026-11-10에 앱에서 “새 버전”이 뜬다고 했다. `ReleaseQuery`는 `releases/latest`를 보고, `VersionCodes.isNewer`는 `remote > installed`만 본다. 최신 릴리스 versionCode는 2이므로, 설치된 versionCode는 **2보다 작다**. 릴리스 APK(vc2)가 그대로 깔려 있다면 “최신 버전입니다”가 나와야 한다. 따라서 지금은 릴리스 vc2가 아니다.
- 치프 기록과 로컬 APK 기준으로 S25에는 디버그 qafix(`e1bd9c0` 이후 `b3e6c52`, 마지막 설치 로그는 `4be137d`)가 올라갔다. 이 APK들은 모두 vc1, 서명 `bdae79bf…c8ca`이다.
- 마지막 설치 로그는 2026-11-05 22:24 KST의 `4be137d`이다. 2026-11-10 현재 실행 중인 SHA가 `b3e6c52`인지 `4be137d`인지는 라이브 미확인 → **UNKNOWN**. 둘 다 서명은 같다.
- 더 이전 문서(`DESIGN.md` 109행, `README.md` 77행, `qa/CANONICAL-AC-PACK.md` 47행, `release.yml` phone_sha)는 폰 설치본을 `7b49d052…796d`로 적었다. 그건 릴리스 키(또는 그 키로 처음 깔린 빌드)를 가리킨다. 이후에 디버그 qafix를 덮어쓴 것으로 보이지만, 7b49에서 bdae로 바꿀 때 삭제(데이터 초기화)를 했는지는 기록이 없다 → **UNKNOWN**.
- S25 logcat 스냅샷(`/workspace/qa-env/s25-logcat-2115.txt`, 2026-11-05 21:18경, uid 10761)에는 앱이 이미 실행 중이었다. 그 시점의 인증서 덤프는 없다. `e1bd9c0` 설치 로그는 그 뒤 21:35이다.

---

## 3. 덮어설치·데이터 유지 결론

### 지금 공개된 릴리스(v0.0.1-test2)로 업데이트하면?

**데이터 유지 덮어설치는 안 된다(서명이 다르다는 전제).**

- 코드도 더 오래됐다(11월 4일 `1d1e017`). 알람 소리·진동(`b3e6c52`), 잡힌 단어 표시(`4be137d`) 등이 없다.
- versionCode만 보면 2 > 1이라 앱이 “새 버전”으로 띄운다. 버전 번호만 비교하기 때문이다.
- 설치본 서명이 디버그 키(`bdae…`)라면, 릴리스 키(`7b49…`) APK는 Android가 `INSTALL_FAILED_UPDATE_INCOMPATIBLE`으로 거부한다. 지우지 않고는 못 올린다. 지우면 감시 규칙·감지 기록이 사라진다.
- 라이브 인증서는 미확인(UNKNOWN)이지만, “새 버전”이 뜬 것과 qafix 설치 기록은 이 전제와 맞다.
- **이 알림은 누르고 설치하지 않는 것이 맞다.**

### 지금 시크릿(release-secret)으로 최신 코드 새 릴리스를 만들면?

워크플로는 시크릿 4개(`BUNGAE_KEYSTORE_BASE64`, `BUNGAE_KEYSTORE_PASSWORD`, `BUNGAE_KEY_ALIAS`, `BUNGAE_KEY_PASSWORD`)가 있으면 그 키로 서명하고 `signing=release-secret`을 적는다. v0.0.1-test2가 이미 그 경로로 만들어졌고, 결과 인증서는 `7b49…`이다. 시크릿을 바꾸지 않으면 다음 릴리스도 같은 키가 된다.

그 APK는 지금 S25 디버그 설치본 위에 **덮어쓰이지 않는다.** 데이터 유지가 안 된다.

### 데이터를 지키려면

설치본과 **같은 인증서**로 최신 코드 릴리스를 만들어야 한다.

- 같은 키: 커밋된 `debug.keystore` (`bdae79bf…c8ca`). 워크플로에 시크릿이 없으면 `signing=debug-keystore-fallback`으로 이 키를 쓴다 (`release.yml` 102–115행).
- 그러려면 캡틴 승인이 필요한 선택이 있다. (가) 그 실행 동안 릴리스 시크릿 4개를 빼거나 비워 폴백을 타게 한다. (나) 시크릿을 커밋된 debug.keystore로 교체한다. 시크릿 내용은 이 조사에서 열지 않았다.
- 그 반대(기존 7b49 키를 “정식”으로 되돌리기)는 앱을 지우고 다시 까는 경로라 데이터가 사라진다. 캡틴이 삭제를 승인하지 않는 한 쓰면 안 된다.

라이브 인증서를 설치 직전에 한 번 더 확인하는 것이 안전하다. 이 턴에는 하지 않았다.

### 새 릴리스의 versionCode

`release.yml` 77행: `version_code="${GITHUB_RUN_NUMBER}"` (해당 워크플로의 실행 번호).

Release 워크플로(id 406617449) 실행은 딱 2개다.

- run 1: 2026-11-04 16:06 KST, event=`push`, 태그 `v0.0.1-test1`, SHA `1d1e017`, versionCode 1, 성공
- run 2: 2026-11-04 16:06 KST, event=`push`, 태그 `v0.0.1-test2`, SHA `1d1e017`, versionCode 2, 성공

중간에 실패한 실행은 없다. **다음 성공 실행의 versionCode는 3으로 예상된다** (그 전에 다른 실행이 없으면). 설치본이 vc1이면 3 > 1이라 앱의 “새 버전” 비교는 올바르게 통과한다.

로컬 디버그 빌드의 기본값도 `app/build.gradle.kts` 53행에서 `BUNGAE_VERSION_CODE`가 없으면 1이다. 그래서 지금 앱이 오래된 릴리스 vc2를 새 버전으로 본 것이다.

이미 있는 태그: `v0.0.1-test1`, `v0.0.1-test2`. 새 태그가 필요하다 (예: `v0.0.1-test3`).

---

## 4. 캡틴 승인이 필요한 단계

이 조사에서는 아래를 실행하지 않았다. 진행하려면 각 항목을 따로 승인해 달라는 뜻이다.

1. **지금 앱에 뜬 업데이트는 설치하지 말 것.** `v0.0.1-test2`는 코드가 더 오래됐고, 서명이 다르면 덮어쓰기도 거부된다. 지우고 올리면 데이터가 사라진다.
2. **로컬 브랜치 푸시.** `qa-fixes-20261105` (`4be137d` + 미푸시 `eea1dce`/`e1bd9c0`/`b3e6c52`)를 origin에 올린다. 이 커밋들은 지금 GitHub에 없다. 푸시 없이는 릴리스에 최신 코드를 넣을 수 없다.
3. **서명 방식 결정 (데이터 유지의 핵심).**
   - 데이터 유지: 새 릴리스를 커밋된 debug.keystore(`bdae…`)로 서명. 지금 들어 있는 release-secret을 그대로 쓰면 `7b49…`가 나와 덮어쓰기가 거부된다. 시크릿을 비워 폴백을 타거나, 시크릿을 그 debug.keystore로 바꾸는 일은 캡틴 승인이 필요하다.
   - 7b49 키로 “정식” 릴리스를 유지: 앱을 삭제한 뒤 설치. 데이터 손실. 삭제 승인이 따로 필요하다.
4. **릴리스 트리거.** `release.yml`이 main에 없으므로, Actions 기본 화면의 “Run workflow”(workflow_dispatch)는 파일이 main에 병합되기 전에는 안 나타나거나 쓸 수 없다. 지난 두 릴리스는 **태그 푸시**(`event: push`)로 만들어졌다.
   - 4a. `4be137d`를 origin에 올린 뒤, 새 태그(예: `v0.0.1-test3`)를 그 커밋에 푸시한다. 지금까지 실제로 통했던 방법이다.
   - 4b. `release.yml`과 최신 코드를 main에 합친 뒤, 캡틴이 Actions에서 Release 워크플로를 수동 실행하고 tag 입력에 새 태그를 넣는다. 이 봇의 GitHub App은 `actions:write`가 없어 dispatch가 403이다. 캡틴이 직접 눌러야 한다.
5. **(선택) main 병합.** 2번 브랜치를 main으로 합치거나, 열린 PR 스택(#2→#4→#5→#6→#7 + qafix)을 정리한다. 태그 푸시만으로 릴리스할 때는 필수는 아니지만, 이후 업데이트와 dispatch를 쓰려면 `release.yml`이 기본 브랜치에 있어야 한다. #3은 4be137d 계보가 아니다.
6. **릴리스가 나온 뒤, 설치 전에 확인.** APK 서명 SHA-256이 설치본과 같은지(데이터 유지라면 `bdae79bf…c8ca`), versionCode가 설치본(추정 1)보다 큰지(예상 3), 패키지 이름이 `app.bungaealert`인지. 하나라도 다르면 설치를 멈춘다. 삭제·데이터 초기화는 하지 않는다.
7. **(권장, 설치 직전) S25 라이브 확인.** 지금 켜진 앱의 versionCode, versionName, 서명 SHA-256, 가능하면 GIT_SHA. 이 턴에는 기기 작업을 하지 않아 이 값은 UNKNOWN이다.

---

## 5. UNKNOWN

- 2026-11-10 기준 S25에 실제로 깔린 커밋 SHA (`b3e6c52` vs `4be137d` vs 그 외)
- S25 라이브 서명 SHA-256 (기기 미조회). 설치 기록상 `bdae…`로 추론
- 처음에 폰이 `7b49…`였다면, 디버그 qafix로 바꿀 때 앱을 지웠는지(당시 데이터 손실 여부)
- GitHub 릴리스 시크릿의 현재 내용 (열지 않음). v0.0.1-test2 결과로 그때는 release-secret=`7b49…`였다고만 안다
- 다음 `GITHUB_RUN_NUMBER`가 3인 것은, 그 전에 다른 Release 실행이 없을 때의 예상값이다

---

## 6. 산출물·근거 경로

- 받은 릴리스 APK: `/workspace/bungae-release-check/bungae-alert-v0.0.1-test2-vc2.apk`
- 로컬 qafix/릴리스 APK: `/workspace/qa-env/apks/`
- 설치 로그: `/workspace/qa-env/logs/install-e1bd9c0.log`, `install-b3e6c52.log`, `install-4be137d.log`
- 워크플로: 로컬 `4be137d`의 `.github/workflows/release.yml` (main에는 없음)
- 릴리스 페이지: https://github.com/Gforce10-design/kakao-bungae-alert/releases/tag/v0.0.1-test2
- Release 실행: https://github.com/Gforce10-design/kakao-bungae-alert/actions/workflows/release.yml

---

## 7. 실행 기록 — 2026-11-10 13:47 KST 이후 (캡틴 승인: 릴리스 키 유지, 코드 푸시, 새 릴리스 준비)

**승인 내용:** 릴리스 키(`7b49d052…796d`, release-secret)를 유지한다. 고친 코드를 올리고 새 릴리스를 준비한다. main 병합·시크릿 변경·기기 작업은 하지 않는다.

### 사전 확인

- 작업 트리: 깨끗함. 브랜치 `qa-fixes-20261105`, HEAD `4be137d572a854cdb10772967cf81809b332bb99`.
- `.github/workflows/release.yml`이 HEAD에 있음 (blob `d86190edf95f1df36f9d3d1fdbcc4a93ccb0c752`).
- 태그 `v0.1.0`은 로컬과 origin 모두에 없음 (`git tag -l`, `git ls-remote --tags origin`).
- 유닛 테스트: `./gradlew testDebugUnitTest` **BUILD SUCCESSFUL**. `testDebugUnitTest` 결과 XML 19개, **127 테스트, 실패 0, 에러 0, 스킵 0**. (리포트 시각 2026-11-05 22:19 KST, 이 커밋과 동일. 이번 실행은 UP-TO-DATE로 재사용.)

### 푸시 결과: 차단됨 (태그·릴리스 미진행)

명령: `git push -u origin qa-fixes-20261105` (강제 푸시 아님). 원격은 `https://github.com/Gforce10-design/kakao-bungae-alert`.

실패 메시지:

```
fatal: could not read Username for 'https://github.com': No such device or address
exit 128
```

원인: 이 환경의 셸 git에 GitHub HTTPS 자격 증명이 없다. `gh auth status`는 "You are not logged into any GitHub hosts." 이고, git credential helper도 설정되어 있지 않다. cursor-github API 읽기는 되지만, 아직 GitHub에 없는 커밋을 올리는 푸시 도구는 없다 (`create_branch`는 이미 있는 SHA만 가능하다).

지시대로 파일·SECRETS.txt·브라우저 쿠키에서 토큰을 꺼내 쓰지 않았고, SSH 키로 우회 푸시도 하지 않았다. 태그는 만들지 않았고, 워크플로도 실행되지 않았다. main과 시크릿은 그대로다.

**막힌 지점:** 브랜치 푸시 (2단계). 태그 `v0.1.0` 생성·푸시와 Release 워크플로는 시작하지 않음.

**다음에 필요한 것:** 이 환경에 Git 자격 증명이 생기거나, 캡틴/권한이 있는 환경에서 같은 브랜치를 일반 푸시하고 `v0.1.0` 태그를 `4be137d`에 올리면 워크플로가 이어진다.
