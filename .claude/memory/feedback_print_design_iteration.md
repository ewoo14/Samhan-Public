---
name: 인쇄 양식 디자인 반복 정정 패턴
description: 작업지시서/거래명세서 같은 인쇄 양식은 사용자 첨부 이미지 → mock → 캡처 → 미세 조정 3~4회 iteration 가정. 단번에 완성 가정 금지
type: feedback
originSessionId: 78cac99d-5dee-47ca-8254-3834a088f393
---
**규칙**: 인쇄 양식 (작업지시서 / 거래명세서 / 영수증 등) 디자인 작업 시 다음 프로세스 강제 적용.

1. **사용자 첨부 이미지 1차 충실 반영**: 사용자가 첨부한 양식 이미지 (스크린샷/PDF) 가 있으면 layout / 컬럼 수 / 헤더 위치 / 컬러 등을 일대일 mock 으로 작성
2. **Edge headless 캡처로 즉시 시연**: HTML mock + Edge headless 로 빠르게 캡처 → PR body 에 첨부
3. **미세 조정 iteration**: 사용자가 추가 피드백을 주면 1회당 최대 5~10건의 CSS-only tweak 가정. 절대 단번에 완성될 거라 가정하지 말 것

**Why**: 2026-05-04 PR #21 (sales-polish-2-slice) 에서 발생.
- 1차 (Slice A): 8건 디자인 피드백 반영 (Designer wireframe 기반)
- 2차 (hotfix v1): 추가 10건 디자인 피드백 (월/일 컬럼 / 결재칸 / 색상 등)
- 3차 (hotfix v2): 사용자 이미지 첨부 → 큰 재디자인 (4-col, 새 레이아웃)
- 4차 (hotfix v3): 로고 작게 / 분리 / 세로선 추가
- 5차 (hotfix v3.1): 표 선 검은색 / 데이터 가운데 정렬 / 결재란 높이 매칭

**총 5회 iteration, 8 hotfix commits** — 단번에 완성 가정했다면 사용자 만족도 저하.

**적용 절차**:

```bash
# 1. 사용자 첨부 이미지 분석 (layout / 컬럼 / 색상 / 폰트)
# 2. 자체 HTML mock 작성 (실제 global.css 와 동기화 — class 이름 일치)
docs/qa/<slice-slug>/mocks/<seq>_<view>_v<n>.html

# 3. Edge headless 캡처
"/c/Program Files (x86)/Microsoft/Edge/Application/msedge.exe" --headless=new \
  --disable-gpu --hide-scrollbars --window-size=900,1300 \
  --screenshot=<absolute path>.png "file:///<absolute mock path>.html"

# 4. PR body 에 raw URL 첨부 (commit-pinned)
# 5. 사용자 회신 받기 → CSS-only tweak (JSX 변경 최소화)
# 6. mock 동기화 → 재캡처 → push → PR body 갱신
```

**캡처 파일명 규약**: `<숫자>_<view>_<버전 또는 키워드>.html` / `.png`. 예:
- `09_dispatch_hotfix_10feedback.html` (1차 피드백 반영)
- `10_dispatch_hotfix_v2.html` (큰 재디자인)
- `12_dispatch_hotfix_v3.html` (미세 조정)

**PR body 캡처 관리**: hotfix 가 5회 이상 누적되면 **최종 버전만 PR body 에 유지**. 중간 hotfix 는 commit 메시지로 충분. 너무 많은 캡처는 리뷰 노이즈 발생.

**JSX vs CSS 분리 원칙**: 디자인 iteration 의 90%는 CSS-only tweak. JSX 변경은 컬럼 수가 바뀌거나 신규 섹션 추가 시만. JSX 변경 없는 CSS 정정은 컴파일 위험 0 → 빠른 iteration 가능.

**관련 메모리**: `feedback_pr_qa_screenshots.md` (PR QA 스크린샷 의무), `feedback_multi_agent_team_pattern.md` (Designer agent 역할).

## 관련 가드

- 디자인 iteration 후 통합 PR 1개로 발행 (`feedback_integrated_pr_pattern.md`) — 단편 hotfix 누적이 아닌 차이 전수 누적 후 통합 PR 발행 (PR #66 회고)
