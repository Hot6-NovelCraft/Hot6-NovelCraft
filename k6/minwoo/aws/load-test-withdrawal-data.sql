-- ============================================
-- 환전 부하 테스트용 데이터 생성 SQL (AWS 환경)
-- 작가 500명 (잔액 100만원씩) + 인증된 계좌 500개
-- ============================================

DELIMITER $$
DROP PROCEDURE IF EXISTS create_withdrawal_test_data$$
CREATE PROCEDURE create_withdrawal_test_data()
BEGIN
    DECLARE i INT DEFAULT 1;
    DECLARE v_author_id BIGINT;

    WHILE i <= 500 DO
        -- 작가 유저 생성 (loadtest 유저를 AUTHOR로 업데이트)
UPDATE users SET role = 'AUTHOR' WHERE email = CONCAT('loadtest', i, '@test.com');
SELECT id INTO v_author_id FROM users WHERE email = CONCAT('loadtest', i, '@test.com');

IF v_author_id IS NOT NULL THEN
            -- 인증된 계좌 생성
            INSERT IGNORE INTO bank_accounts (user_id, bank_name, account_number, account_holder, is_verified, created_at)
            VALUES (
                v_author_id,
                '국민은행',
                CONCAT('tbYc2WiB6w0ki6CkyHhXh4PCHkSSPpPhC8WvIKlVdtE=', i),
                CONCAT('테스트작가', i),
                true,
                NOW()
            );

            -- 수익 잔액 100만원 생성
INSERT INTO revenues (author_id, episode_id, amount, balance, type, created_at)
VALUES (v_author_id, NULL, 1000000, 1000000, 'EPISODE_SALE', NOW());
END IF;

        SET i = i + 1;
END WHILE;
END$$
DELIMITER ;

CALL create_withdrawal_test_data();
DROP PROCEDURE IF EXISTS create_withdrawal_test_data;

-- 확인
SELECT COUNT(*) AS author_count FROM users WHERE role = 'AUTHOR';
SELECT COUNT(*) AS account_count FROM bank_accounts WHERE is_verified = true;
SELECT COUNT(*) AS revenue_count FROM revenues WHERE balance = 1000000;