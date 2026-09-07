-- ============================================
-- revenues 테이블 인덱스 성능 비교 테스트
-- 1단계: 더미 데이터 10만건 삽입
-- 2단계: 인덱스 없이 쿼리 성능 측정
-- 3단계: 인덱스 추가 후 쿼리 성능 측정
-- ============================================

-- 1단계: 더미 데이터 10만건 삽입
-- 작가 50명 x 각 2000건 = 10만건

DELIMITER $$
DROP PROCEDURE IF EXISTS insert_revenue_dummy$$
CREATE PROCEDURE insert_revenue_dummy()
BEGIN
    DECLARE i INT DEFAULT 1;
    DECLARE author_id BIGINT;
    DECLARE j INT;
    DECLARE rev_type VARCHAR(20);
    DECLARE rev_amount INT;
    DECLARE rev_date DATETIME;

    -- loadtest 유저 1~50번을 작가로 활용
    WHILE i <= 50 DO
SELECT id INTO author_id FROM users WHERE email = CONCAT('loadtest', i, '@test.com');

IF author_id IS NOT NULL THEN
            SET j = 1;
            WHILE j <= 2000 DO
                -- 타입 랜덤 배분: 70% EPISODE_SALE, 15% SUBSCRIPTION, 10% WITHDRAWAL, 5% REFUND
                SET rev_type = CASE
                    WHEN j % 20 <= 13 THEN 'EPISODE_SALE'
                    WHEN j % 20 <= 16 THEN 'SUBSCRIPTION'
                    WHEN j % 20 <= 18 THEN 'WITHDRAWAL'
                    ELSE 'REFUND'
END;

                SET rev_amount = CASE
                    WHEN rev_type = 'EPISODE_SALE' THEN FLOOR(100 + RAND() * 900)
                    WHEN rev_type = 'SUBSCRIPTION' THEN FLOOR(5000 + RAND() * 5000)
                    WHEN rev_type = 'WITHDRAWAL' THEN FLOOR(10000 + RAND() * 50000)
                    ELSE FLOOR(1000 + RAND() * 5000)
END;

                -- 최근 1년간 랜덤 날짜
                SET rev_date = DATE_SUB(NOW(), INTERVAL FLOOR(RAND() * 365) DAY);

INSERT INTO revenues (author_id, episode_id, amount, balance, type, created_at)
VALUES (author_id, NULL, rev_amount, 0, rev_type, rev_date);

SET j = j + 1;
END WHILE;
END IF;

        SET i = i + 1;
END WHILE;
END$$
DELIMITER ;

CALL insert_revenue_dummy();
DROP PROCEDURE insert_revenue_dummy;

-- 데이터 확인
SELECT COUNT(*) AS total_revenues FROM revenues;
SELECT type, COUNT(*) AS cnt, SUM(amount) AS total FROM revenues GROUP BY type;