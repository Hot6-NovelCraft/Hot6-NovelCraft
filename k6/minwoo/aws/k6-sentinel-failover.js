import http from 'k6/http';
import { check, sleep } from 'k6';

const BASE_URL = 'http://novelcraft-dev-alb-336387969.ap-northeast-2.elb.amazonaws.com';

export const options = {
    scenarios: {
        sustained_load: {
            executor: 'constant-vus',
            vus: 50,
            duration: '5m',
        }
    },
    thresholds: {
        // Failover 구간 일시 에러 허용 (10% 미만)
        http_req_failed: ['rate<0.1'],
    }
};

export function setup() {
    const loginRes = http.post(
        `${BASE_URL}/api/auth/login`,
        JSON.stringify({ email: 'loadtest502@test.com', password: 'test1234' }),
        { headers: { 'Content-Type': 'application/json' } }
    );

    // 로그인 실패 시 즉시 중단
    if (loginRes.status !== 200) {
        console.error(`[로그인 실패] status=${loginRes.status}, body=${loginRes.body}`);
        return null;
    }

    const body = JSON.parse(loginRes.body);
    const token = `Bearer ${body.data.accessToken}`;
    console.log(`[토큰 발급 완료] 테스트 시작 — ${new Date().toISOString()}`);
    return { token };
}

export default function (data) {
    // setup 실패 시 skip
    if (!data || !data.token) {
        sleep(1);
        return;
    }

    const headers = {
        'Content-Type': 'application/json',
        'Authorization': data.token
    };

    // 이벤트 목록 조회 (캐시 Hit 시나리오 — Redis 의존 엔드포인트)
    const res = http.get(
        `${BASE_URL}/api/events?status=ONGOING&page=0&size=10`,
        { headers }
    );

    check(res, {
        '200 응답': (r) => r.status === 200,
        '401 아님 (토큰 유효)': (r) => r.status !== 401,
    });

    // 에러 시 로깅 (Failover 에러 vs 다른 에러 구분)
    if (res.status !== 200) {
        console.log(`[에러] status=${res.status}, time=${new Date().toISOString()}`);
    }

    sleep(0.5);
}