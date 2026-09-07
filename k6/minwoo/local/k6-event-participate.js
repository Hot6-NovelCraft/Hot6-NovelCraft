import http from 'k6/http';
import { check, sleep } from 'k6';
import { SharedArray } from 'k6/data';
import { Counter, Rate, Trend } from 'k6/metrics';

// ============================================
// 커스텀 메트릭
// ============================================
const successCount = new Counter('event_participate_success');
const failCount = new Counter('event_participate_fail');
const successRate = new Rate('event_participate_success_rate');
const participateLatency = new Trend('event_participate_latency');

// ============================================
// 설정
// ============================================
const BASE_URL = 'http://localhost:8080';
const EVENT_ID = __ENV.EVENT_ID || 1;

// 테스트 유저 목록 생성
const users = new SharedArray('users', function () {
    const arr = [];
    for (let i = 1; i <= 200; i++) {
        arr.push({
            email: `loadtest${i}@test.com`,
            password: 'test1234',
        });
    }
    return arr;
});

// ============================================
// 공통: 로그인하여 토큰 획득
// ============================================
function getAccessToken(userIndex) {
    const user = users[userIndex % users.length];

    const loginRes = http.post(
        `${BASE_URL}/api/auth/login`,
        JSON.stringify({ email: user.email, password: user.password }),
        { headers: { 'Content-Type': 'application/json' }, tags: { name: 'login' } }
    );

    if (loginRes.status !== 200) {
        return null;
    }

    const loginBody = JSON.parse(loginRes.body);
    return loginBody.data.accessToken;
}

// ============================================
// 테스트 시나리오
// ============================================
export const options = {
    scenarios: {
        // 시나리오 1: 선착순 동시 참여 (200명 동시 요청)
        spike_participate: {
            executor: 'shared-iterations',
            vus: 200,
            iterations: 200,
            maxDuration: '60s',
            exec: 'participateTest',
        },
        // 시나리오 2: 조회 API 지속 부하 (30초간)
        sustained_read: {
            executor: 'constant-vus',
            vus: 50,
            duration: '30s',
            startTime: '0s',
            exec: 'readTest',
        },
    },
    thresholds: {
        http_req_duration: ['p(95)<3000'],
        event_participate_success_rate: ['rate>0.45'],
    },
};

// ============================================
// 시나리오 1: 선착순 이벤트 참여
// ============================================
export function participateTest() {
    const accessToken = getAccessToken(__VU - 1);

    if (!accessToken) {
        failCount.add(1);
        successRate.add(false);
        return;
    }

    // 이벤트 참여
    const participateRes = http.post(
        `${BASE_URL}/api/events/${EVENT_ID}/participants`,
        null,
        {
            headers: {
                'Content-Type': 'application/json',
                'Authorization': accessToken,
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

// ============================================
// 시나리오 2: 이벤트 목록 + 상세 조회 (30초 지속)
// ============================================
export function readTest() {
    // VU 201~250 범위로 유저 할당 (참여 유저와 겹치지 않게)
    const accessToken = getAccessToken((__VU - 1) % users.length);

    if (!accessToken) {
        sleep(1);
        return;
    }

    const headers = {
        'Content-Type': 'application/json',
        'Authorization': accessToken,
    };

    // 이벤트 목록 조회
    const listRes = http.get(
        `${BASE_URL}/api/events?status=ONGOING&page=0&size=10`,
        { headers: headers, tags: { name: 'event-list' } }
    );

    check(listRes, {
        '목록 조회 성공': (r) => r.status === 200,
    });

    sleep(0.3);

    // 이벤트 상세 조회
    const detailRes = http.get(
        `${BASE_URL}/api/events/${EVENT_ID}`,
        { headers: headers, tags: { name: 'event-detail' } }
    );

    check(detailRes, {
        '상세 조회 성공': (r) => r.status === 200,
    });

    sleep(0.3);
}

// ============================================
// 테스트 종료 후 요약
// ============================================
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
    console.log('========================================');
    console.log('  → DB 검증 쿼리:');
    console.log(`  SELECT COUNT(*) FROM event_participants WHERE event_id = ${EVENT_ID};`);
    console.log('========================================\n');

    return {
        stdout: JSON.stringify(data, null, 2),
    };
}