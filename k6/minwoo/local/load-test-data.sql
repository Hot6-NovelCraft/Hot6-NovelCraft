-- ============================================
-- 부하 테스트용 데이터 생성 SQL
-- 테스트 유저 200명 + 선착순 이벤트 1개 (100명 모집)
-- ============================================

-- 1. 테스트 유저 200명 생성 (비밀번호: test1234)
-- BCrypt 해시: $2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy

DELIMITER $$
DROP PROCEDURE IF EXISTS create_test_users$$
CREATE PROCEDURE create_test_users()
BEGIN
    DECLARE i INT DEFAULT 1;
    WHILE i <= 200 DO
        INSERT IGNORE INTO users (email, password, nickname, phone_no, birthday, role, is_adult_verified, is_deleted, created_at)
        VALUES (
            CONCAT('loadtest', i, '@test.com'),
            '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy',
            CONCAT('loadtest_user_', i),
            CONCAT('010', LPAD(i, 8, '0')),
            '2000-01-01',
            'READER',
            false,
            false,
            NOW()
        );
        SET i = i + 1;
END WHILE;
END$$
DELIMITER ;

CALL create_test_users();
DROP PROCEDURE create_test_users;

-- 2. 선착순 이벤트 생성 (100명 모집, 현재 진행중)
INSERT INTO events (created_by, title, description, reward_points, max_participants, started_at, ended_at, created_at)
VALUES (
           1,
           '[부하테스트] 선착순 100명 포인트 지급 이벤트',
           '부하 테스트용 선착순 이벤트입니다.',
           1000,
           100,
           NOW(),
           DATE_ADD(NOW(), INTERVAL 1 DAY),
           NOW()
       );

-- 3. 생성된 이벤트 ID 확인
SELECT id, title, max_participants, started_at, ended_at FROM events WHERE title LIKE '%부하테스트%';

-- 4. 생성된 테스트 유저 수 확인
SELECT COUNT(*) AS test_user_count FROM users WHERE email LIKE 'loadtest%@test.com';