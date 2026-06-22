UPDATE approval_line_config
   SET label = '출고자',
       modified_at = now(),
       modified_by = 'v65-seed'
 WHERE document_type = 'SLIP_OUTBOUND'
   AND created_by = 'v61-seed'
   AND label = '출고인'
   AND is_deleted = FALSE;

UPDATE approval_line_config
   SET label = '검수자',
       modified_at = now(),
       modified_by = 'v65-seed'
 WHERE document_type = 'SLIP_OUTBOUND'
   AND created_by = 'v61-seed'
   AND label = '검수인'
   AND is_deleted = FALSE;
