-- F-05 총괄표 검토·감사부서 제출
ALTER TABLE round_item ADD review_comment VARCHAR(1000);
ALTER TABLE round_item ADD reviewed_by BIGINT;
ALTER TABLE round_item ADD reviewed_at TIMESTAMP;

ALTER TABLE audit_round ADD submitted_by BIGINT;
ALTER TABLE audit_round ADD submitted_at TIMESTAMP;
