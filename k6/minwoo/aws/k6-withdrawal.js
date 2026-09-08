import http from 'k6/http';
import { check } from 'k6';
import { Counter, Rate, Trend } from 'k6/metrics';

const withdrawalSuccess = new Counter('withdrawal_success');
const withdrawalFail = new Counter('withdrawal_fail');
const withdrawalSuccessRate = new Rate('withdrawal_success_rate');
const withdrawalLatency = new Trend('withdrawal_latency');

const BASE_URL = 'http://novelcraft-dev-alb-336387969.ap-northeast-2.elb.amazonaws.com';
const AUTHOR_EMAIL = 'author_loadtest@test.com';
const AUTHOR_PASSWORD = 'test1234';
const WITHDRAWAL_AMOUNT = 1000000;

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

export function setup() {
    // 같은 유저로 10개 토큰 미리 발급
    const tokens = [];
    for (let i = 0; i < 10; i++) {
        const loginRes = http.post(
            `${BASE_URL}/api/auth/login`,
            JSON.stringify({ email: AUTHOR_EMAIL, password: AUTHOR_PASSWORD }),
            { headers: { 'Content-Type': 'application/json' } }
        );
        if (loginRes.status === 200) {
            const body = JSON.parse(loginRes.body);
            tokens.push(body.data.accessToken);
        } else {
            tokens.push(null);
        }
    }
    console.log(`토큰 발급 완료: ${tokens.filter(t => t !== null).length} / 10`);
    return { tokens };
}

export function withdrawalTest(data) {
    const token = data.tokens[(__VU - 1) % data.tokens.length];
    if (!token) {
        withdrawalFail.add(1);
        withdrawalSuccessRate.add(false);
        return;
    }

    const withdrawalRes = http.post(
        `${BASE_URL}/api/revenues/me/exchanges`,
        JSON.stringify({ requestAmount: WITHDRAWAL_AMOUNT }),
        {
            headers: {
                'Content-Type': 'application/json',
                'Authorization': token,
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
    console.log('========================================\n');
    return { stdout: JSON.stringify(data, null, 2) };
}