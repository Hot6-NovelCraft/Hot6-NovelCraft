-- ============================================
-- 환전 부하 테스트 후 정합성 검증 SQL
-- ============================================

-- 1. Revenue 타입별 확인 (WITHDRAWAL이 1건이어야 정합성 통과)
SELECT type, COUNT(*) AS cnt, SUM(amount) AS total_amount
FROM revenues
WHERE author_id = (SELECT id FROM users WHERE email = 'author_loadtest@test.com')
GROUP BY type;

-- 2. Withdrawal 건수 확인 (1건이어야 정상)
SELECT COUNT(*) AS withdrawal_count, SUM(request_amount) AS total_requested
FROM withdrawals
WHERE author_id = (SELECT id FROM users WHERE email = 'author_loadtest@test.com');

-- 3. 잔액 정합성 확인
SELECT
    (SELECT COALESCE(SUM(amount), 0) FROM revenues
     WHERE author_id = (SELECT id FROM users WHERE email = 'author_loadtest@test.com')
       AND type IN ('EPISODE_SALE', 'SUBSCRIPTION', 'REFUND')) AS total_earned,
    (SELECT COALESCE(SUM(amount), 0) FROM revenues
     WHERE author_id = (SELECT id FROM users WHERE email = 'author_loadtest@test.com')
       AND type = 'WITHDRAWAL') AS total_withdrawn,
    (SELECT COALESCE(SUM(amount), 0) FROM revenues
     WHERE author_id = (SELECT id FROM users WHERE email = 'author_loadtest@test.com')
       AND type IN ('EPISODE_SALE', 'SUBSCRIPTION', 'REFUND'))
        -
    (SELECT COALESCE(SUM(amount), 0) FROM revenues
     WHERE author_id = (SELECT id FROM users WHERE email = 'author_loadtest@test.com')
       AND type = 'WITHDRAWAL') AS available_balance;


-- ============================================
-- 테스트 데이터 정리 SQL (테스트 완료 후 실행)
-- ============================================
-- DELETE FROM withdrawals WHERE author_id = (SELECT id FROM users WHERE email = 'author_loadtest@test.com');
-- DELETE FROM revenues WHERE author_id = (SELECT id FROM users WHERE email = 'author_loadtest@test.com');
-- DELETE FROM bank_accounts WHERE user_id = (SELECT id FROM users WHERE email = 'author_loadtest@test.com');
-- DELETE FROM users WHERE email = 'author_loadtest@test.com';