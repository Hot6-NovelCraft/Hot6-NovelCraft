-- ============================================================
--  [dashboard-v2/v3-test-deploy.js 전용] 인덱스 복구
--
--  실행 시점: V1 테스트(dashboard-v1-test-deploy.js)가 끝난 직후,
--            V2 테스트를 실행하기 "직전"에 딱 한 번 실행
--
--  목적: V2/V3는 "인덱스 적용 상태"가 전제 조건이므로,
--        index-drop-before-v1.sql로 내려둔 인덱스를 다시 올려야 함
--
--  주의: 원본 더미데이터 스크립트(dashboard-v3-dummy-data.sql) 섹션 6과
--        완전히 동일한 인덱스 정의를 사용함 (컬럼 순서까지 동일하게 맞춰야
--        옵티마이저가 같은 실행계획을 선택함 - V2/V3 결과가 로컬 결과와 비교 가능해짐)
-- ============================================================

ALTER TABLE users  ADD INDEX idx_user_role_deleted (is_deleted, role);
ALTER TABLE users  ADD INDEX idx_user_created_at (created_at);
ALTER TABLE novels ADD INDEX idx_novel_genre (genre);
ALTER TABLE novels ADD INDEX idx_novel_status (status);
ALTER TABLE novels ADD INDEX idx_novel_status_deleted (is_deleted, status);
ALTER TABLE novels ADD INDEX idx_novel_new_list (is_deleted, status, genre, created_at);
ALTER TABLE mentors ADD INDEX idx_mentor_status (status);
ALTER TABLE mentors ADD INDEX idx_mentor_created_at (created_at);

SELECT '===== 인덱스 복구 완료 - 이제 dashboard-v2 → dashboard-v3 순서로 실행 =====' AS result;