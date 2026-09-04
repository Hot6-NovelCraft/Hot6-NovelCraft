import http from 'k6/http';
import { check } from 'k6';
import { Counter, Rate, Trend } from 'k6/metrics';

// ============================================
// 커스텀 메트릭
// ============================================
const withdrawalSuccess = new Counter('withdrawal_success');
const withdrawalFail = new Counter('withdrawal_fail');
const withdrawalSuccessRate = new Rate('withdrawal_success_rate');
const withdrawalLatency = new Trend('withdrawal_latency');

// ============================================
// 설정
// ============================================
const BASE_URL = 'http://localhost:8080';
const AUTHOR_EMAIL = 'author_loadtest@test.com';
const AUTHOR_PASSWORD = 'test1234';
const WITHDRAWAL_AMOUNT = 1000000; // 100만원 전액 환전 시도

// ============================================
// 테스트 시나리오
// 동일 유저가 동시에 10번 환전 요청 → 1번만 성공해야 함
// ============================================
export const options = {
    scenarios: {
        spike_withdrawal: {
            executor: 'shared-iterations',
            vus: 10,
            iterations: 10,
            maxDuration: '30s',
            exec: 'withdrawalTest',
        },
    },
    thresholds: {
        http_req_duration: ['p(95)<5000'],
    },
};

// ============================================
// 환전 동시성 테스트
// ============================================
export function withdrawalTest() {
    // 로그인
    const loginRes = http.post(
        `${BASE_URL}/api/auth/login`,
        JSON.stringify({ email: AUTHOR_EMAIL, password: AUTHOR_PASSWORD }),
        { headers: { 'Content-Type': 'application/json' }, tags: { name: 'login' } }
    );

    if (loginRes.status !== 200) {
        console.error(`로그인 실패 - VU: ${__VU}, status: ${loginRes.status}`);
        withdrawalFail.add(1);
        withdrawalSuccessRate.add(false);
        return;
    }

    const loginBody = JSON.parse(loginRes.body);
    const accessToken = loginBody.data.accessToken;

    // 환전 신청
    const withdrawalRes = http.post(
        `${BASE_URL}/api/revenues/me/exchanges`,
        JSON.stringify({ requestAmount: WITHDRAWAL_AMOUNT }),
        {
            headers: {
                'Content-Type': 'application/json',
                'Authorization': accessToken,
            },
            tags: { name: 'withdrawal' },
        }
    );

    withdrawalLatency.add(withdrawalRes.timings.duration);

    const success = check(withdrawalRes, {
        '환전 성공 (201)': (r) => r.status === 201,
    });

    if (success) {
        withdrawalSuccess.add(1);
        withdrawalSuccessRate.add(true);
        console.log(`환전 성공 - VU: ${__VU}`);
    } else {
        withdrawalFail.add(1);
        withdrawalSuccessRate.add(false);
        console.log(`환전 실패 - VU: ${__VU}, status: ${withdrawalRes.status}, body: ${withdrawalRes.body}`);
    }
}

// ============================================
// 테스트 종료 후 요약
// ============================================
export function handleSummary(data) {
    const success = data.metrics.withdrawal_success ? data.metrics.withdrawal_success.values.count : 0;
    const fail = data.metrics.withdrawal_fail ? data.metrics.withdrawal_fail.values.count : 0;

    console.log('\n========================================');
    console.log('  환전 동시성 부하 테스트 결과');
    console.log('========================================');
    console.log(`  총 요청: ${success + fail}`);
    console.log(`  환전 성공: ${success}`);
    console.log(`  환전 실패: ${fail}`);
    console.log(`  기대 성공 수: 1 (잔액 부족으로 1번만 성공)`);
    console.log(`  정합성 검증: ${success === 1 ? '✅ PASS' : '⚠️ DB 직접 확인 필요'}`);
    console.log('========================================');
    console.log('  → DB 검증 쿼리:');
    console.log('  SELECT type, COUNT(*), SUM(amount) FROM revenues');
    console.log("  WHERE author_id = (SELECT id FROM users WHERE email = 'author_loadtest@test.com')");
    console.log('  GROUP BY type;');
    console.log('========================================\n');

    return {
        stdout: JSON.stringify(data, null, 2),
    };
}