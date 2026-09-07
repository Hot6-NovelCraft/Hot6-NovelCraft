-- ============================================
-- 환전 부하 테스트용 데이터 생성 SQL
-- 작가 1명 (잔액 100만원) + 인증된 계좌 1개
-- ============================================

-- 1. 테스트용 작가 유저 생성 (이미 있으면 스킵)
INSERT IGNORE INTO users (email, password, nickname, phone_no, birthday, role, is_adult_verified, is_deleted, created_at)
VALUES (
    'author_loadtest@test.com',
    '$2a$10$olEFSXkP4KaiHDlrbVWMeeV8e.nVtj4bkttVfB.0BgOXWZz2Z687K',
    'loadtest_author',
    '01099990000',
    '1990-01-01',
    'AUTHOR',
    false,
    false,
    NOW()
);

-- 작가 유저 ID 확인
SELECT @author_id := id FROM users WHERE email = 'author_loadtest@test.com';

-- 2. 인증된 계좌 생성
INSERT INTO bank_accounts (user_id, bank_name, account_number, account_holder, is_verified, created_at)
VALUES (
           @author_id,
           '국민은행',
           'tbYc2WiB6w0ki6CkyHhXh4PCHkSSPpPhC8WvIKlVdtE=',
           '테스트작가',
           true,
           NOW()
       );

-- 3. 수익 데이터 생성 (잔액 100만원)
INSERT INTO revenues (author_id, episode_id, amount, balance, type, created_at)
VALUES (
           @author_id,
           NULL,
           1000000,
           1000000,
           'EPISODE_SALE',
           NOW()
       );

-- 4. 확인
SELECT id, email, role FROM users WHERE email = 'author_loadtest@test.com';
SELECT id, user_id, bank_name, is_verified FROM bank_accounts WHERE user_id = @author_id;
SELECT id, author_id, amount, balance, type FROM revenues WHERE author_id = @author_id;