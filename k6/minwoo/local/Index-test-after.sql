-- ============================================
-- 3단계: 인덱스 추가 후 성능 측정
-- ============================================

-- 복합 인덱스 추가
CREATE INDEX idx_revenue_author_type ON revenues (author_id, type);
CREATE INDEX idx_revenue_author_type_date ON revenues (author_id, type, created_at);

-- 인덱스 확인
SHOW INDEX FROM revenues;

-- 쿼리 1: 특정 작가의 수익 현황 집계 (인덱스 적용 후)
EXPLAIN ANALYZE
SELECT
    SUM(CASE WHEN type IN ('EPISODE_SALE', 'SUBSCRIPTION', 'REFUND') THEN amount ELSE 0 END) AS total_earned,
    SUM(CASE WHEN type = 'WITHDRAWAL' THEN amount ELSE 0 END) AS total_withdrawn
FROM revenues
WHERE author_id = (SELECT id FROM users WHERE email = 'loadtest1@test.com');

-- 쿼리 2: 특정 작가의 월별 수익 통계 (인덱스 적용 후)
EXPLAIN ANALYZE
SELECT
    YEAR(created_at) AS yr,
    MONTH(created_at) AS mn,
    SUM(amount) AS total_amount,
    COUNT(*) AS cnt
FROM revenues
WHERE author_id = (SELECT id FROM users WHERE email = 'loadtest1@test.com')
  AND type IN ('EPISODE_SALE', 'SUBSCRIPTION')
  AND created_at >= '2025-01-01'
GROUP BY YEAR(created_at), MONTH(created_at)
ORDER BY yr, mn;

-- 쿼리 3: 전체 작가 중 수익 TOP 10 (인덱스 적용 후)
EXPLAIN ANALYZE
SELECT author_id, SUM(amount) AS total
FROM revenues
WHERE type IN ('EPISODE_SALE', 'SUBSCRIPTION')
GROUP BY author_id
ORDER BY total DESC
    LIMIT 10;

-- ============================================
-- 결과 비교 포인트:
-- 1. actual time 값 비교 (Before vs After)
-- 2. rows examined 비교
-- 3. Full Table Scan → Index Range Scan 변화
-- ============================================