---
name: qa-processes-disrupt-user-gaming
description: 라이브QA 가 남긴 브라우저·Electron 프로세스가 사용자 전체화면 게임을 튕겨낸다 — headless 기본 + 라운드 끝 회수 + BelowNormal
metadata: 
  node_type: memory
  type: feedback
  originSessionId: c13996f5-fe35-4d8e-ae50-ba7df3c72afa
  modified: 2026-08-06T23:32:28.689Z
---

# 🚨 내 QA 프로세스가 사용자의 게임 화면을 꺼뜨린다 (2026-08-07 개발책임자 항의)

개발책임자: *"아 진짜 자꾸 너때매 롤 화면이 꺼지잖아. 왜 자꾸 중요한 부분에서 그러는데"*

같은 기계에서 개발책임자가 **리그 오브 레전드를 전체화면으로** 하고 있었고,
내 야간 자율 라운드가 창을 띄우고 CPU 를 먹어 게임이 튕겼다. **실측 잔재:**

```text
chrome (Playwright)      31개   1,895 MB   ← 전부 내 것. 사용자 브라우저는 Whale 이라 안 섞임
chrome-headless-shell    19개   2,143 MB
electron (t1013b)         4개     424 MB   ← 새벽 3:05 #1088 아로로지스 QA. 라운드는 몇 시간 전 종료
node_repl                80개   1,628 MB   ← 78개가 2시간 넘은 codex 잔재
java (Gradle 데몬)        6개   3,661 MB   ← 13.5시간·3.75시간 묵은 것 포함
                                 ≈ 14 GB
```

## 🔑 두 가지가 겹친다 — 하나만 고치면 안 된다

1. **창 탈취** — headed chromium·Electron 이 뜨면 전체화면 게임이 alt-tab 아웃된다.
   순간이라 "화면이 꺼졌다" 로 느껴진다.
2. **자원 고갈** — 14 GB + Gradle 컴파일 + 컨테이너 스택이면 프레임이 무너진다.

## 상시 규칙

```text
① Playwright 는 headless: true 가 기본이다
   🔑 headless 캡처도 실 앱이 실 서버를 때린 진짜 스크린샷이다 —
      [[no-fake-data-ever]] 위반이 아니다. 그 규칙은 합성·fixture 금지이지 창 표시 의무가 아니다.
② Electron QA 는 headless 가 안 된다 ⟹ 개발책임자가 게임/작업 중이면 그 시간대에 잡지 않는다
③ 라운드 끝나면 브라우저·Electron 을 반드시 회수한다 (끝났다고 죽지 않는다 — 실측 4시간 생존)
④ 내 장기 프로세스는 BelowNormal 로 강등해 둔다 (48개 강등으로 즉시 완화됨)
⑤ 묵은 Gradle 데몬은 주기적으로 정리 — 유휴여도 RAM 을 쥔다
```

## 판별 명령

```powershell
# 창을 가진 프로세스 (게임 위로 뜨는 놈 찾기)
Get-Process | ? { $_.MainWindowTitle -ne '' } | Select Name, MainWindowTitle

# 내 chrome 인지 사용자 브라우저인지
Get-CimInstance Win32_Process -Filter "Name='chrome.exe'" |
  ? { $_.CommandLine -match 'playwright|remote-debugging-port|--headless' }

# 잔재 나이
Get-CimInstance Win32_Process -Filter "Name='node_repl.exe'" |
  % { [int]((Get-Date)-$_.CreationDate).TotalHours }
```

🚫 **사용자 프로세스를 휩쓸지 말 것** — 죽이기 전에 커맨드라인으로 내 것임을 확정한다.
이번엔 chrome 31개가 전부 Playwright 였고 개발책임자 브라우저는 Whale 이라 안전했지만,
확인 없이 `Stop-Process -Name chrome` 을 했다면 작업 중인 탭을 다 날렸다.

관련: [[live-qa-every-round-screenshots]] · [[qa-environment-verification-first]]
