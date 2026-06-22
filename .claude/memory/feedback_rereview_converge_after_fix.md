# 🚨 fix 후 0-수렴 재리뷰 의무 (CI-green 머지 금지) — 반복 위반 박제

> 2026-06-22 개발책임자 강력 지적("자꾸 반복되는데 분명하게 박제"). 슬3 #562 회고.

**규칙**: 듀얼리뷰에서 **어떤 fix든**(🔵Opus 라운드 fix · 🟣Codex 라운드 fix · CI 실패 fix · 리뷰 후 임의 fix) 적용하면, **그 fix를 포함한 최종 상태를 다시 순차 듀얼리뷰로 0-blocking/0-skip 수렴 확인한 뒤에만 머지**한다.

**왜**: fix 자체가 새 결함·회귀·미검토 델타를 만든다. fix가 발생한 라운드의 **다음 라운드가 그 fix를 검토**해야 하고, **마지막 fix 이후 Opus·Codex 양쪽이 새 fix 없이 0을 반환할 때까지** 반복(에러 0까지 무제한, [[temp-multimodel-workflow]]·[[review-posting-and-zero-skip]]).

**절대 금지**:
- **CI-green 만으로 머지** — CI 통과는 수렴 재리뷰를 대체하지 않는다. CI는 빌드/테스트만 보고 리뷰 차원(설계·회귀·계약)을 못 본다.
- **마지막 fix가 어느 모델 재리뷰도 안 거친 채 머지** — 슬3 위반: Codex 라운드가 추가한 route 계약 IT + CI 실패 fix(401→403)가 재리뷰 없이 CI-green 만으로 머지됨(사후 재리뷰로 0 확인했으나 **순서 위반**).

**올바른 순서** (예):
1. Opus 5-agent → fix A → 게시
2. Codex 5-agent(A 검토) → fix B(신규) → 게시
3. **fix B 미검토 → Opus 재라운드(B 검토) → 0 게시 → Codex 재라운드 → 0 게시** (양쪽 새 fix 없이 0)
4. CI 실패 fix C 발생 시 → **다시 3 반복**(C 포함 0-수렴)
5. **양쪽 0 수렴 확정 후** 머지

**자가 점검(머지 직전 필수 mental check)**: "마지막 커밋(fix)이 Opus·Codex 양쪽 재리뷰를 거쳐 0-blocking 확인됐나? CI-green 만으로 넘어가려는 건 아닌가?" — 아니오면 **재리뷰부터**.

관련: [[review-posting-and-zero-skip]] · [[temp-multimodel-workflow]] · [[dual-5agent-review]] · [[no-backlog-strict]]
