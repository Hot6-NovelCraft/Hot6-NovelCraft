-- ============================================================
--  [시나리오 A / user-journey 전용] 더미데이터 (고속 적재 최적화판)
--  대상 흐름: 로그인 → 검색 → 랭킹 → AI 추천
--
--  ⚠️ 전제조건: k6/load-test-data.sql (loadtest1~200) 이미 적용됨.
--  ⚠️ 실행 위치: 반드시 EC2(RDS와 같은 VPC) 안에서 실행하세요.
--  ⚠️ novels 테이블 보조 인덱스를 적재 직전 잠깐 지웠다가 끝나면 자동 복구합니다.
-- ============================================================

SET @TARGET_NOVELS = 50000;
SET @AUTHOR_USERS   = 2000;
SET @CHUNK_SIZE      = 50000;

SET SESSION unique_checks = 0;
SET SESSION foreign_key_checks = 0;
SET autocommit = 0;

-- 0. 사전 확인
SELECT COUNT(*) AS journey_login_users_found
FROM users WHERE email LIKE 'loadtest%@test.com' AND email NOT LIKE 'dummyauthor%';
-- ↑ 200이 안 나오면 k6/load-test-data.sql 부터 먼저 실행하세요.

-- 1. 숫자 시퀀스 헬퍼 테이블 (교차조인 방식)
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
    CONCAT('journeyauthor', n, '@test.com'),
    '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy',
    CONCAT('journey_author_', n),
    CONCAT('030', LPAD(n, 8, '0')),
    '1995-01-01', 'AUTHOR', true, false,
    NOW() - INTERVAL FLOOR(RAND() * 365) DAY
FROM numbers
WHERE n <= @AUTHOR_USERS;
COMMIT;

DROP TABLE IF EXISTS author_pool;
CREATE TABLE author_pool (rn BIGINT PRIMARY KEY, user_id BIGINT NOT NULL) ENGINE=InnoDB;
INSERT INTO author_pool (rn, user_id)
SELECT ROW_NUMBER() OVER (ORDER BY id) AS rn, id
FROM users WHERE email LIKE 'journeyauthor%@test.com';
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

-- 4. novels 대량 생성 (검색 키워드 8종을 제목에 순환 삽입 + 랭킹용 view_count 편차)
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
            CONCAT(
                ELT(1 + (num.n % 8), '백산','판타지','로맨스','무협','회귀','환생','전생','히어로'),
                '_더미소설_', num.n
            ),
            CONCAT('[더미 설명] ', num.n, '번째 저니 테스트용 소설입니다.'),
            NULL,
            ELT(1 + (num.n % 9),
                'FANTASY','ROMANCE_FANTASY','MODERN','CHIVALROUS','BL','SF','HORROR','CLASSIC','DAILY_LIFE'),
            ELT(1 + (num.n % 5),
                'ISEKAI,REGRESSION,HEALING',
                'ACTION,ROMANCE,COMEDY',
                'MYSTERY,MARTIAL_ARTS,VILLAIN',
                'MUNCHKIN,ACADEMY,CONTRACT',
                'REVENGE,GROWTH,DUNGEON'),
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

SELECT '백산' AS keyword, COUNT(*) AS matched FROM novels WHERE title LIKE '백산%'
UNION ALL SELECT '판타지', COUNT(*) FROM novels WHERE title LIKE '판타지%'
UNION ALL SELECT '로맨스', COUNT(*) FROM novels WHERE title LIKE '로맨스%'
UNION ALL SELECT '무협',   COUNT(*) FROM novels WHERE title LIKE '무협%'
UNION ALL SELECT '회귀',   COUNT(*) FROM novels WHERE title LIKE '회귀%'
UNION ALL SELECT '환생',   COUNT(*) FROM novels WHERE title LIKE '환생%'
UNION ALL SELECT '전생',   COUNT(*) FROM novels WHERE title LIKE '전생%'
UNION ALL SELECT '히어로', COUNT(*) FROM novels WHERE title LIKE '히어로%';

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

SELECT '===== user-journey 더미데이터 생성 완료 (인덱스 복구됨) =====' AS result;