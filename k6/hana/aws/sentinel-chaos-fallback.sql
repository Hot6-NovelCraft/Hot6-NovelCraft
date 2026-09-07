-- ============================================================
--  [chaos-fallback-test.js / sentinel-chaos-fallback-aws 전용] 더미데이터 (고속 적재 최적화판)
--  대상 API: GET /api/auth/users/me (JWT 블랙리스트 → Redis 장애 시 DB Fallback)
--
--  ⚠️ 전제조건: k6/load-test-data.sql (loadtest1~200) 이미 적용됨 (loadtest1 로그인용).
--  ⚠️ 실행 위치: 반드시 EC2(RDS와 같은 VPC) 안에서 실행하세요.
--  ⚠️ blacklist_tokens 보조 인덱스를 적재 직전 잠깐 지웠다가 끝나면 자동 복구합니다.
-- ============================================================

SET @TARGET_BLACKLIST = 150000;
SET @CHUNK_SIZE        = 50000;

SET SESSION unique_checks = 0;
SET SESSION foreign_key_checks = 0;
SET autocommit = 0;

-- 0. 사전 확인
SELECT COUNT(*) AS login_user_found FROM users WHERE email = 'loadtest1@test.com';
-- ↑ 0이면 k6/load-test-data.sql 부터 먼저 실행하세요.

-- 1. 숫자 시퀀스 헬퍼 테이블 (교차조인 방식)
DROP TABLE IF EXISTS seed_digits;
CREATE TABLE seed_digits (d TINYINT PRIMARY KEY) ENGINE=InnoDB;
INSERT INTO seed_digits VALUES (0),(1),(2),(3),(4),(5),(6),(7),(8),(9);

DROP TABLE IF EXISTS numbers;
CREATE TABLE numbers (n BIGINT PRIMARY KEY) ENGINE=InnoDB;

INSERT INTO numbers (n)
SELECT (a.d + b.d*10 + c.d*100 + d.d*1000 + e.d*10000 + f.d*100000) + 1 AS n
FROM seed_digits a, seed_digits b, seed_digits c, seed_digits d, seed_digits e, seed_digits f
WHERE (a.d + b.d*10 + c.d*100 + d.d*1000 + e.d*10000 + f.d*100000) < @TARGET_BLACKLIST;

DROP TABLE seed_digits;
COMMIT;

-- 2. (고속화) blacklist_tokens 보조 인덱스 임시 제거 (token 자체의 UNIQUE 제약은 유지)
DELIMITER $$
DROP PROCEDURE IF EXISTS safe_drop_index$$
CREATE PROCEDURE safe_drop_index(IN tbl VARCHAR(64), IN idx VARCHAR(64))
BEGIN
    DECLARE CONTINUE HANDLER FOR SQLEXCEPTION BEGIN END;
    SET @ddl = CONCAT('ALTER TABLE ', tbl, ' DROP INDEX ', idx);
    PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
END$$
DELIMITER ;

CALL safe_drop_index('blacklist_tokens', 'idx_blacklist_token');
COMMIT;

-- 3. blacklist_tokens 대량 생성
--    token은 SHA2-256(64자, UUID 포함)이라 unique_checks=0 이어도 충돌 확률은 사실상 0
DELIMITER $$
DROP PROCEDURE IF EXISTS bulk_insert_blacklist$$
CREATE PROCEDURE bulk_insert_blacklist()
BEGIN
    DECLARE start_n BIGINT DEFAULT 1;
    DECLARE end_n BIGINT;

    WHILE start_n <= @TARGET_BLACKLIST DO
        SET end_n = LEAST(start_n + @CHUNK_SIZE - 1, @TARGET_BLACKLIST);

        INSERT INTO blacklist_tokens (token, reason, expired_at, created_at)
        SELECT
            SHA2(CONCAT('dummy-blacklist-token-', n, '-', UUID()), 256),
            ELT(1 + (n % 3), 'LOGOUT', 'PASSWORD_CHANGE', 'ADMIN_FORCE_LOGOUT'),
            NOW() + INTERVAL (FLOOR(RAND() * 5760) - 4320) MINUTE,
            NOW() - INTERVAL FLOOR(RAND() * 30) DAY
        FROM numbers
        WHERE n BETWEEN start_n AND end_n;

        COMMIT;
        SET start_n = end_n + 1;
    END WHILE;
END$$
DELIMITER ;

CALL bulk_insert_blacklist();
DROP PROCEDURE bulk_insert_blacklist;

SELECT COUNT(*) AS total_blacklist_tokens FROM blacklist_tokens;

-- 4. (고속화 원상복구) 인덱스 재생성
ALTER TABLE blacklist_tokens ADD INDEX idx_blacklist_token (token);
COMMIT;

-- 5. 세션 옵션 원복 + 정리
SET SESSION unique_checks = 1;
SET SESSION foreign_key_checks = 1;
SET autocommit = 1;

DROP PROCEDURE IF EXISTS safe_drop_index;
DROP TABLE IF EXISTS numbers;

SELECT '===== sentinel-chaos-fallback-aws 더미데이터 생성 완료 (인덱스 복구됨) =====' AS result;