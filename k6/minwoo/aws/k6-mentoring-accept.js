import http from 'k6/http';
import { check } from 'k6';
import { Counter, Rate, Trend } from 'k6/metrics';

const acceptSuccess = new Counter('accept_success');
const acceptFail = new Counter('accept_fail');
const acceptSuccessRate = new Rate('accept_success_rate');
const acceptLatency = new Trend('accept_latency');

const BASE_URL = 'http://novelcraft-dev-alb-336387969.ap-northeast-2.elb.amazonaws.com';
const MENTOR_EMAIL = 'mentor_loadtest@test.com';
const MENTOR_PASSWORD = 'test1234';

const MENTORSHIP_START_ID = parseInt(__ENV.MENTORSHIP_START_ID || '1');
const MENTEE_START_ID = parseInt(__ENV.MENTEE_START_ID || '502013');

export const options = {
    scenarios: {
        spike_accept: {
            executor: 'shared-iterations',
            vus: 10,
            iterations: 10,
            maxDuration: '30s',
            exec: 'acceptTest',
        },
    },
    thresholds: {
        http_req_duration: ['p(95)<5000'],
    },
};

export function setup() {
    // 멘토 토큰 10개 미리 발급
    const tokens = [];
    for (let i = 0; i < 10; i++) {
        const loginRes = http.post(
            `${BASE_URL}/api/auth/login`,
            JSON.stringify({ email: MENTOR_EMAIL, password: MENTOR_PASSWORD }),
            { headers: { 'Content-Type': 'application/json' } }
        );
        if (loginRes.status === 200) {
            tokens.push(JSON.parse(loginRes.body).data.accessToken);
        } else {
            tokens.push(null);
        }
    }
    console.log(`토큰 발급 완료: ${tokens.filter(t => t !== null).length} / 10`);
    return { tokens };
}

export function acceptTest(data) {
    const token = data.tokens[(__VU - 1) % data.tokens.length];
    if (!token) {
        acceptFail.add(1);
        acceptSuccessRate.add(false);
        return;
    }

    const mentoringId = MENTORSHIP_START_ID + (__VU - 1);
    const menteeId = MENTEE_START_ID + (__VU - 1);

    const acceptRes = http.patch(
        `${BASE_URL}/api/v2/mentorings/${mentoringId}/mentees/${menteeId}/accept`,
        null,
        {
            headers: {
                'Content-Type': 'application/json',
                'Authorization': token,
            },
            tags: { name: 'accept-mentee' },
        }
    );

    acceptLatency.add(acceptRes.timings.duration);

    const success = check(acceptRes, {
        '수락 성공 (200)': (r) => r.status === 200,
    });

    if (success) {
        acceptSuccess.add(1);
        acceptSuccessRate.add(true);
        console.log(`수락 성공 - VU: ${__VU}, mentoringId: ${mentoringId}`);
    } else {
        acceptFail.add(1);
        acceptSuccessRate.add(false);
        console.log(`수락 실패 - VU: ${__VU}, mentoringId: ${mentoringId}, status: ${acceptRes.status}, body: ${acceptRes.body}`);
    }
}

export function handleSummary(data) {
    const success = data.metrics.accept_success ? data.metrics.accept_success.values.count : 0;
    const fail = data.metrics.accept_fail ? data.metrics.accept_fail.values.count : 0;

    console.log('\n========================================');
    console.log('  멘토링 멘티 수락 동시성 테스트 결과');
    console.log('========================================');
    console.log(`  총 요청: ${success + fail}`);
    console.log(`  수락 성공: ${success}`);
    console.log(`  수락 실패: ${fail}`);
    console.log(`  기대 성공 수: 3 (maxMentees)`);
    console.log(`  정합성 검증: ${success <= 3 ? '✅ PASS' : '⚠️ 초과 발생!'}`);
    console.log('========================================\n');

    return { stdout: JSON.stringify(data, null, 2) };
}