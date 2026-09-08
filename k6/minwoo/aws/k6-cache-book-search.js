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
    const loginRes = http.post(
        `${BASE_URL}/api/auth/login`,
        JSON.stringify({ email: EMAIL, password: PASSWORD }),
        { headers: { 'Content-Type': 'application/json' } }
    );
    const token = JSON.parse(loginRes.body).data.accessToken;

    console.log('\n[워밍업 시작 - 5회]');
    for (let i = 0; i < 5; i++) {
        http.get(
            `${BASE_URL}/api/v1/national-library/books/search?query=%EC%8A%A4%ED%94%84%EB%A7%81&page=1&size=10`,
            { headers: { 'Content-Type': 'application/json', 'Authorization': token } }
        );
        sleep(0.5);
    }
    console.log('[워밍업 완료 → SSM에서 FLUSHALL 실행하세요]');
    sleep(10);
    return { token };
}

export function cacheTest(data) {
    const headers = {
        'Content-Type': 'application/json',
        'Authorization': data.token,
    };

    console.log('\n[Cache Miss 측정]');
    const missRes = http.get(
        `${BASE_URL}/api/v1/national-library/books/search?query=%EC%8A%A4%ED%94%84%EB%A7%81&page=1&size=10`,
        { headers: headers, tags: { name: 'cache-miss' } }
    );
    check(missRes, { 'Cache Miss 성공': (r) => r.status === 200 });
    cacheMissLatency.add(missRes.timings.duration);
    const missTime = missRes.timings.duration;
    console.log(`Cache Miss 응답시간: ${missTime.toFixed(2)}ms (국립도서관 외부 API 호출)`);

    sleep(0.5);

    console.log('\n[Cache Hit 측정 - 10회 반복]');
    let hitTimes = [];
    for (let i = 0; i < 10; i++) {
        const hitRes = http.get(
            `${BASE_URL}/api/v1/national-library/books/search?query=%EC%8A%A4%ED%94%84%EB%A7%81&page=1&size=10`,
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
    const improvement = (missTime / hitAvg).toFixed(1);
    const improvementPct = ((missTime - hitAvg) / missTime * 100).toFixed(1);

    console.log('\n========================================');
    console.log('  도서 검색 캐시 성능 비교');
    console.log('========================================');
    console.log('  [측정 방식]');
    console.log('  - 워밍업: 5회 (JVM JIT + 커넥션 풀 안정화)');
    console.log('  - Cache Miss: FLUSHALL 후 1회 (국립도서관 외부 API 호출)');
    console.log('  - Cache Hit: 10회 반복 평균 (Redis 응답)');
    console.log('  [결과]');
    console.log(`  Cache Miss:  ${missTime.toFixed(2)}ms (외부 API 호출 1회)`);
    console.log(`  Cache Hit:   ${hitAvg.toFixed(2)}ms (평균) / min: ${hitMin.toFixed(2)}ms / max: ${hitMax.toFixed(2)}ms`);
    console.log(`  개선율:      ${improvement}배 빠름 (${improvementPct}% 응답시간 단축)`);
    console.log('========================================');
}