import http from 'k6/http';
import { check, sleep } from 'k6';
import { Counter, Rate, Trend } from 'k6/metrics';

const successCount = new Counter('event_participate_success');
const failCount = new Counter('event_participate_fail');
const successRate = new Rate('event_participate_success_rate');
const participateLatency = new Trend('event_participate_latency');

const BASE_URL = 'http://novelcraft-dev-alb-336387969.ap-northeast-2.elb.amazonaws.com';
const EVENT_ID = __ENV.EVENT_ID || 1;

export const options = {
    scenarios: {
        spike_participate: {
            executor: 'shared-iterations',
            vus: 200,
            iterations: 200,
            maxDuration: '60s',
            exec: 'participateTest',
        },
        sustained_read: {
            executor: 'constant-vus',
            vus: 50,
            duration: '60s',
            startTime: '0s',
            exec: 'readTest',
        },
    },
    thresholds: {
        http_req_duration: ['p(95)<30000'],
        event_participate_success_rate: ['rate>0.01'],
    },
};

export function setup() {
    // 테스트 시작 전 200명 토큰 미리 발급
    const tokens = [];
    for (let i = 1; i <= 200; i++) {
        const loginRes = http.post(
            `${BASE_URL}/api/auth/login`,
            JSON.stringify({ email: `author${i}@test.com`, password: 'test1234' }),
            { headers: { 'Content-Type': 'application/json' } }
        );
        if (loginRes.status === 200) {
            const body = JSON.parse(loginRes.body);
            tokens.push(body.data.accessToken);
        } else {
            tokens.push(null);
        }
    }
    console.log(`토큰 발급 완료: ${tokens.filter(t => t !== null).length} / 200`);
    return { tokens };
}

export function participateTest(data) {
    const token = data.tokens[(__VU - 1) % data.tokens.length];
    if (!token) {
        failCount.add(1);
        successRate.add(false);
        return;
    }
    const participateRes = http.post(
        `${BASE_URL}/api/events/${EVENT_ID}/participants`,
        null,
        {
            headers: {
                'Content-Type': 'application/json',
                'Authorization': token,
            },
            tags: { name: 'participate' },
        }
    );
    participateLatency.add(participateRes.timings.duration);
    const participated = check(participateRes, {
        '참여 성공 (201)': (r) => r.status === 201,
    });
    if (participated) {
        successCount.add(1);
        successRate.add(true);
    } else {
        failCount.add(1);
        successRate.add(false);
    }
}

export function readTest(data) {
    const token = data.tokens[(__VU - 1) % data.tokens.length];
    if (!token) {
        sleep(1);
        return;
    }
    const headers = {
        'Content-Type': 'application/json',
        'Authorization': token,
    };
    const listRes = http.get(
        `${BASE_URL}/api/events?status=ONGOING&page=0&size=10`,
        { headers: headers, tags: { name: 'event-list' } }
    );
    check(listRes, { '목록 조회 성공': (r) => r.status === 200 });
    sleep(0.3);
    const detailRes = http.get(
        `${BASE_URL}/api/events/${EVENT_ID}`,
        { headers: headers, tags: { name: 'event-detail' } }
    );
    check(detailRes, { '상세 조회 성공': (r) => r.status === 200 });
    sleep(0.3);
}

export function handleSummary(data) {
    const success = data.metrics.event_participate_success ? data.metrics.event_participate_success.values.count : 0;
    const fail = data.metrics.event_participate_fail ? data.metrics.event_participate_fail.values.count : 0;
    console.log('\n========================================');
    console.log('  선착순 이벤트 부하 테스트 결과');
    console.log('========================================');
    console.log(`  총 요청: ${success + fail}`);
    console.log(`  참여 성공: ${success}`);
    console.log(`  참여 실패: ${fail}`);
    console.log(`  기대 성공 수: 100 (maxParticipants)`);
    console.log(`  정합성 검증: ${success === 100 ? '✅ PASS' : '⚠️ DB 직접 확인 필요'}`);
    console.log('========================================\n');
    return { stdout: JSON.stringify(data, null, 2) };
}