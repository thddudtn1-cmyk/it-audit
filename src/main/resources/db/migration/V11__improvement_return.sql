-- 조치 결과가 '완료'로 바뀌면 주관자가 검토해 최종확정(closed_yn='Y')하거나 이전 단계로 되돌린다.
-- 되돌릴 때의 사유를 담당자에게 보여 주기 위해 마지막 반려 정보를 남긴다.
ALTER TABLE improvement ADD return_comment VARCHAR(1000);
ALTER TABLE improvement ADD returned_by BIGINT;
ALTER TABLE improvement ADD returned_at TIMESTAMP;
