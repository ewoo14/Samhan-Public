# D-AX-21 도메인 정합성 체크

## 번호 범위

| 도메인 | 공개번호 | 범위 | 중복 허용 |
|---|---|---|---|
| 판매전표 | `yyyy/MM/dd-N` | `slip_type=OUTBOUND` + 날짜 | 구매전표와 중복 허용 |
| 구매전표 | `yyyy/MM/dd-N` | `slip_type=INBOUND` + 날짜 | 판매전표와 중복 허용 |
| 배차번호 | `yyyy/MM/dd-N` | 배차 메뉴 + 날짜 | 전표번호와 중복 허용 |

## DB 체크

```sql
-- slip_number_sequences 는 날짜 + 전표유형별 독립 row
SELECT slip_date, slip_type, last_seq
FROM slip_number_sequences
WHERE is_deleted = false
ORDER BY slip_date DESC, slip_type;

-- active slip 중복 체크: slip_type + slip_no 가 기준
SELECT slip_type, slip_no, COUNT(*)
FROM slips
WHERE is_deleted = false
GROUP BY slip_type, slip_no
HAVING COUNT(*) > 1;

-- 전역 slip_no 중복은 허용될 수 있으므로 경고가 아니다.
SELECT slip_no, COUNT(*)
FROM slips
WHERE is_deleted = false
GROUP BY slip_no
HAVING COUNT(*) > 1;
```

## 화면/API 가드

- 화면은 내부 UUID 대신 `slipNo`, `taskCode`, `partnerCode`, `partnerName`을 보여준다.
- `downloadUrl`, `attachmentId`, `slipId`, `dispatchId`, `vehicleId`, `stopId`는 기사 앱 read model에서 제거 상태를 유지한다.
- 공개 첨부 조회처럼 업무 context가 필요한 경로는 `SlipType.OUTBOUND`를 명시해 `slip_no` 단독 충돌을 피한다.
