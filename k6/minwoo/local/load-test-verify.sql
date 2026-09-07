-- ============================================
-- 부하 테스트 후 정합성 검증 SQL
-- ============================================

-- 1. 참여자 수 확인 (100명이어야 정합성 통과)
SELECT COUNT(*) AS actual_participants
FROM event_participants
WHERE event_id = (SELECT id FROM events WHERE title LIKE '%부하테스트%' LIMIT 1);

-- 2. 중복 참여 확인 (0이어야 정상)
SELECT user_id, COUNT(*) AS cnt
FROM event_participants
WHERE event_id = (SELECT id FROM events WHERE title LIKE '%부하테스트%' LIMIT 1)
GROUP BY user_id
HAVING cnt > 1;

-- 3. maxParticipants vs 실제 참여자 비교
SELECT
    e.id AS event_id,
    e.max_participants,
    COUNT(ep.id) AS actual_participants,
    CASE
        WHEN COUNT(ep.id) = e.max_participants THEN '✅ PASS - 정합성 유지'
        WHEN COUNT(ep.id) > e.max_participants THEN '❌ FAIL - 초과 참여 발생'
        ELSE '⚠️ 참여자 부족 (정상일 수 있음)'
        END AS result
FROM events e
         LEFT JOIN event_participants ep ON e.id = ep.event_id
WHERE e.title LIKE '%부하테스트%'
GROUP BY e.id, e.max_participants;


-- ============================================
-- 테스트 데이터 정리 SQL (테스트 완료 후 실행)
-- ============================================

-- DELETE FROM event_participants WHERE event_id = (SELECT id FROM events WHERE title LIKE '%부하테스트%' LIMIT 1);
-- DELETE FROM events WHERE title LIKE '%부하테스트%';
-- DELETE FROM users WHERE email LIKE 'loadtest%@test.com';