-- ============================================================
--  [dashboard-v1-test-deploy.js 전용] 인덱스 임시 제거
--
--  실행 시점: dashboard-v3용 더미데이터(dashboard-v3-dummy-data.sql 등) 적재를
--            이미 마친 "직후", V1 테스트를 실행하기 "직전"에 딱 한 번 실행
--
--  목적: V1은 "인덱스 미적용 상태에서 Full Table Scan이 발생하는가"가 검증 대상이므로,
--        더미데이터 적재 스크립트가 마지막에 인덱스를 복구해둔 상태 그대로 V1을 돌리면
--        원래 의도(인덱스 부재)가 재현되지 않음 → 반드시 이 스크립트로 한 번 더 내려야 함
--
--  주의: PK/UNIQUE 제약은 여기서 건드리지 않음 (보조 인덱스만 제거)
-- ============================================================

ALTER TABLE users   DROP INDEX idx_user_role_deleted;
ALTER TABLE users   DROP INDEX idx_user_created_at;
ALTER TABLE novels  DROP INDEX idx_novel_genre;
ALTER TABLE novels  DROP INDEX idx_novel_status;
ALTER TABLE novels  DROP INDEX idx_novel_status_deleted;
ALTER TABLE novels  DROP INDEX idx_novel_new_list;
ALTER TABLE mentors DROP INDEX idx_mentor_status;
ALTER TABLE mentors DROP INDEX idx_mentor_created_at;

SELECT '===== 인덱스 제거 완료 - 이제 dashboard-v1-test-deploy.js 실행 =====' AS result;