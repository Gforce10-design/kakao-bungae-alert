# 번개알림 (kakao-bungae-alert) — 수용 기준(AC) 팩

> **APPROVED — 캡틴 승인 2026-11-05 08:36 KST (음성 통화) (캡틴 승인 전, 구속력 없음)**
> 작성: 2026-11-05 KST, Grok Bot. 테스트 실행·코드 수정·기기 조작 없음.

## 읽은 출처
- 저장소 main `a8988b7` (공개 clone): README.md, AndroidManifest.xml, detect/{DecisionPolicy,ChatMatcher,KakaoNoticeParser,ScoreClassifier,Models}.kt, data/Stores.kt, alert/Alerts.kt(시그니처만)
- PR #6 "Make match phrases editable and show when notification permission is missing" — **open, 미병합** (PR #5 위에 스택)
- PR #7 "Publish signed release APKs and check versionCode" — **open, 미병합**
- APK 존재 확인만: /workspace/apks/kakao-bungae-alert-{debug,pr6-debug,pr6b-debug}.apk (내용 미분석)
- 읽지 않음: DESIGN.md 전문, ui/Screens.kt, ai/Judges.kt 세부, 테스트 본문

## AC 목록
| ID | 제목 | 사전조건 | 단계 | 기대 결과 | 심각도 | 근거 |
|---|---|---|---|---|---|---|
| BA-01 | 카카오톡 패키지만 읽음 | 알림 접근 허용 | com.kakao.talk 외 앱 알림 게시(F-PKG-OTHER) | 파싱 결과 없음, 알림 없음 | BLOCK | KakaoNoticeParser.kt:37-41 `shouldRead` |
| BA-02 | 무음 방도 감시 | 무음 처리된 감시 방 | 저중요도(importance ≥ -2) 카톡 알림 게시 | 처리됨(중요도로 거르지 않음) | BLOCK | README "무음 방도…"; KakaoNoticeParser.kt:39 |
| BA-03 | 채팅방 규칙 매칭(정확/포함/끝) | 규칙 3종 등록 | F-CHAT 픽스처로 매칭 | EXACT=완전일치, CONTAINS=포함, ENDS_WITH=끝일치; 공백 trim; 비활성 규칙·빈 패턴은 불일치 | BLOCK | ChatMatcher.kt:4-16 |
| BA-04 | 사진·이모티콘·빈 본문 무시 | 감시 방 | "사진", "(이모티콘)", "" , "ㅋㅋㅋ" 알림 | 크래시 없음, 알림 없음(mediaOrEmpty → CLEAR_REJECT) | BLOCK | ScoreClassifier.kt:36-48; DecisionPolicy.kt:49 |
| BA-05 | 명확한 번개 모집글 즉시 알림 | 민감도 기본, 보조판정 꺼짐 | F-MSG-MEETUP 게시 | 점수 ≥ cutoff+band → 앱 알림 1회(CLEAR_MATCH) | BLOCK | DecisionPolicy.kt:67-69 |
| BA-06 | "번개장터"는 번개 가점 없음 | — | "번개장터에 올렸어요" | 번개 가점 미적용, 알림 없음 | WARN | ScoreClassifier.kt:51-53 |
| BA-07 | 보조판정 실패 시 점수로 복귀 | 경계 점수 글, 보조판정 1개 켬, 네트워크 차단 | 경계 글 게시 | 타임아웃/예외 후 score ≥ cutoff면 알림, 요약에 "보조판정 없음" | BLOCK | DecisionPolicy.kt:72-93,118-119; README |
| BA-08 | 보조판정은 경계 구간에서만 호출 | 보조판정 켬 | 명확 일치/명확 거절 글 게시 | 네트워크 요청 없음 | WARN | DecisionPolicy.kt:64-69 |
| BA-09 | 중복 억제 10분 | — | 같은 방·같은 본문(공백만 다름) 2회 | 두 번째는 처리 안 함; 10분 경과 후 재처리 | WARN | KakaoNoticeParser.kt:120-138 |
| BA-10 | 반복 알람 최대 3분 | 알림 방식 "확인할 때까지 반복" | 일치 글 후 방치 | 최대 180초 후 정지; 누르면 정지 및 가능하면 해당 방 열림 | BLOCK | Stores.kt:23 maxRingSeconds=180; README; Models.kt:31 |
| BA-11 | 외부 전송 기본 없음 | 새 설치, 보조판정 미설정 | 일치 글 처리 | 네트워크 요청 0건; 기본 웹훅·ntfy 토픽 없음 | BLOCK | README "켜지 않으면 글은 기기를 나가지 않습니다" |
| BA-12 | 구독 로그인 동의 전 비활성 | 새 설치 | 구독 로그인 버튼 누름 | 위험 안내 동의 전 동작 안 함 | WARN | README "구독 로그인은 위험 안내에 동의하기 전에는 꺼져 있습니다" |
| BA-13 | (PR #6) 권한 배너 | PR #6 빌드, 알림 접근 OFF 또는 POST_NOTIFICATIONS 거부 | 홈 진입, 권한 부여 후 복귀 | 누락 시 "권한 필요"+설정 버튼; 모두 부여·리스너 bound일 때만 "감시 중"; onResume 재평가 | BLOCK | PR #6 본문 "권한 필요" |
| BA-14 | (PR #6) 찾을 말 편집 | PR #6 빌드 | 키워드 추가/수정/삭제/끄기, 빈·중복 입력 | 저장 반영; 빈·중복 거부; 파일 없음/손상 시 번개·선착순 시드; 빈 목록은 빈 채로 | WARN | PR #6 본문 "찾을 말" |
| BA-15 | (PR #7) 업데이트 알림 1회 | PR #7 빌드, 더 큰 versionCode 릴리스 | 앱 시작/24h 체크 | 업데이트 알림 1회, 같은 코드 재알림 없음; 404/401은 크래시 없음 | WARN | PR #7 본문 |
| BA-16 | Grok 웹훅 + 내장 ntfy 구독 경로 (별도 ntfy 앱 불필요) — **added 2026-11-05 at 캡틴 request** | Grok 웹훅 켬(주소·선택적 인증 헤더 설정), ntfy 토픽 설정, 별도 ntfy 앱 미설치, 경계 점수 글 | 경계 글 게시 → 웹훅 POST → ntfy 답 수신; 무응답/잘못된 requestId/끊김/서비스 종료/로그 점검 (아래 케이스 a–f) | 아래 'BA-16 기대 결과' 참조 | WARN | DESIGN.md:62, 78; NtfySubscribeService.kt; Judges.kt GrokWebhookJudge; VerdictParser.kt NtfyEvents |

## BA-16 기대 결과 (added 2026-11-05 at 캡틴 요청; 실행 전에 DESIGN.md 62/78행과 소스에서 작성, 실행 후 수정 금지)
출처: DESIGN.md 62행("Grok Bot 웹훅: 사용자가 넣은 주소로 JSON을 POST하고, 답은 사용자가 고른 ntfy 토픽으로 받는다"), 78행(요청 본문 형식 이후 문단: 토픽·서버 기본값, `{서버}/{토픽}/json` 스트림, `requestId`로 맞춤, 끊기면 잠시 쉬었다가 재접속, 포그라운드 서비스가 구독 유지, WorkManager 15분마다 서비스 점검, 제한 시간 기본 45초 후 점수로 복귀). 테스트용 웹훅·ntfy 답은 **시뮬레이션(SIMULATED, 박스의 모의 웹훅)**이며 실제 Grok Bot이 아니다.
- (a) 별도 ntfy 앱이 설치되어 있지 않아도, Grok 웹훅 판정이 켜져 있고 주소가 있으면 앱의 `NtfySubscribeService`(포그라운드, 알림 id 42 채널 bungae_ntfy)가 실행되어 `{ntfyServer}/{토픽}/json`을 구독한다.
- (b) 경계 점수 글 → 설정된 주소로 JSON POST: 필드 requestId(UUID), chatName, sender, text, postTypes, score, replyTopic, replyServer. 인증 헤더 이름·값이 설정돼 있으면 그 헤더가 요청에 포함된다. 모의 웹훅이 ntfy 토픽에 같은 requestId의 판정 JSON(`isMatch`/`type`/`confidence`/`reason`/`requestId`)을 올리면 앱이 그 판정을 받아 사용한다(요약에 "보조판정 grok_webhook 일치/아님" 표시, isMatch=true+type이면 알림). 판정은 제한 시간(45초) 안에 도착한다.
- (c) 모의 웹훅이 답하지 않으면 제한 시간(기본 45초, 판정 타임아웃은 1~120초로 제한) 후 점수로 복귀: score ≥ cutoff면 알림, 요약에 "보조판정 없음, 점수와 기준 비교". 앱 크래시 없음.
- (d) requestId가 다르거나 없는 답은 대기 중인 요청에 전달되지 않아 무시된다(해당 요청은 (c)처럼 점수로 복귀).
- (e) 네트워크가 약 30초 끊겼다가 복구되면 구독이 다시 붙어(백오프 1초→최대 60초) 이후 답을 받는다. 서비스를 강제 종료하면 WorkManager 감시(15분 주기) 또는 다음 판정(`NtfySubscribeService.start`)이 서비스를 다시 시작한다 — 관찰 기록.
- (f) 웹훅 주소, 인증 헤더 값, ntfy 토픽이 logcat에 평문으로 나타나지 않는다(DESIGN "사생활" 취지; 소스는 로그를 남기지 않는 것으로 보임). 나타나면 위반으로 기록한다.
- 합격: (a)–(d), (f) 모두 충족하고 (e)는 재접속 관찰이 기대와 같을 때 PASS. 하나라도 어긋나면 FAIL(WARN).

## 오픈 질문 (캡틴 결정 필요)
1. [결정 2026-11-05 캡틴] BA-13~15 지금 기준에 포함. 판정 APK는 가장 최신 빌드.
2. 기본 민감도·band 값(cutoff 35~80 범위)을 확인하지 못함 — BA-05 경계값 기준 확정 필요.
3. 기준 APK: debug / pr6-debug / pr6b-debug 중 어느 것이 정본인가? pr6b의 차이는?
4. PR #7 서명: 폰 설치본 인증서(7b49…796d)와 저장소 debug.keystore(bdae…c8ca) 불일치 — 업데이트 설치 가능 여부를 BLOCK로 둘지?
5. "알림 누르면 해당 방으로 이동"은 '가능하면'으로 적혀 있음 — 실패 허용 범위?
6. Galaxy S25 Ultra 실기기 검증을 필수로 할지(전체화면 인텐트·배터리 제한)?
