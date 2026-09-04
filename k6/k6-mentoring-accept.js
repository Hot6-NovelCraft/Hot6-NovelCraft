import http from 'k6/http';
import { check } from 'k6';
import { Counter, Rate, Trend } from 'k6/metrics';
import { SharedArray } from 'k6/data';

// ============================================
// 커스텀 메트릭
// ============================================
const acceptSuccess = new Counter('accept_success');
const acceptFail = new Counter('accept_fail');
const acceptSuccessRate = new Rate('accept_success_rate');
const acceptLatency = new Trend('accept_latency');

// ============================================
// 설정
// ============================================
const BASE_URL = 'http://localhost:8080';
const MENTOR_EMAIL = 'mentor_loadtest@test.com';
const MENTOR_PASSWORD = 'test1234';

// 멘토십 ID와 멘티 ID 매핑 (ENV로 시작 ID 받음)
const MENTORSHIP_START_ID = parseInt(__ENV.MENTORSHIP_START_ID || '1');
const MENTEE_START_ID = parseInt(__ENV.MENTEE_START_ID || '7');

const mentorships = new SharedArray('mentorships', function () {
    const arr = [];
    for (let i = 0; i < 10; i++) {
        arr.push({
            mentoringId: MENTORSHIP_START_ID + i,
            menteeId: MENTEE_START_ID + i,
        });
    }
    return arr;
});

// ============================================
// 테스트 시나리오
// ============================================
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

// ============================================
// 멘티 수락 동시성 테스트
// ============================================
export function acceptTest() {
    // 멘토로 로그인
    const loginRes = http.post(
        `${BASE_URL}/api/auth/login`,
        JSON.stringify({ email: MENTOR_EMAIL, password: MENTOR_PASSWORD }),
        { headers: { 'Content-Type': 'application/json' }, tags: { name: 'login' } }
    );

    if (loginRes.status !== 200) {
        console.error(`로그인 실패 - VU: ${__VU}, status: ${loginRes.status}`);
        acceptFail.add(1);
        acceptSuccessRate.add(false);
        return;
    }

    const loginBody = JSON.parse(loginRes.body);
    const accessToken = loginBody.data.accessToken;

    // 각 VU에 고유한 멘토십 + 멘티 할당
    const mentorship = mentorships[__VU - 1];

    // 멘티 수락 요청
    const acceptRes = http.patch(
        `${BASE_URL}/api/v2/mentorings/${mentorship.mentoringId}/mentees/${mentorship.menteeId}/accept`,
        null,
        {
            headers: {
                'Content-Type': 'application/json',
                'Authorization': accessToken,
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
        console.log(`수락 성공 - VU: ${__VU}, mentoringId: ${mentorship.mentoringId}`);
    } else {
        acceptFail.add(1);
        acceptSuccessRate.add(false);
        console.log(`수락 실패 - VU: ${__VU}, mentoringId: ${mentorship.mentoringId}, status: ${acceptRes.status}, body: ${acceptRes.body}`);
    }
}

// ============================================
// 테스트 종료 후 요약
// ============================================
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
    console.log(`  정합성 검증: ${success === 3 ? '✅ PASS' : '⚠️ DB 직접 확인 필요'}`);
    console.log('========================================');
    console.log('  → DB 검증 쿼리:');
    console.log("  SELECT status, COUNT(*) FROM mentorships WHERE mentor_id = 1 GROUP BY status;");
    console.log("  SELECT max_mentees, version FROM mentors WHERE id = 1;");
    console.log('========================================\n');

    return {
        stdout: JSON.stringify(data, null, 2),
    };
}