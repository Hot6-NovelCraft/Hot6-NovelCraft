-- ============================================================
--  [Fail-Open/Fail-Closed 정책 테스트 전용] 더미데이터 (고속 적재 최적화판)
--  대상 API:
--    - Fail-Open  : GET /api/novels/ranking?type=realtime
--    - Fail-Closed: PATCH /api/auth/users/me/password
--
--  ⚠️ 전제조건: k6/load-test-data.sql (loadtest1~200) 이미 적용됨.
--  ⚠️ 실행 위치: 반드시 EC2(RDS와 같은 VPC) 안에서 실행하세요.
--  ⚠️ novels 테이블 보조 인덱스를 적재 직전 잠깐 지웠다가 끝나면 자동 복구합니다.
--     (PK/UNIQUE는 건드리지 않음)
-- ============================================================

SET @TARGET_NOVELS = 50000;
SET @AUTHOR_USERS   = 2000;
SET @CHUNK_SIZE      = 50000;

SET SESSION unique_checks = 0;
SET SESSION foreign_key_checks = 0;
SET autocommit = 0;

-- 0. 사전 확인
SELECT COUNT(*) AS password_test_users_found
FROM users
WHERE email IN (
    'loadtest191@test.com','loadtest192@test.com','loadtest193@test.com',
    'loadtest194@test.com','loadtest195@test.com','loadtest196@test.com',
    'loadtest197@test.com','loadtest198@test.com','loadtest199@test.com',
    'loadtest200@test.com'
);
-- ↑ 10이 아니면 여기서 멈추고 k6/load-test-data.sql 부터 실행하세요.

-- 1. 숫자 시퀀스 헬퍼 테이블 (교차조인 방식 - 재귀 CTE보다 훨씬 빠름)
DROP TABLE IF EXISTS seed_digits;
CREATE TABLE seed_digits (d TINYINT PRIMARY KEY) ENGINE=InnoDB;
INSERT INTO seed_digits VALUES (0),(1),(2),(3),(4),(5),(6),(7),(8),(9);

DROP TABLE IF EXISTS numbers;
CREATE TABLE numbers (n BIGINT PRIMARY KEY) ENGINE=InnoDB;

INSERT INTO numbers (n)
SELECT (a.d + b.d*10 + c.d*100 + d.d*1000 + e.d*10000 + f.d*100000) + 1 AS n
FROM seed_digits a, seed_digits b, seed_digits c, seed_digits d, seed_digits e, seed_digits f
WHERE (a.d + b.d*10 + c.d*100 + d.d*1000 + e.d*10000 + f.d*100000) < @TARGET_NOVELS;

DROP TABLE seed_digits;
COMMIT;

-- 2. 이 스크립트 전용 더미 작가 계정 (소량이라 인덱스 드랍 불필요)
INSERT IGNORE INTO users
    (email, password, nickname, phone_no, birthday, role,
     is_adult_verified, is_deleted, created_at)
SELECT
    CONCAT('dummyauthor', n, '@test.com'),
    '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy',
    CONCAT('dummy_author_', n),
    CONCAT('020', LPAD(n, 8, '0')),
    '1995-01-01', 'AUTHOR', true, false,
    NOW() - INTERVAL FLOOR(RAND() * 365) DAY
FROM numbers
WHERE n <= @AUTHOR_USERS;
COMMIT;

DROP TABLE IF EXISTS author_pool;
CREATE TABLE author_pool (rn BIGINT PRIMARY KEY, user_id BIGINT NOT NULL) ENGINE=InnoDB;
INSERT INTO author_pool (rn, user_id)
SELECT ROW_NUMBER() OVER (ORDER BY id) AS rn, id
FROM users WHERE email LIKE 'dummyauthor%@test.com';
COMMIT;

SET @AUTHOR_COUNT = (SELECT COUNT(*) FROM author_pool);

-- 3. (고속화) novels 보조 인덱스 임시 제거
DELIMITER $$
DROP PROCEDURE IF EXISTS safe_drop_index$$
CREATE PROCEDURE safe_drop_index(IN tbl VARCHAR(64), IN idx VARCHAR(64))
BEGIN
    DECLARE CONTINUE HANDLER FOR SQLEXCEPTION BEGIN END;
    SET @ddl = CONCAT('ALTER TABLE ', tbl, ' DROP INDEX ', idx);
    PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
END$$
DELIMITER ;

CALL safe_drop_index('novels', 'idx_novel_genre');
CALL safe_drop_index('novels', 'idx_novel_status');
CALL safe_drop_index('novels', 'idx_novel_status_deleted');
CALL safe_drop_index('novels', 'idx_novel_new_list');
COMMIT;

-- 4. novels 대량 생성 (랭킹 API 조회 대상, view_count 멱법칙 분포)
DELIMITER $$
DROP PROCEDURE IF EXISTS bulk_insert_novels$$
CREATE PROCEDURE bulk_insert_novels()
BEGIN
    DECLARE start_n BIGINT DEFAULT 1;
    DECLARE end_n BIGINT;

    WHILE start_n <= @TARGET_NOVELS DO
        SET end_n = LEAST(start_n + @CHUNK_SIZE - 1, @TARGET_NOVELS);

        INSERT INTO novels
            (author_id, title, description, cover_image_url, genre, tags,
             status, view_count, is_deleted, bookmark_count, updated_at, created_at)
        SELECT
            ap.user_id,
            CONCAT('랭킹더미소설_', num.n),
            CONCAT('[더미 설명] ', num.n, '번째 랭킹 테스트용 소설입니다.'),
            NULL,
            ELT(1 + (num.n % 9),
                'FANTASY','ROMANCE_FANTASY','MODERN','CHIVALROUS','BL','SF','HORROR','CLASSIC','DAILY_LIFE'),
            'ISEKAI,REGRESSION,HEALING',
            'ONGOING',
            FLOOR(POWER(RAND(), 3) * 500000),
            false,
            FLOOR(RAND() * 3000),
            NOW(),
            NOW() - INTERVAL FLOOR(RAND() * 365) DAY
        FROM numbers num
        JOIN author_pool ap ON ap.rn = ((num.n - 1) % @AUTHOR_COUNT) + 1
        WHERE num.n BETWEEN start_n AND end_n;

        COMMIT;
        SET start_n = end_n + 1;
    END WHILE;
END$$
DELIMITER ;

CALL bulk_insert_novels();
DROP PROCEDURE bulk_insert_novels;

SELECT COUNT(*) AS total_novels FROM novels WHERE title LIKE '랭킹더미소설_%';

-- 5. (고속화 원상복구) 인덱스 재생성
ALTER TABLE novels ADD INDEX idx_novel_genre (genre);
ALTER TABLE novels ADD INDEX idx_novel_status (status);
ALTER TABLE novels ADD INDEX idx_novel_status_deleted (is_deleted, status);
ALTER TABLE novels ADD INDEX idx_novel_new_list (is_deleted, status, genre, created_at);
COMMIT;

-- 6. 세션 옵션 원복 + 정리
SET SESSION unique_checks = 1;
SET SESSION foreign_key_checks = 1;
SET autocommit = 1;

DROP PROCEDURE IF EXISTS safe_drop_index;
DROP TABLE IF EXISTS numbers;
DROP TABLE IF EXISTS author_pool;

SELECT '===== fail-policy 더미데이터 생성 완료 (인덱스 복구됨) =====' AS result;