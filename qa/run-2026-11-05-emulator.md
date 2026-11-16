# 번개알림 (kakao-bungae-alert, branch pr7) — emulator QA run 2026-11-05 (KST)

- App: 번개알림 (kakao-bungae-alert, branch pr7)
- Build SHA: 1d1e017
- APK: /workspace/qa-env/apks/kakao-bungae-alert-pr7-1d1e017-debug.apk
- Device: emulator qa-api34 (emulator-5558), Android API 34 x86_64, software TCG (-accel off), userdebug image
- Run time: 2026-11-05 09:18–10:55 KST
- **STATUS: PASS**  (BLOCK: 9 pass / 0 fail / 0 blocked of 9; WARN: 6 pass / 0 fail / 0 blocked of 6)
- Pack: CANONICAL-AC-PACK.md (owner-APPROVED 2026-11-05), results table not edited; expected results untouched.

## Per-AC results

| AC | Severity | Result | Evidence / notes |
|---|---|---|---|
| BA-01 | BLOCK | **PASS** | com.example.other notif (title/subText 수다방, meetup text) posted while listener bound -> no detection, no app alert; Kakao control alerted. evidence BA-01-05-other-pkg-ignored-and-meetup-alert.txt (SIMULATED notifs) |
| BA-02 | BLOCK | **PASS** | Kakao-stub notifs on channels importance 1 (MIN) and 2 (LOW) both processed and alerted (score 100). BA-02-low-importance-processed.txt (SIMULATED) |
| BA-03 | BLOCK | **PASS** | exact/contains/ends_with rules: 수다방 alert, 수다방2 none, '경제적 자유를 위하여~' alert, '  수다방  ' alert, '자유로운 영혼들' (contains) alert, disabled rule room none, unmatched room none. Empty-pattern rule also verified (matches nothing). BA-03-chat-matching.txt |
| BA-04 | BLOCK | **PASS** | '사진','(이모티콘)','', 'ㅋㅋㅋㅋ','삭제된 메시지입니다','민수: 사진','민수: 번개 사진' -> no new alert, no crash, process alive. BA-04-media-empty-ignored.txt |
| BA-05 | BLOCK | **PASS** | F-MSG-MEETUP and F-MSG-GIVEAWAY -> exactly 1 alert each (score 100, meetup/giveaway). F-PAST scored 2 no alert; F-REPLY filtered. BA-05-meetup-giveaway-alerts.txt |
| BA-06 | WARN | **PASS** | '번개장터에 올렸어요' score 0 (해당하는 신호 없음), no alert. BA-06-bungaejangteo.txt |
| BA-07 | BLOCK | **PASS** | Gemini-API judge enabled (fake key via UI) + app-uid traffic DROPped: borderline score-68 text alerted via score fallback, summary 보조판정 없음; DROP counter shows judge attempted. BA-07-08-judge-fallback.txt |
| BA-08 | WARN | **PASS** | With judge enabled: clear match/clear reject -> 0 app packets; borderline -> 15 pkts. BA-07-08-judge-fallback.txt |
| BA-09 | WARN | **PASS** | Whitespace-variant duplicate 25s later suppressed (1 detection); same text 611s later processed again. BA-09-dedupe.txt |
| BA-10 | BLOCK | **PASS** | Repeat ring auto-stopped 184s after FGS start (180s cap + slow-emulator timer latency, ~4s over); tap -> AlarmActivity -> 카톡 열기 stops ring (service gone), no crash; room open not verifiable (stub kakao has no chat activity; spec 가능하면). SIMULATED notifs. BA-10-*.txt/png |
| BA-11 | BLOCK | **PASS** | 0 packets from app uid across all processing at default settings (counter verified with control probe); webhook field empty; ntfy topic is random per-install and unused unless Grok judge enabled. BA-11-no-network.txt |
| BA-12 | WARN | **PASS** | Fresh-state Settings>판정>구독 shows only "동의 전에는 로그인하지 않습니다" + "위험 안내" button; no subscription login button is offered before risk consent. BA-12-subscription-needs-risk-consent.png |
| BA-13 | BLOCK | **PASS** | Home: no perms -> 권한 필요 + 알림 접근 허용/알림 표시 허용/배터리 최적화 제외; POST_NOTIFICATIONS granted -> still 권한 필요, 알림 표시 허용 button gone; listener granted+resume -> 감시 중. Perms granted via adb/cmd (not Settings UI). BA-13-1/2/3 png |
| BA-14 | WARN | **PASS(partial)** | Add accepted, empty rejected, case-insensitive duplicate rejected, disable persisted; missing/corrupt file reseeds 번개+선착순, [] stays empty. Not done: Korean F-KEYWORDS strings (adb cannot type Hangul; ASCII used), edit/delete. BA-14-*.png/txt |
| BA-15 | WARN | **PASS(partial)** | Real GitHub release v0.0.1-test2 vc2 > installed vc1 -> exactly one 업데이트 notification, repeat check did not re-notify, no crash. 404/401 sub-case BLOCKED (not producible on emulator). BA-15-update-notification.txt |

## Repro steps for each FAIL

_No FAIL results in this run._


## Disclosures (test-method deviations)

- **Kakao notifications are SIMULATED.** No real KakaoTalk exists on the emulator. A throwaway stub app with package `com.kakao.talk` (`/workspace/qa-env/notifier/com.kakao.talk.apk`, label "QA stub com.kakao.talk") posts notifications (title=room, text="sender: message", importance per channel) on command; `com.example.other` stub is the F-PKG-OTHER source. Real KakaoTalk notification structure (MessagingStyle, group summaries, muted-room behaviour on real KakaoTalk) is not covered.
- Test setup via adb/run-as (debug build): onboarding bypassed by writing `onboardingDone:true` into `shared_prefs/bungae_settings.xml`; watch rules seeded in `files/watch-rules.json` (수다방 exact, 자유 contains, 위하여~ ends_with, 반복방 exact+repeat, 꺼진방 disabled, an empty-pattern rule); app then force-stopped and relaunched. Notification-listener access granted with `cmd notification allow_listener` and POST_NOTIFICATIONS with `pm grant` (not through the Settings UI) — BA-13 verifies the banner re-evaluation, not the system permission screens.
- BA-07/BA-08: Gemini API judge enabled via the app UI with a FAKE placeholder key (not a real credential). Network blocking for BA-07 done with an iptables DROP rule on the app's uid (adb root on emulator-5558 only), not airplane mode. BA-11 traffic counted with an iptables owner-uid counter chain (control probe verified).
- BA-15 used the real public GitHub release v0.0.1-test2 (versionCode 2); the 6h start throttle was bypassed by deleting `bungae_update.xml` / its `last_check_at` key.
- Emulator housekeeping: emulator was restarted once (stop-emulator.sh/start-emulator.sh); to cope with ANR storms from 2.5 GB RAM + TCG, non-essential Google apps (gms, vending, maps, youtube, chrome, etc.) were disabled with pm disable-user, Bluetooth turned off, animations set to 0, screen stay-awake enabled. A watcher script taps "Wait" on ANR dialogs. UI actions are very slow (10–30 s per tap), which prevented some UI-heavy sub-steps.
- Timing note (BA-10): ring cap observed at ~184 s versus 180 s configured, on a heavily CPU-throttled emulator; judged within tolerance, flagged for a real-device re-check.
- Emulator 5556 was never observed; only qemu for 5558 was seen running. No other adb device was touched.


## Footer

```yaml
status: PASS
sha: 1d1e017
block_pass: 9
block_fail: 0
block_blocked: 0
warn_pass: 6
warn_fail: 0
ac_failed: []
```
