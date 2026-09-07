import http from 'k6/http';
import { check, sleep } from 'k6';
import { Trend } from 'k6/metrics';

// ============================================
// 커스텀 메트릭
// ============================================
const cacheMissLatency = new Trend('cache_miss_latency');
const cacheHitLatency = new Trend('cache_hit_latency');

// ============================================
// 설정 - AUTHOR 토큰 필요
// ============================================
const BASE_URL = 'http://localhost:8080';
const EMAIL = 'author_loadtest@test.com';
const PASSWORD = 'test1234';

export const options = {
    scenarios: {
        cache_test: {
            executor: 'shared-iterations',
            vus: 1,
            iterations: 1,
            exec: 'cacheTest',
        },
    },
};

export function cacheTest() {
    // 로그인
    const loginRes = http.post(
        `${BASE_URL}/api/auth/login`,
        JSON.stringify({ email: EMAIL, password: PASSWORD }),
        { headers: { 'Content-Type': 'application/json' } }
    );

    const token = JSON.parse(loginRes.body).data.accessToken;
    const headers = {
        'Content-Type': 'application/json',
        'Authorization': token,
    };

    // ============================================
    // 1. Cache Miss 측정 - 첫 요청 (DB 집계 쿼리)
    // revenues 테이블 10만건 집계 → 느림
    // ============================================
    console.log('\n[Cache Miss 측정 시작]');
    let missTimes = [];

    for (let i = 0; i < 5; i++) {
        const res = http.get(
            `${BASE_URL}/api/revenues/me`,
            { headers: headers, tags: { name: 'cache-miss' } }
        );

        check(res, { '수익 현황 조회 성공': (r) => r.status === 200 });
        cacheMissLatency.add(res.timings.duration);
        missTimes.push(res.timings.duration);
        console.log(`Cache Miss ${i+1}회: ${res.timings.duration.toFixed(2)}ms`);

        sleep(0.1);
    }

    const missAvg = missTimes.reduce((a, b) => a + b, 0) / missTimes.length;
    console.log(`Cache Miss 평균: ${missAvg.toFixed(2)}ms`);

    // ============================================
    // 2. Cache Hit 측정 - 동일 요청 반복 (Redis 반환)
    // ============================================
    console.log('\n[Cache Hit 측정 시작]');
    let hitTimes = [];

    for (let i = 0; i < 5; i++) {
        const res = http.get(
            `${BASE_URL}/api/revenues/me`,
            { headers: headers, tags: { name: 'cache-hit' } }
        );

        check(res, { '수익 현황 조회 성공': (r) => r.status === 200 });
        cacheHitLatency.add(res.timings.duration);
        hitTimes.push(res.timings.duration);
        console.log(`Cache Hit ${i+1}회: ${res.timings.duration.toFixed(2)}ms`);

        sleep(0.1);
    }

    const hitAvg = hitTimes.reduce((a, b) => a + b, 0) / hitTimes.length;
    console.log(`Cache Hit 평균: ${hitAvg.toFixed(2)}ms`);

    // ============================================
    // 결과 요약
    // ============================================
    console.log('\n========================================');
    console.log('  수익 현황 조회 캐시 성능 비교');
    console.log('========================================');
    console.log(`  Cache Miss 평균: ${missAvg.toFixed(2)}ms (DB 집계 쿼리)`);
    console.log(`  Cache Hit  평균: ${hitAvg.toFixed(2)}ms (Redis 반환)`);
    console.log(`  개선율: ${((missAvg - hitAvg) / missAvg * 100).toFixed(1)}% 빠름`);
    console.log('========================================\n');
}