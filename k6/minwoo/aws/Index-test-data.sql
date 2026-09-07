-- ============================================
-- revenues 테이블 인덱스 성능 비교 테스트 (AWS 환경 최적화)
-- 작가 500명 x 각 10,000건 = 500만건
-- INSERT INTO SELECT 방식으로 최적화
-- ============================================
SET @@cte_max_recursion_depth = 100000;

DELIMITER $$
DROP PROCEDURE IF EXISTS insert_revenue_dummy$$
CREATE PROCEDURE insert_revenue_dummy()
BEGIN
    DECLARE i INT DEFAULT 1;
    DECLARE v_author_id BIGINT;

    WHILE i <= 500 DO
SELECT id INTO v_author_id FROM users WHERE email = CONCAT('loadtest', i, '@test.com');

IF v_author_id IS NOT NULL THEN
            -- 10,000건을 한 번에 INSERT (SELECT 방식)
            INSERT INTO revenues (author_id, episode_id, amount, balance, type, created_at)
            WITH RECURSIVE seq AS (
                SELECT 1 AS n
                UNION ALL
                SELECT n + 1 FROM seq WHERE n < 10000
            )
SELECT
    v_author_id,
    NULL,
    CASE FLOOR(RAND() * 4)
        WHEN 0 THEN FLOOR(100 + RAND() * 900)
        WHEN 1 THEN FLOOR(5000 + RAND() * 5000)
        WHEN 2 THEN FLOOR(10000 + RAND() * 50000)
        ELSE FLOOR(1000 + RAND() * 5000)
        END,
    0,
    CASE (n % 20)
        WHEN 0 THEN 'EPISODE_SALE'
        WHEN 1 THEN 'EPISODE_SALE'
        WHEN 2 THEN 'EPISODE_SALE'
        WHEN 3 THEN 'EPISODE_SALE'
        WHEN 4 THEN 'EPISODE_SALE'
        WHEN 5 THEN 'EPISODE_SALE'
        WHEN 6 THEN 'EPISODE_SALE'
        WHEN 7 THEN 'EPISODE_SALE'
        WHEN 8 THEN 'EPISODE_SALE'
        WHEN 9 THEN 'EPISODE_SALE'
        WHEN 10 THEN 'EPISODE_SALE'
        WHEN 11 THEN 'EPISODE_SALE'
        WHEN 12 THEN 'EPISODE_SALE'
        WHEN 13 THEN 'EPISODE_SALE'
        WHEN 14 THEN 'SUBSCRIPTION'
        WHEN 15 THEN 'SUBSCRIPTION'
        WHEN 16 THEN 'SUBSCRIPTION'
        WHEN 17 THEN 'WITHDRAWAL'
        WHEN 18 THEN 'WITHDRAWAL'
        ELSE 'REFUND'
        END,
    DATE_SUB(NOW(), INTERVAL FLOOR(RAND() * 365) DAY)
FROM seq;
END IF;

        SET i = i + 1;
END WHILE;
END$$
DELIMITER ;

CALL insert_revenue_dummy();
DROP PROCEDURE IF EXISTS insert_revenue_dummy;

-- 확인
SELECT COUNT(*) AS total_revenues FROM revenues;
SELECT type, COUNT(*) AS cnt, SUM(amount) AS total FROM revenues GROUP BY type;