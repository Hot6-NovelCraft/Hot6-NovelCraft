import http from 'k6/http';
import { check } from 'k6';
import { Counter, Rate, Trend } from 'k6/metrics';

// ============================================
// 커스텀 메트릭
// ============================================
const feedbackSuccess = new Counter('feedback_success');
const feedbackFail = new Counter('feedback_fail');
const feedbackLatency = new Trend('feedback_latency');

// ============================================
// 설정
// ============================================
const BASE_URL = 'http://localhost:8080';
const MENTOR_EMAIL = 'mentor_loadtest@test.com';
const MENTOR_PASSWORD = 'test1234';
const MENTORING_ID = parseInt(__ENV.MENTORING_ID || '6');
const API_VERSION = __ENV.API_VERSION || 'v2'; // v1 또는 v2

// ============================================
// 테스트 시나리오
// 동일 멘토가 동시에 5번 피드백 작성 → sessionNumber 중복 여부 확인
// ============================================
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

// ============================================
// 피드백 동시성 테스트
// ============================================
export function feedbackTest() {
    // 멘토로 로그인
    const loginRes = http.post(
        `${BASE_URL}/api/auth/login`,
        JSON.stringify({ email: MENTOR_EMAIL, password: MENTOR_PASSWORD }),
        { headers: { 'Content-Type': 'application/json' }, tags: { name: 'login' } }
    );

    if (loginRes.status !== 200) {
        console.error(`로그인 실패 - VU: ${__VU}, status: ${loginRes.status}`);
        feedbackFail.add(1);
        return;
    }

    const loginBody = JSON.parse(loginRes.body);
    const accessToken = loginBody.data.accessToken;

    // 피드백 작성 요청
    const feedbackRes = http.post(
        `${BASE_URL}/api/${API_VERSION}/mentorings/${MENTORING_ID}/feedbacks`,
        JSON.stringify({
            title: `동시성 테스트 피드백 - VU ${__VU}`,
            content: `동시 요청 테스트입니다. VU: ${__VU}, 시간: ${new Date().toISOString()}`
        }),
        {
            headers: {
                'Content-Type': 'application/json',
                'Authorization': accessToken,
            },
            tags: { name: `feedback-${API_VERSION}` },
        }
    );

    feedbackLatency.add(feedbackRes.timings.duration);

    const success = check(feedbackRes, {
        '피드백 작성 성공 (201)': (r) => r.status === 201,
    });

    if (success) {
        feedbackSuccess.add(1);
        console.log(`피드백 성공 - VU: ${__VU}, status: ${feedbackRes.status}`);
    } else {
        feedbackFail.add(1);
        console.log(`피드백 실패 - VU: ${__VU}, status: ${feedbackRes.status}, body: ${feedbackRes.body}`);
    }
}

// ============================================
// 테스트 종료 후 요약
// ============================================
export function handleSummary(data) {
    const success = data.metrics.feedback_success ? data.metrics.feedback_success.values.count : 0;
    const fail = data.metrics.feedback_fail ? data.metrics.feedback_fail.values.count : 0;

    console.log('\n========================================');
    console.log(`  피드백 동시성 테스트 결과 (${API_VERSION})`);
    console.log('========================================');
    console.log(`  총 요청: ${success + fail}`);
    console.log(`  피드백 성공: ${success}`);
    console.log(`  피드백 실패: ${fail}`);
    if (API_VERSION === 'v1') {
        console.log(`  ⚠️ V1: 동시성 보호 없음 → sessionNumber 중복 가능`);
    } else {
        console.log(`  ✅ V2: 비관적 락 + 유니크 제약 → 1건만 성공 기대`);
    }
    console.log('========================================');
    console.log('  → DB 검증 쿼리:');
    console.log(`  SELECT session_number, COUNT(*) AS cnt FROM mentor_feedbacks WHERE mentorship_id = ${MENTORING_ID} GROUP BY session_number;`);
    console.log(`  SELECT total_sessions FROM mentorships WHERE id = ${MENTORING_ID};`);
    console.log('========================================\n');

    return {
        stdout: JSON.stringify(data, null, 2),
    };
}