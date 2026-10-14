-- F-06 프로세스 변경: 적정성 점검은 감사부서가 제출된 분기 점검 내역을 항목별로 검토하는 방식이다.
-- 보완자료를 요청하지 않고 바로 판정(적정·미흡 확정)하는 행이 생기므로 요청자료를 비울 수 있게 한다.
-- (DROP NOT NULL 은 H2·PostgreSQL 문법이다. Oracle 로 옮길 때는 MODIFY request_material NULL 로 바꾼다.)
ALTER TABLE audit_request ALTER COLUMN request_material DROP NOT NULL;
