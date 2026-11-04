# 번개알림

카카오톡 단톡 알림에서 **번개모임 모집**과 **선착순 나눔**을 의미로 가려 바로 알려 주는 안드로이드 앱입니다. 화면 문구는 한국어입니다. Galaxy S25 Ultra를 포함한 Android 8.0 이상에서 동작하도록 만들었고, 대상 SDK는 37입니다.

라이선스는 [GNU GPL 3.0](LICENSE)입니다. 구독 계정 로그인은 [OpenMinis](https://github.com/OpenMinis/OpenMinis) (GPL-3.0)의 인증 코드를 가져와 고쳤습니다. 앱 안 **설정 → 오픈소스 고지**에도 같은 내용을 적었습니다. OpenMinis는 이 앱의 공식 배포가 아닙니다.

광고와 분석 도구는 없습니다. 네트워크는 설정에서 켠 보조 판정(API 키, 구독 로그인, Grok 웹훅, ntfy 응답)과 GitHub 릴리스 확인에만 사용합니다. 앱에 미리 넣어 둔 웹훅이나 ntfy 토픽은 없습니다.

**앱을 받은 분은 [사용설명서](docs/사용설명서.md)부터 보세요.** 설치, 첫 허용, 규칙, 알람, 알림이 안 올 때를 쉬운 말로 적었습니다.

설계는 [DESIGN.md](DESIGN.md)에 있습니다.

## 하는 일

- 카카오톡 알림을 읽습니다. 무음 방도 알림이 게시되면 채널 중요도와 상관없이 봅니다. 요약·묶음 알림과 빈 본문, 사진·이모티콘은 앱이 죽거나 잘못 울리지 않게 처리합니다.
- 채팅방마다 규칙을 둡니다. 이름이 정확히 같을 때, 포함할 때, 끝날 때(예: `수다방`, `경제적 자유를 위하여~`)와 볼 글 종류를 고릅니다.
- 기기 안 점수로 먼저 판단합니다. 애매한 점수만, 켜 둔 보조 판정에 넘깁니다. 실패하면 점수로 돌아옵니다.
- 보조 판정은 Gemini Nano(기기에서 쓸 수 있을 때만), Gemini·OpenAI·Claude API 키, ChatGPT·Codex / Claude / GitHub Copilot 구독 로그인, Grok Bot 웹훅입니다. 순서와 사용 여부는 설정에서 바꿉니다.
- 구독 로그인은 위험 안내에 동의하기 전에는 꺼져 있습니다. 비공식 절차라 갑자기 막히거나, 제공자 약관에 어긋나거나, 계정에 불이익이 갈 수 있습니다.
- Grok 경로는 사용자가 넣은 웹훅으로 글을 보내고, 앱이 만든 ntfy 토픽으로 답을 받습니다. 수십 초가 걸릴 수 있고 Grok Bot 사용량을 쓰므로 선착순에는 느립니다.
- 알림은 이 앱이 직접 띄웁니다. 한 번만, 또는 누를 때까지 알람처럼 반복(기본 최대 3분)입니다. 알림을 누르면 가능하면 그 카카오톡 방으로 갑니다.

## 디버그 APK 받아 설치하기

스토어에는 올리지 않습니다. GitHub Actions가 푸시와 풀 리퀘스트마다 디버그 APK를 만들고, 그 실행의 **Artifacts**에 `bungae-debug-apk`로 올립니다. 릴리스 APK는 [릴리스 만들기](#릴리스-만들기)를 따릅니다. 앱은 새 릴리스의 `versionCode`가 더 크면 **업데이트** 채널로 알리고, 알림을 누르면 그 릴리스 페이지가 열립니다. 설치는 받은 APK를 직접 실행하는 방식입니다.

1. 저장소의 **Actions** 탭에서 가장 최근 `Debug APK` 실행을 엽니다.
2. 실행이 성공하면 아래 Artifacts에서 `bungae-debug-apk`를 받아 압축을 풉니다. `app-debug.apk`가 들어 있습니다.
3. 휴대폰으로 파일을 옮깁니다.
4. 설치가 막히면 브라우저나 파일 앱의 설정에서 **이 출처 허용**(알 수 없는 앱 설치)을 켭니다. 경로는 기기마다 `설정 → 애플리케이션 → 특별한 접근 → 알 수 없는 앱 설치` 근처입니다.
5. APK를 눌러 설치합니다. GitHub Actions는 저장소의 `debug.keystore`(비밀번호 `android`, 별칭 `androiddebugkey`, 인증서 SHA-256 `BD:AE:79:BF:52:C4:FE:C7:5B:9C:4B:31:64:B7:6A:D4:08:96:6B:73:77:56:9B:34:35:81:91:E0:A1:90:C8:CA`)로 서명합니다. 이 키로 설치한 뒤에는 다음 CI APK를 지우고 다시 깔 필요 없이 덮어씁니다. 그 전에 받은 APK는 실행기마다 새로 만든 디버그 키라 서명이 다릅니다. 그 앱은 한 번은 지워야 하고, 지우면 알림 접근도 풀립니다.
6. 앱을 열고 처음 설정을 따라갑니다.
   - 파일로 설치한 앱은 Android 13 이상에서 **제한된 설정** 때문에 알림 접근이 잠길 수 있습니다. 앱 정보 오른쪽 위 메뉴에서 제한된 설정을 허용한 뒤 알림 접근을 켭니다.
   - 알림 접근에서 **번개알림**을 켭니다.
   - 알림 표시를 허용합니다.
   - Android 14 이상이면 전체 화면 인텐트를 허용합니다. 반복 알림이 잠금 화면 위로 뜨는 데 필요합니다.
   - 방해금지 예외에 이 앱을 넣습니다.
   - 배터리 최적화 제외를 허용합니다. Galaxy에서는 그것만으로 부족합니다. `설정 → 배터리 및 디바이스 케어 → 배터리 → 백그라운드 사용 제한`에서 잠자는 앱·깊게 잠자는 앱을 빼고 **잠들지 않는 앱**에 넣습니다. 사용하지 않는 앱 절전도 이 앱을 재울 수 있습니다.
7. 홈 또는 설정의 **연결 상태**에서 리스너 연결, 마지막 카카오톡 알림 시각, 배터리·대기 버킷을 봅니다. 낮은 중요도 상태 알림은 캐시 상태에서 프로세스를 꺼내 두기 위한 것이고, 삼성 깊은 잠자기를 대신하지 않습니다.
8. **규칙**에서 감시할 채팅방을 추가합니다.
9. 필요하면 **설정**에서 민감도, 반복 시간, 보조 판정을 바꿉니다. 구독 로그인은 위험 안내를 동의한 뒤에만 버튼이 동작합니다.

컴퓨터에서 직접 만들려면 Android SDK 플랫폼 37.2와 Build-Tools 36, JDK 17 이상이 필요합니다.

```bash
./gradlew testDebugUnitTest assembleDebug
```

APK는 `app/build/outputs/apk/debug/app-debug.apk`입니다.

Claude 구독 로그인을 쓰려면 `app/provider-customization.properties.example`을 `app/provider-customization.properties`로 복사하고, 필요하면 `ANTHROPIC_OAUTH_IDENTIFIER_PROMPT`를 맞춥니다. API 키 방식은 이 값이 필요 없습니다.

## 릴리스 만들기

`.github/workflows/release.yml`이 서명된 릴리스 APK를 만들고 GitHub Release에 올립니다. 동작은 두 가지입니다.

- `v*` 태그를 푸시합니다. 예: `git tag v1.2.3 && git push origin v1.2.3`
- Actions 탭에서 **Release**를 수동 실행하고, `tag`에 `v1.2.3`처럼 넣습니다. 태그 푸시가 아니면 이 값이 있어야 합니다.

`versionName`은 태그에서 앞의 `v`를 뺀 값입니다. `versionCode`는 그 워크플로의 `github.run_number`입니다. 커밋 SHA는 `BuildConfig.GIT_SHA`에 들어갑니다. 릴리스 본문에는 `versionCode: N`이 있고, APK 파일 이름은 `bungae-alert-v1.2.3-vcN.apk`입니다. 앱은 본문의 번호를 먼저 읽고, 없으면 파일 이름의 `vcN`을 읽습니다.

워크플로는 APK와 `SHA256SUMS`를 아티팩트 `bungae-release-apk`로 올린 다음, `gh release --repo "$GITHUB_REPOSITORY"`로 그 태그의 GitHub Release에 첨부합니다. 같은 태그가 이미 있으면 이전 `bungae-alert-*.apk`와 `SHA256SUMS`를 지우고 다시 올립니다.

디버그 APK의 `versionCode`는 1입니다. 이 워크플로의 첫 실행도 `run_number`가 1이면 번호가 같아서 앱은 업데이트를 알리지 않습니다. 한 번 더 실행하면 번호가 올라갑니다.

### 서명 시크릿

릴리스 키를 쓰려면 저장소 Actions 시크릿 네 개가 모두 있어야 합니다. 하나라도 없으면 저장소의 `debug.keystore`(비밀번호 `android`, 별칭 `androiddebugkey`)로 서명합니다. 그 키로 깔린 디버그 빌드는 그 APK로 덮어쓸 수 있습니다.

| 시크릿 | 내용 |
| --- | --- |
| `BUNGAE_KEYSTORE_BASE64` | 키스토어 파일을 한 줄 base64로 인코딩한 값 |
| `BUNGAE_KEYSTORE_PASSWORD` | 키스토어 비밀번호 |
| `BUNGAE_KEY_ALIAS` | 키 별칭 |
| `BUNGAE_KEY_PASSWORD` | 키 비밀번호 |

지금 폰에 깔린 빌드의 인증서 SHA-256은 `7b49d0522171aadc5dfcba49955763f396948923b30fab459c5966d6482e796d`입니다. 저장소에 커밋된 `debug.keystore`의 SHA-256은 `bdae79bf52c4fec75b9c4b3164b76ad408966b7377569b34358191e0a190c8ca`라서 **같지 않습니다.** 시크릿 없이 만든 릴리스 APK는 그 폰 설치본 위에 올라가지 않습니다. 같은 인증서의 키스토어를 시크릿으로 넣어야 합니다. 보통은 그 빌드를 만든 컴퓨터의 `~/.android/debug.keystore`(별칭 `androiddebugkey`, 비밀번호 `android`)입니다. 아래 명령으로 SHA-256이 같은지 확인한 다음 등록합니다.

```bash
keytool -list -v -keystore /path/to/matching.keystore -alias YOUR_ALIAS
# SHA256이 7B:49:D0:52:21:71:AA:DC:5D:FC:BA:49:95:57:63:F3:96:94:89:23:B3:0F:AB:45:9C:59:66:D6:48:2E:79:6D 인지 확인

base64 -w 0 /path/to/matching.keystore
```

나온 한 줄을 `BUNGAE_KEYSTORE_BASE64`로, 비밀번호와 별칭을 나머지 시크릿으로 저장소 Settings → Secrets and variables → Actions에 넣습니다. 워크플로 로그에 찍힌 SHA-256이 위 값과 같아야 그 폰에 덮어쓸 수 있습니다.

릴리스를 찾는 주소는 기본적으로 `https://api.github.com/repos/Gforce10-design/kakao-bungae-alert/releases/latest`입니다. 저장소가 비공개면 인증 없는 요청은 404나 401이 되고, 앱은 죽지 않고 로그만 남깁니다. 빌드할 때 `BUNGAE_UPDATE_REPO`(예: `owner/name`)나 `BUNGAE_UPDATE_URL`(https 전체 주소)로 바꿀 수 있습니다.

앱은 24시간마다, 그리고 앱을 열 때 마지막 확인 후 6시간이 지났으면 한 번 더 확인합니다. 같은 `versionCode`는 알림을 한 번만 보냅니다. 알림 권한이 없으면 알림은 보내지 않고, 설정 → 고지에 현재 버전과 **업데이트 확인**이 있습니다. 새 버전이 있으면 설정 위에도 배너가 나옵니다.

## 알아 둘 점

- 카카오톡이 알림을 아예 올리지 않게 끈 방은 읽을 수 없습니다.
- Gemini Nano는 기기에서 사용 가능할 때만 켜지고, 그렇지 않으면 조용히 건너뜁니다. S25에서는 빠지는 경우가 많습니다.
- 구독 로그인과 Grok 웹훅은 선택입니다. 켜지 않으면 글은 기기를 나가지 않습니다.
