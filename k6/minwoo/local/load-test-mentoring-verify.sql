-- ============================================
-- 멘토링 멘티 수락 동시성 테스트 후 정합성 검증
-- ============================================

-- 1. 멘토십 상태별 건수 (ACCEPTED가 3이어야 정합성 통과)
SELECT status, COUNT(*) AS cnt
FROM mentorships
WHERE mentor_id = (SELECT id FROM mentors WHERE user_id = (SELECT id FROM users WHERE email = 'mentor_loadtest@test.com'))
GROUP BY status;

-- 2. 멘토 슬롯 확인 (maxMentees가 0이어야 정상)
SELECT max_mentees, version
FROM mentors
WHERE user_id = (SELECT id FROM users WHERE email = 'mentor_loadtest@test.com');

-- 3. 정합성 요약
SELECT
    (SELECT COUNT(*) FROM mentorships
     WHERE mentor_id = (SELECT id FROM mentors WHERE user_id = (SELECT id FROM users WHERE email = 'mentor_loadtest@test.com'))
       AND status = 'ACCEPTED') AS accepted_count,
    (SELECT max_mentees FROM mentors
     WHERE user_id = (SELECT id FROM users WHERE email = 'mentor_loadtest@test.com')) AS remaining_slots,
    CASE
        WHEN (SELECT COUNT(*) FROM mentorships
              WHERE mentor_id = (SELECT id FROM mentors WHERE user_id = (SELECT id FROM users WHERE email = 'mentor_loadtest@test.com'))
                AND status = 'ACCEPTED') = 3
            THEN '✅ PASS - 정확히 3명만 수락됨'
        ELSE '⚠️ DB 직접 확인 필요'
        END AS result;


-- ============================================
-- 테스트 데이터 정리 SQL (테스트 완료 후 실행)
-- ============================================
-- DELETE FROM mentorships WHERE mentor_id = (SELECT id FROM mentors WHERE user_id = (SELECT id FROM users WHERE email = 'mentor_loadtest@test.com'));
-- DELETE FROM mentors WHERE user_id = (SELECT id FROM users WHERE email = 'mentor_loadtest@test.com');
-- DELETE FROM novels WHERE title = '[부하테스트] 테스트 소설';
-- DELETE FROM users WHERE email = 'mentor_loadtest@test.com';