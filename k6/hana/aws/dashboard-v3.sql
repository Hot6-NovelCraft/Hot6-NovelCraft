-- ============================================================
--  [dashboard-v3-test.js 전용] 더미데이터 (고속 적재 최적화판)
--  대상 API: GET /api/admin/dashboard/live (?role=... 필터 포함)
--
--  ⚠️ 전제조건: k6/load-test-data.sql (loadtest1~200) 이미 적용됨. 그 200명은 건드리지 않음.
--  ⚠️ 실행 위치: 반드시 EC2(RDS와 같은 VPC) 안에서 실행하세요.
--  ⚠️ 이 스크립트는 로딩 속도를 위해 novels/users/mentors 의 "보조 인덱스"를
--     적재 직전에 잠깐 지웠다가, 적재가 끝나면 스크립트 안에서 자동으로 다시 만듭니다.
--     (PK/UNIQUE 제약은 절대 건드리지 않습니다 - 중복 방지 안전장치 유지)
--     → 스크립트가 끝까지 정상 실행되면 인덱스는 원상복구되어 있으니,
--        그대로 이어서 k6 테스트를 실행하면 됩니다.
--
--  ※ 참고: dashboard-v3-test.js 는 `?userRole=USER` 를 보내는데 실제 파라미터명은
--    `role` 이고 UserRole enum엔 USER 값이 없습니다. `?role=READER` 로 고쳐야
--    필터 ON/OFF 비교가 의미있게 측정됩니다.
-- ============================================================

SET @TARGET_USERS   = 500000;
SET @TARGET_NOVELS  = 50000;
SET @TARGET_MENTORS = 3000;
SET @CHUNK_SIZE     = 50000;   -- unique_checks/fk_checks OFF 상태라 청크를 더 크게 잡아도 안전

-- ------------------------------------------------------------
-- 0. 세션 레벨 고속 적재 옵션
--    - unique_checks=0  : INSERT 시 보조 유니크 인덱스 중복검사를 미룸(대량 삽입 핵심 가속 포인트)
--    - foreign_key_checks=0 : FK 검증 스킵 (이 프로젝트는 엔티티에 실제 FK가 없어 영향 적지만 관례상 끔)
--    - autocommit=0 : 각 청크 끝에서만 명시적으로 COMMIT (매 행마다 fsync 방지)
-- ------------------------------------------------------------
SET SESSION unique_checks = 0;
SET SESSION foreign_key_checks = 0;
SET autocommit = 0;

-- ------------------------------------------------------------
-- 1. 숫자 시퀀스 헬퍼 테이블
--    재귀 CTE(1행씩 반복 생성) 대신, 10자리 시드 테이블을 6번 교차조인해서
--    최대 999,999개까지 "한 번의 SELECT"로 만듭니다. 500,000건 기준 체감 속도 차이가 큽니다.
-- ------------------------------------------------------------
DROP TABLE IF EXISTS seed_digits;
CREATE TABLE seed_digits (d TINYINT PRIMARY KEY) ENGINE=InnoDB;
INSERT INTO seed_digits VALUES (0),(1),(2),(3),(4),(5),(6),(7),(8),(9);

DROP TABLE IF EXISTS numbers;
CREATE TABLE numbers (n BIGINT PRIMARY KEY) ENGINE=InnoDB;

INSERT INTO numbers (n)
SELECT (a.d + b.d*10 + c.d*100 + d.d*1000 + e.d*10000 + f.d*100000) + 1 AS n
FROM seed_digits a, seed_digits b, seed_digits c, seed_digits d, seed_digits e, seed_digits f
WHERE (a.d + b.d*10 + c.d*100 + d.d*1000 + e.d*10000 + f.d*100000) < @TARGET_USERS;

DROP TABLE seed_digits;
COMMIT;

-- ------------------------------------------------------------
-- 2. (고속화) 인덱스 임시 제거 - 에러가 나도 스크립트가 멈추지 않도록 안전 프로시저 사용
-- ------------------------------------------------------------
DELIMITER $$
DROP PROCEDURE IF EXISTS safe_drop_index$$
CREATE PROCEDURE safe_drop_index(IN tbl VARCHAR(64), IN idx VARCHAR(64))
BEGIN
    DECLARE CONTINUE HANDLER FOR SQLEXCEPTION BEGIN END;  -- 인덱스가 없어도 그냥 넘어감
    SET @ddl = CONCAT('ALTER TABLE ', tbl, ' DROP INDEX ', idx);
    PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
END$$
DELIMITER ;

CALL safe_drop_index('users', 'idx_user_role_deleted');
CALL safe_drop_index('users', 'idx_user_created_at');
CALL safe_drop_index('novels', 'idx_novel_genre');
CALL safe_drop_index('novels', 'idx_novel_status');
CALL safe_drop_index('novels', 'idx_novel_status_deleted');
CALL safe_drop_index('novels', 'idx_novel_new_list');
CALL safe_drop_index('mentors', 'idx_mentor_status');
CALL safe_drop_index('mentors', 'idx_mentor_created_at');
COMMIT;

-- ------------------------------------------------------------
-- 3. users 대량 생성 (201번 ~ TARGET_USERS, 기존 200명은 보존)
-- ------------------------------------------------------------
DELIMITER $$
DROP PROCEDURE IF EXISTS bulk_insert_users$$
CREATE PROCEDURE bulk_insert_users()
BEGIN
    DECLARE start_n BIGINT DEFAULT 201;
    DECLARE end_n BIGINT;

    WHILE start_n <= @TARGET_USERS DO
        SET end_n = LEAST(start_n + @CHUNK_SIZE - 1, @TARGET_USERS);

        INSERT IGNORE INTO users
            (email, password, nickname, phone_no, birthday, role,
             is_adult_verified, is_deleted, created_at)
        SELECT
            CONCAT('loadtest', n, '@test.com'),
            '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', -- test1234
            CONCAT('loadtest_user_', n),
            CONCAT('010', LPAD(n, 8, '0')),
            '2000-01-01',
            CASE
                WHEN n % 10 = 0 THEN 'AUTHOR'
                WHEN n % 100 = 1 THEN 'ADMIN'
                WHEN n % 100 = 2 THEN 'TEMP'
                ELSE 'READER'
            END,
            (n % 5 = 0),
            (n % 50 = 0),
            CASE
                WHEN n % 100 = 0 THEN NOW() - INTERVAL FLOOR(RAND() * 1440) MINUTE
                ELSE NOW() - INTERVAL FLOOR(RAND() * 730) DAY
            END
        FROM numbers
        WHERE n BETWEEN start_n AND end_n;

        COMMIT;
        SET start_n = end_n + 1;
    END WHILE;
END$$
DELIMITER ;

CALL bulk_insert_users();
DROP PROCEDURE bulk_insert_users;

SELECT COUNT(*) AS total_users FROM users;

-- ------------------------------------------------------------
-- 4. novels 대량 생성 (author_id는 방금 만든 AUTHOR 역할 유저 풀에서 순환 매핑)
-- ------------------------------------------------------------
DROP TABLE IF EXISTS author_pool;
CREATE TABLE author_pool (rn BIGINT PRIMARY KEY, user_id BIGINT NOT NULL) ENGINE=InnoDB;

INSERT INTO author_pool (rn, user_id)
SELECT ROW_NUMBER() OVER (ORDER BY id) AS rn, id
FROM users
WHERE role = 'AUTHOR';
COMMIT;

SET @AUTHOR_COUNT = (SELECT COUNT(*) FROM author_pool);

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
            CONCAT('더미소설_', num.n),
            CONCAT('[더미 설명] ', num.n, '번째 더미 소설입니다.'),
            NULL,
            ELT(1 + (num.n % 9),
                'FANTASY','ROMANCE_FANTASY','MODERN','CHIVALROUS','BL','SF','HORROR','CLASSIC','DAILY_LIFE'),
            'ISEKAI,REGRESSION,HEALING',
            ELT(1 + (num.n % 5), 'PENDING','ONGOING','COMPLETED','HIATUS','PUBLISHED'),
            FLOOR(RAND() * 100000),
            (num.n % 100 = 0),
            FLOOR(RAND() * 3000),
            NOW(),
            NOW() - INTERVAL FLOOR(RAND() * 730) DAY
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

SELECT COUNT(*) AS total_novels FROM novels;

-- ------------------------------------------------------------
-- 5. mentors 생성
-- ------------------------------------------------------------
-- ⚠️ mentors.version 은 @Version(낙관적 락) 컬럼 - NOT NULL, 기본값 없음.
--    빠뜨리면 strict mode에서 "Field 'version' doesn't have a default value" 로 INSERT 자체가 실패함.
INSERT INTO mentors
    (user_id, career_level, main_genres, special_fields, mentoring_style,
     bio, awards_career, max_mentees, allow_instant, preferred_mentee_desc,
     status, version, created_at)
SELECT
    ap.user_id,
    ELT(1 + (ap.rn % 4), 'INTRODUCTION', 'ELEMENTARY', 'INTERMEDIATE', 'PROFICIENT'),
    '더미 주요장르', '더미 특기 분야', '더미 멘토링 스타일',
    CONCAT('[더미 멘토] ', ap.rn, '번째 멘토 소개입니다.'),
    '더미 수상 이력', 5, (ap.rn % 3 = 0), '더미 선호 멘티 설명',
    ELT(1 + (ap.rn % 3), 'PENDING', 'APPROVED', 'REJECTED'),
    0,   -- 초기 버전 값 (JPA @Version 규약)
    NOW() - INTERVAL FLOOR(RAND() * 365) DAY
FROM author_pool ap
WHERE ap.rn <= @TARGET_MENTORS;
COMMIT;

SELECT COUNT(*) AS total_mentors FROM mentors;

-- ------------------------------------------------------------
-- 6. (고속화 원상복구) 인덱스 재생성 - 실제 k6 테스트는 이 인덱스가 반드시 있어야 함
-- ------------------------------------------------------------
ALTER TABLE users  ADD INDEX idx_user_role_deleted (is_deleted, role);
ALTER TABLE users  ADD INDEX idx_user_created_at (created_at);
ALTER TABLE novels ADD INDEX idx_novel_genre (genre);
ALTER TABLE novels ADD INDEX idx_novel_status (status);
ALTER TABLE novels ADD INDEX idx_novel_status_deleted (is_deleted, status);
ALTER TABLE novels ADD INDEX idx_novel_new_list (is_deleted, status, genre, created_at);
ALTER TABLE mentors ADD INDEX idx_mentor_status (status);
ALTER TABLE mentors ADD INDEX idx_mentor_created_at (created_at);
COMMIT;

-- ------------------------------------------------------------
-- 7. 세션 옵션 원복 + 정리
-- ------------------------------------------------------------
SET SESSION unique_checks = 1;
SET SESSION foreign_key_checks = 1;
SET autocommit = 1;

DROP PROCEDURE IF EXISTS safe_drop_index;
DROP TABLE IF EXISTS numbers;
DROP TABLE IF EXISTS author_pool;

SELECT '===== dashboard-v3 더미데이터 생성 완료 (인덱스 복구됨) =====' AS result;