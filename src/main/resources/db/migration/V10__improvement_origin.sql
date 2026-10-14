-- 개선사항 등록 경위(origin).
--   ROUND_SUBMIT  : 분기 점검을 감사부서에 제출할 때 점검결과가 정상이 아닌 항목이 자동 등록됨 (감사부서 검토와 무관)
--   AUDIT_FINDING : 감사부서가 미흡으로 확정한 건을 주관자가 등록함
--   RECOMMENDATION: 권고사항
ALTER TABLE improvement ADD origin VARCHAR(20) DEFAULT 'AUDIT_FINDING' NOT NULL;
UPDATE improvement SET origin = 'RECOMMENDATION' WHERE kind = 'RECOMMENDATION';
ALTER TABLE improvement ADD CONSTRAINT ck_improvement_origin
    CHECK (origin IN ('ROUND_SUBMIT', 'AUDIT_FINDING', 'RECOMMENDATION'));
