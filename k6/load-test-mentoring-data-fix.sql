-- ============================================
-- 멘토링 멘티 수락 동시성 테스트용 데이터 (한번에 실행)
-- 멘토 유저 ID: 208, 멘토 ID: 1 (이미 생성됨)
-- ============================================

-- 1. 테스트용 소설 생성
INSERT INTO novels (author_id, title, description, genre, tags, status, view_count, is_deleted, updated_at, bookmark_count, created_at)
VALUES (208, '[부하테스트] 테스트 소설', '부하 테스트용 소설입니다', '판타지', '테스트', 'ONGOING', 0, false, NOW(), 0, NOW());

-- 2. PENDING 멘토십 10개 생성 (loadtest1~10 유저 활용)
INSERT INTO mentorships (mentor_id, mentee_id, current_novel_id, motivation, manuscript_url, total_sessions, status, manuscript_download_count, created_at)
SELECT 1, u.id, n.id, CONCAT('멘토링을 받고 싶습니다 - ', u.nickname), NULL, 0, 'PENDING', 0, NOW()
FROM users u
         CROSS JOIN (SELECT id FROM novels WHERE title = '[부하테스트] 테스트 소설' LIMIT 1) n
WHERE u.email LIKE 'loadtest%@test.com'
ORDER BY u.id
    LIMIT 10;

-- 3. 확인
SELECT id, mentor_id, mentee_id, status FROM mentorships WHERE mentor_id = 1 ORDER BY id;
SELECT max_mentees, version FROM mentors WHERE id = 1;
