# Dev Report — 배차 보드 개편 + 전역 협업 문서 플랫폼 에픽 (#463)

> 2026-06-11 개발책임자 야간 자율 위임: **#463 에 배차 관련 내역 전부 + 전 전표/화면 협업 플랫폼 기술 슬라이스까지 모두 적용**. 한 사이클만 돌고 머지 금지 — **PM 은 리뷰 error=0 AND skip=0 도달 후에만 머지**. 본 문서 = 실행 체크리스트 + 진행 누적(아침 보고용).

## 스코프 (2 spec)
- 배차 보드 고도화: `docs/superpowers/specs/2026-06-11-dispatch-board-enhancement-spec.md`
- 전역 협업 플랫폼: `docs/superpowers/specs/2026-06-11-collab-document-platform-spec.md`

## 진행 현황 (커밋 누적)
| # | 청크 | 상태 | 커밋 |
|---|---|---|---|
| 0a | E0 보드 실연동(게이트웨이+envelope) | ✅ | 3cec1acd |
| 0b | 완료배차 뷰 → R1 fix(DISPATCHED 한정) | ✅ | 6acf3812 |
| 0c | R2 fix(real-qa 강화 + 슬립→전표) | ✅ | 757be335 |
| 0d | **메뉴 IA**(배차현황 등 3 rename + 배차 메뉴·수동 배차 삭제) | ✅ | e4bdee6b |
| 0e | 배차현황 실 QA 재캡처 + 협업 spec | ✅ | 22a014f9 |
| 1 | **collab-core C0**(공유 협업 서브시스템 골격) | ⏳ 정찰 중 | |
| 2 | **dispatch C1**(레퍼런스: 수정이력/회귀/실시간 = E3/E5/E4) | ⏳ | |
| 3 | **§2-1 2-pane 보드 + 차량 캡슐**(좌 전표풀/우 캡슐·드래그·전표번호·중복 적색·GAS) | ⏳ | |
| 4 | **2축 차량모델 E1**(차종12+톤수10, additive, arologis 호환) | ⏳ | |
| 5 | **배차현황 enrich**(차량번호 + 배차안내 SMS feed) | ⏳ | |
| 6 | **다중 vendor E2**(아로로지스/경기퀵/전국화물 + 체크박스 일괄전송) | ⏳ | |
| 7 | **전역 롤아웃** C2 입출고전표 · C3 회계전표 · C4 주문/견적 | ⏳ | |
| 8 | 다모델 리뷰 사이클 → 0 error/0 skip → Docker 실QA → 머지 | ⏳ | |

## 리뷰 (다모델, [[temp-multimodel-workflow]])
- R1 Opus 5-agent ✅ 게시(P1 → 6acf3812). R2 Codex 5-agent ✅ 게시(P2 → 757be335). R3 Fable5 + 최종 종합 = 배차 청크 누적 후.

## 🚩 개발책임자 아침 검토 플래그 (야간엔 PM 기본값으로 진행)
1. **2축 톤수 매핑**: 소형(오토바이/승용차/다마스/라보)=톤수 없음, 그 외=1~25톤 전체. 기본 카고/1톤. config 맵 분리. (dispatch spec §1)
2. **arologis 차량 enum 호환**: `DispatchVehicleType`(9값)이 arologis-service 와 enum-name 와이어 공유 → 2축 신톤수(1.2/1.4/11/14/18/25)는 legacy 와이어에 **무손실 표현 불가**. PM 야간 기본값 = **additive(slip 측 차종+톤수 신규 필드 추가, legacy vehicleType 은 톤수→nearest legacy 파생으로 arologis 무변경 유지)**. 아침 결정: arologis VehicleTonnage 도 신 모델로 확장 vs lossy 호환 유지.
3. **collab 테이블 배치**: 각 서비스 DB 스키마(라이브러리 Flyway) = PM 기본값(DB-per-service 유지). vs collab 전용 서비스.
4. **2-pane 보드 대상 화면**: 가배차리스트(/arologis/pre-classify)를 좌우 2-pane 로 재설계(PM 기본값 — 개발책임자 "좌측=가배차 진행 화면"). 기존 /dispatch-board 팔레트는 de-menu 됨.

## 원칙 준수
[[codex-implements-claude-reviews]](Codex 구현) · [[no-fake-data-ever]](실데이터/실QA) · [[qa-docker-real-test]](Docker 실서버) · [[enum-expansion-check-constraint]](CHECK 마이그) · [[jeonpyo-not-slip]](전표) · [[pr-qa-screenshots]](본문 인라인) · [[korean-commits]]. 머지 = 0 error/0 skip + CI green + Docker 실QA 후.
