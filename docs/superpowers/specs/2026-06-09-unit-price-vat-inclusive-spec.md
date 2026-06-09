# 단가 부가세포함 전환 spec (2026-06-09 개발책임자 확정)

## 업무 규칙 (확정)
- **단가 = 무조건 부가세 포함 단가.** 사용자는 VAT포함 단가를 입력한다.
- 단가에서 **라인 단위(eCount 방식)** 로 공급가액/부가세를 분리:
  - 라인 **합계(VAT포함)** = `단가 × 수량`
  - 라인 **공급가액** = `round(단가 × 수량 ÷ 1.1)`
  - 라인 **부가세** = `합계(VAT포함) − 공급가액`
- 단가 입력란 옆에 해당 라인의 **공급가액·부가세·합계(VAT포함)** 를 즉시 표시.
- 전표 합계 = 라인별 공급가액 합 / 부가세 합 / 총합(VAT포함) 합.
- **BE 에 라인 공급가액 직접 전송**(정합 보장) — per-unit 재계산으로 인한 ±1 drift 방지.

## 적용 범위 (확정)
전표 **전체**: 출고전표 + 입고전표(SlipFormPage 공용) + 판매/구매 조회(상세/리스트) + 견적서(작성/상세).

## BE 현황 / 변경
- `SlipLine` 이미 보유: `unitPrice`(공급 단가 VAT-excl), `unitPriceWithVat`(=unitPrice×1.1), `supplyAmount`(=unitPrice×qty). → 현재 supplyAmount 가 per-unit 파생.
- **변경 필요**: 라인 공급가액을 **권위값으로 수신·저장**(per-line round). `CreateSlipRequest.SlipLineRequest` + `EstimateLineRequest` 에 라인 단위 입력 의미 재정의(또는 supplyAmount/unitPriceWithVat 직접 수신). EstimateLine 동일.
  - 옵션: 요청에 `unitPriceWithVat`(VAT포함 단가) 추가 → BE 가 라인 공급가액=round(단가×qty/1.1), 부가세 파생, supplyAmount/unitPrice 역산 저장. (FE 가 VAT포함 단가를 보내고 BE 가 분해 — 단일 진실원.)

## 다단계 구현 계획 (multi-PR)
1. **PR-A (BE 계약 + 출고/입고 전표 create)**: 요청에 unitPriceWithVat 수신 → 라인 공급가액/부가세/supplyAmount 라인단위 산출·저장. SlipFormPage 단가=VAT포함 입력 + 라인별 공급가액/부가세/합계 표시 + 합계. 세트 전개 단가도 VAT포함 base. IT + 실 UI QA.
2. **PR-B (조회/상세 표시)**: 판매/구매 조회 리스트·상세에서 단가=VAT포함(unitPriceWithVat) 표시 + 공급가액/부가세 분해. 실 UI QA.
3. **PR-C (견적서)**: EstimateFormPage/상세 동일 모델. 견적→전표 변환 시 단가 전파 정합. IT + 실 UI QA.

## 세트 전개와의 관계
- 세트 단가(VAT포함) → 구성품 재배분도 VAT포함 base 로 일관. expand 의 setUnitOverride 에 VAT포함/VAT-excl 일관 결정 필요(PR-A 에서 확정).

## 검증
- 각 PR: 변경 모듈 IT + **실 Docker 스택 + 데스크톱 실 UI 캡처**([[feedback_real_server_check_screenshot]]). 라인 단위 반올림 수치(공급가액·부가세) 실 화면 단언.
- dual 리뷰(Codex 다운 시 독립 Claude 대체), CI green.

## 미해결/확인
- 라인 공급가액 권위 저장 방식: 요청에 unitPriceWithVat 추가(권장) vs supplyAmount 직접 — PR-A 착수 시 BE 구조 확인 후 확정.
- 세트 구성품 규격 자동채움(product_spec)은 별도 트랙(연관: ProductSpec flapping reconcile).
