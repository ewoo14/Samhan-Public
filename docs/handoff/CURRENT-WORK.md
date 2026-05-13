# 현재 작업 핸드오프 노트

> 갱신일: 2026-05-13
> 갱신자: PM (Claude Opus 4.7) + 개발책임자 (ewoo14)
> 사용법: PC 이동 직전 갱신, 새 PC 에서 Claude 첫 세션 시작 시 이 파일 읽으면 즉시 컨텍스트 회복

---

## 1. 현재 진행 슬라이스

**이카운트 실 데이터 마이그레이션 (MIG 시리즈)** — dev 시드 + 향후 운영 cutover 대비

기능 이식은 이미 100% 완료 (e-Count 의존 0%, [legacy-gas-cross-check-2026-05-11.md](../dev-reports/legacy-gas-cross-check-2026-05-11.md) §6). 남은 작업은 **실 데이터 이동**.

---

## 2. 확정 결정사항 (2026-05-13)

| 항목 | 결정 |
|---|---|
| 마이그레이션 시점 | 지금 즉시 — dev 데이터로 활용 |
| 진행 순서 | 거래처 1개 PoC 먼저 → 검증 후 나머지 5종 |
| PII 마스킹 | 대표자 주민번호만 (B2B 공개정보는 원본) |
| 첨부파일 | Phase 분리 — dev 생략 / cutover D-7 상위 30~50개 수동 / 나머지 lazy |
| 백업 메뉴 (확정) | `Self-Customizing > 정보관리 > 데이터관리 > 백업 및 삭제` 의 **기초코드 탭** (마스터 1회) + **거래내역 탭** (3개월 split N회) |
| 슬라이스 묶음 | MIG-1 (거래처 PoC) → MIG-2 (마스터 5종 일괄) → MIG-3~6 (전표 묶음별) |
| 양 PC 메모리 sync | repo `.claude/memory/` commit + `scripts/sync-claude-memory.ps1` |
| 로컬 raw 데이터 | 각 PC 에서 재다운로드 (gitignore 유지) |

---

## 3. 작성 완료 산출물

| 파일 | 내용 |
|---|---|
| [docs/migration/ecount-data/README.md](../migration/ecount-data/README.md) | 마이그레이션 전체 가이드 (백업 메뉴 2탭 구조, 마스터 6종, 전표 9종, Phase 분리 첨부 전략) |
| [docs/migration/ecount-data/01-partner-mapping.md](../migration/ecount-data/01-partner-mapping.md) | 거래처 27 필드 매핑 + 검증 SQL |
| [docs/migration/ecount-data/raw/.gitkeep](../migration/ecount-data/raw/) | Excel 보관 위치 (실 데이터 .gitignore) |
| [.claude/memory/](../../.claude/memory/) | 34개 메모리 파일 commit (양 PC sync) |
| [scripts/sync-claude-memory.ps1](../../scripts/sync-claude-memory.ps1) | repo → 사용자 홈 메모리 단방향 복사 |
| [CLAUDE.md](../../CLAUDE.md) | Claude Code 진입점 (메모리 + 핸드오프 안내) |

---

## 4. 다음 단계 (개발책임자 행동 대기)

### 즉시 할 일

```
1. 이카운트 ERP 콘솔 로그인
2. Self-Customizing > 정보관리 > 데이터관리 > 백업 및 삭제
3. [기초코드 탭] 선택
4. "자료올리기형태로생성" 클릭
5. 이카운트 메신저로 알림 도착 대기
6. 메신저에서 Excel 다운로드
7. 파일 저장: c:\dev\SamhanLogis\docs\migration\ecount-data\raw\master-export-20260513.xlsx
8. PM (Claude) 에게 "받았어" 알려주세요
```

### PM 후속 자동 진행

파일 받으면 PM 이 즉시:
1. **MIG-1 BE Agent 디스패치** — `EcountPartnerImporter` (Apache POI + staging.ecount_partner_raw + Partner 도메인 적재 + 주민번호 마스킹)
2. **MIG-1 QA Agent 디스패치** — 검증 SQL (행 수, biz_no 중복, NULL 필수, 신용한도 합계, 주민번호 마스킹 적용)
3. **TM 검토** — UUID / cross-service 정합성
4. **MIG-2 일괄** — 같은 파일 다른 시트 (품목/계정/부서/창고/카드) → lookup map 자동 확보

### 후속 (마스터 완료 후)

- 트랜잭션 (거래내역 탭, 3개월 split) 받기 시작 → MIG-3 회계 / MIG-4 매출매입 / MIG-5 입출금 / MIG-6 재고

---

## 5. 미해결 / 사용자 확인 필요

- [ ] 이카운트 거래처 Excel 의 실제 헤더가 [01-partner-mapping.md](../migration/ecount-data/01-partner-mapping.md) 표와 일치하는지 (PoC 시 확인)
- [ ] 대표자 주민번호 컬럼 존재 여부 (없으면 마스킹 로직 skip)
- [ ] `partnerCode` = 이카운트 코드 그대로 vs 신규 발급 (default: 이카운트 코드 유지)
- [ ] 입출고/재고이동이 거래내역 탭 포함되는지 vs 별도 메뉴 (PoC 시점 확인)

---

## 6. 양 PC 작업 인계 순서

### 떠나는 PC (예: 집)

```powershell
# 1. 진행 상황을 이 파일에 갱신
# 2. .claude/memory/ 변경사항 commit
git add .claude/memory/ docs/handoff/
git commit -m "handoff: 이카운트 마이그레이션 진행 상황 (MIG-1 거래처 PoC 대기)"
git push
```

### 도착하는 PC (예: 회사)

```powershell
git pull
.\scripts\sync-claude-memory.ps1   # 메모리 사용자 홈으로 복사
# Claude Code 새 세션 시작 → CLAUDE.md 자동 로드 + 이 파일 (CURRENT-WORK.md) 읽기 요청
```
