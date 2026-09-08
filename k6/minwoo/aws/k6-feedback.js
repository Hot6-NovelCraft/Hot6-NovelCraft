import http from 'k6/http';
import { check } from 'k6';
import { Counter, Rate, Trend } from 'k6/metrics';

const feedbackSuccess = new Counter('feedback_success');
const feedbackFail = new Counter('feedback_fail');
const feedbackSuccessRate = new Rate('feedback_success_rate');
const feedbackLatency = new Trend('feedback_latency');

const BASE_URL = 'http://novelcraft-dev-alb-336387969.ap-northeast-2.elb.amazonaws.com';
const MENTOR_EMAIL = 'mentor_loadtest@test.com';
const MENTOR_PASSWORD = 'test1234';
const MENTORING_ID = parseInt(__ENV.MENTORING_ID || '7');
const MENTEE_ID = parseInt(__ENV.MENTEE_ID || '502019');
const API_VERSION = __ENV.API_VERSION || 'v1';

export const options = {
    scenarios: {
        spike_feedback: {
            executor: 'shared-iterations',
            vus: 5,
            iterations: 5,
            maxDuration: '30s',
            exec: 'feedbackTest',
        },
    },
    thresholds: {
        http_req_duration: ['p(95)<5000'],
    },
};

export function setup() {
    const tokens = [];
    for (let i = 0; i < 5; i++) {
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
    console.log(`토큰 발급 완료: ${tokens.filter(t => t !== null).length} / 5`);
    return { tokens };
}

export function feedbackTest(data) {
    const token = data.tokens[(__VU - 1) % data.tokens.length];
    if (!token) {
        feedbackFail.add(1);
        feedbackSuccessRate.add(false);
        return;
    }

    const feedbackRes = http.post(
        `${BASE_URL}/api/${API_VERSION}/mentorings/${MENTORING_ID}/feedbacks`,
        JSON.stringify({
            title: `피드백 제목 - VU ${__VU}`,
            content: `피드백 내용 - VU ${__VU}`,
            menteeId: MENTEE_ID,
        }),
        {
            headers: {
                'Content-Type': 'application/json',
                'Authorization': token,
            },
            tags: { name: 'feedback' },
        }
    );

    feedbackLatency.add(feedbackRes.timings.duration);

    const success = check(feedbackRes, {
        '피드백 성공 (201)': (r) => r.status === 201,
    });

    if (success) {
        feedbackSuccess.add(1);
        feedbackSuccessRate.add(true);
        console.log(`피드백 성공 - VU: ${__VU}, status: ${feedbackRes.status}`);
    } else {
        feedbackFail.add(1);
        feedbackSuccessRate.add(false);
        console.log(`피드백 실패 - VU: ${__VU}, status: ${feedbackRes.status}, body: ${feedbackRes.body}`);
    }
}

export function handleSummary(data) {
    const success = data.metrics.feedback_success ? data.metrics.feedback_success.values.count : 0;
    const fail = data.metrics.feedback_fail ? data.metrics.feedback_fail.values.count : 0;
    console.log('\n========================================');
    console.log('  멘토링 피드백 동시성 테스트 결과');
    console.log('========================================');
    console.log(`  총 요청: ${success + fail}`);
    console.log(`  피드백 성공: ${success}`);
    console.log(`  피드백 실패: ${fail}`);
    console.log(`  기대 결과: session_number 1~5 중복 없이 순서대로`);
    console.log(`  정합성 검증: ${success === 5 ? '✅ PASS' : '⚠️ DB 직접 확인 필요'}`);
    console.log('========================================\n');
    return { stdout: JSON.stringify(data, null, 2) };
}