-- SP-03: 이동번호 공개 형식을 전표번호 표준과 동일하게 정리한다.
-- UUID PK와 transfer_no unique index가 내부/업무 구분을 담당하므로 화면 번호에는 T-/TR- prefix를 두지 않는다.

UPDATE stock_transfers
SET transfer_no = regexp_replace(transfer_no, '^T-', '')
WHERE transfer_no ~ '^T-[0-9]{4}/[0-9]{2}/[0-9]{2}-[0-9]+$';

UPDATE stock_transfers
SET transfer_no =
    substring(transfer_no from 4 for 4)
    || '/'
    || substring(transfer_no from 8 for 2)
    || '/'
    || substring(transfer_no from 10 for 2)
    || '-'
    || (substring(transfer_no from 13))::integer::text
WHERE transfer_no ~ '^TR-[0-9]{8}-[0-9]+$';
