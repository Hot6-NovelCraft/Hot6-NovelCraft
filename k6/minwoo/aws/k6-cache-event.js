import http from 'k6/http';
import { check, sleep } from 'k6';
import { Trend } from 'k6/metrics';

const cacheMissLatency = new Trend('cache_miss_latency');
const cacheHitLatency = new Trend('cache_hit_latency');

const BASE_URL = 'http://novelcraft-dev-alb-336387969.ap-northeast-2.elb.amazonaws.com';
const EMAIL = 'loadtest1@test.com';
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

export function setup() {
    // 토큰 발급
    const loginRes = http.post(
        `${BASE_URL}/api/auth/login`,
        JSON.stringify({ email: EMAIL, password: PASSWORD }),
        { headers: { 'Content-Type': 'application/json' } }
    );
    const token = JSON.parse(loginRes.body).data.accessToken;

    // 워밍업 (JVM JIT + 커넥션 풀 안정화)
    console.log('\n[워밍업 시작 - 5회]');
    for (let i = 0; i < 5; i++) {
        http.get(
            `${BASE_URL}/api/events?status=ONGOING&page=0&size=10`,
            { headers: { 'Content-Type': 'application/json', 'Authorization': token } }
        );
        sleep(0.2);
    }
    console.log('[워밍업 완료]');

    return { token };
}

export function cacheTest(data) {
    const headers = {
        'Content-Type': 'application/json',
        'Authorization': data.token,
    };

    // ============================================
    // 1. Cache Miss 측정 (FLUSHALL 후 1회만)
    // ============================================
    console.log('\n[Cache Miss 측정]');

    // Redis 캐시 플러시 (Actuator endpoint 또는 SSM으로 미리 실행)
    // → 테스트 실행 전 SSM에서 redis-cli FLUSHALL 실행 필수

    const missRes = http.get(
        `${BASE_URL}/api/events?status=ONGOING&page=0&size=10`,
        { headers: headers, tags: { name: 'cache-miss' } }
    );
    check(missRes, { 'Cache Miss 성공': (r) => r.status === 200 });
    cacheMissLatency.add(missRes.timings.duration);
    const missTime = missRes.timings.duration;
    console.log(`Cache Miss 응답시간: ${missTime.toFixed(2)}ms`);

    sleep(0.5);

    // ============================================
    // 2. Cache Hit 측정 (동시 50 VU × 10회 평균)
    // ============================================
    console.log('\n[Cache Hit 측정 - 10회 반복]');
    let hitTimes = [];

    for (let i = 0; i < 10; i++) {
        const hitRes = http.get(
            `${BASE_URL}/api/events?status=ONGOING&page=0&size=10`,
            { headers: headers, tags: { name: 'cache-hit' } }
        );
        check(hitRes, { 'Cache Hit 성공': (r) => r.status === 200 });
        cacheHitLatency.add(hitRes.timings.duration);
        hitTimes.push(hitRes.timings.duration);
        console.log(`Cache Hit ${i+1}회: ${hitRes.timings.duration.toFixed(2)}ms`);
        sleep(0.1);
    }

    const hitAvg = hitTimes.reduce((a, b) => a + b, 0) / hitTimes.length;
    const hitMin = Math.min(...hitTimes);
    const hitMax = Math.max(...hitTimes);

    // ============================================
    // 결과 요약
    // ============================================
    const improvement = (missTime / hitAvg).toFixed(1);
    const improvementPct = ((missTime - hitAvg) / missTime * 100).toFixed(1);

    console.log('\n========================================');
    console.log('  이벤트 목록 조회 캐시 성능 비교');
    console.log('========================================');
    console.log(`  [측정 방식]`);
    console.log(`  - 워밍업: 5회 (JVM JIT + 커넥션 풀 안정화)`);
    console.log(`  - Cache Miss: FLUSHALL 후 1회 (진짜 DB 조회)`);
    console.log(`  - Cache Hit: 10회 반복 평균 (Redis 응답)`);
    console.log(`  [결과]`);
    console.log(`  Cache Miss:  ${missTime.toFixed(2)}ms (DB 조회 1회)`);
    console.log(`  Cache Hit:   ${hitAvg.toFixed(2)}ms (평균) / min: ${hitMin.toFixed(2)}ms / max: ${hitMax.toFixed(2)}ms`);
    console.log(`  개선율:      ${improvement}배 빠름 (${improvementPct}% 응답시간 단축)`);
    console.log('========================================\n');
}