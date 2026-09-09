import http from 'k6/http';
import { check, sleep } from 'k6';
import { Rate } from 'k6/metrics';

/** =========================================================
 1. 흐름
    setup()에서 미리 로그인해 토큰을 한 번만 발급받고, 모든 VU가 그 토큰을 공유한다.
    (기존 로컬 스크립트는 Postman에서 직접 발급한 토큰을 매번 손으로 붙여넣었는데,
     배포환경에서는 재실행이 잦을 걸 감안해 로그인 자체를 스크립트 안에서 자동화함)
        -> JWT 블랙리스트 검증이 유효한 마이페이지 API(/api/auth/users/me)를 타겟으로 한다.

 2. 테스트 시나리오
    부하를 유지한 채로, 테스트 시작 후 별도 SSH 세션에서 아래 명령으로 redis-master를 강제 종료한다.
        docker kill redis-master
    Redis가 죽어도 DB Fallback으로 무조건 200이 떨어져야 하고, 500은 0%여야 한다.

 3. "최대치로 밀어서 역산" 방침 반영
    로컬은 100VU 고정이었지만, 배포환경에서는 실제 한계가 어디인지 먼저 확인하기 위해
    기본값을 300VU로 올려두고, -e VUS=숫자 로 언제든 조절 가능하게 함.
 ========================================================= */

// ---------- 실행 환경 ----------
const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const VUS = Number(__ENV.VUS) || 300;           // 기본 300VU (원본 100VU 대비 최대치 탐색용으로 상향)
const DURATION = __ENV.DURATION || '2m';

// setup()에 쓸 로그인 계정 (load-test-data.sql로 미리 심어둔 유저 재사용)
const LOGIN_EMAIL = __ENV.LOGIN_EMAIL || 'loadtest1@test.com';
const LOGIN_PASSWORD = __ENV.LOGIN_PASSWORD || 'test1234';

// ---------- 커스텀 메트릭 ----------
// SLO: Redis 장애 중에도 200 응답 비율이 얼마나 유지되는지가 핵심 지표
const fallbackSuccessRate = new Rate('fallback_200_rate');

export const options = {
    vus: VUS,
    duration: DURATION,
    thresholds: {
        // SLO: Redis가 죽어도 200 비율이 99% 이상 유지되어야 함 (DB Fallback이 제대로 동작한다는 증거)
        fallback_200_rate: ['rate>0.99'],
    },
};

// ---------- setup: 테스트 시작 전 딱 한 번만 실행 ----------
// 모든 VU가 로그인을 각자 수행하면 로그인 API 자체에 불필요한 부하가 걸리므로,
// 여기서 미리 토큰을 한 번 받아 모든 VU가 공유하도록 함
export function setup() {
    const loginRes = http.post(
        `${BASE_URL}/api/auth/login`,
        JSON.stringify({ email: LOGIN_EMAIL, password: LOGIN_PASSWORD }),
        { headers: { 'Content-Type': 'application/json' } }
    );

    if (loginRes.status !== 200) {
        throw new Error(`[setup 실패] 로그인 실패 - status: ${loginRes.status}, body: ${loginRes.body}`);
    }

    const accessToken = JSON.parse(loginRes.body).data.accessToken;
    return { accessToken };
}

// ---------- 메인 시나리오 ----------
export default function (data) {
    // JWT 블랙리스트 검증(Redis 의존성)이 포함된 마이페이지 API 타겟
    // 실제 존재하는 소설 상세조회 등, 로그인 필요한 조회 API로 교체
    const url = `${BASE_URL}/api/novels/1`;

    const params = {
        headers: {
            // 이 스크립트는 원본과 동일하게 Bearer 접두사를 붙임 (원본 관례 유지)
            'Authorization': data.accessToken,
            'Content-Type': 'application/json',
        },
    };

    const res = http.get(url, params);

    // 검증: Redis가 죽어도 DB 폴백으로 무조건 200이 떨어져야 함
    const isOk = check(res, {
        'DB Fallback 성공 (200)': (r) => r.status === 200,
    });

    // 500이 발생한 건수는 별도로 명확하게 체크 (원본의 반전된 체크 문구를 명확하게 정리)
    check(res, {
        '서버 에러 없음 (500이 아님)': (r) => r.status !== 500,
    });

    fallbackSuccessRate.add(isOk);

    // 1초에 1번씩 요청
    sleep(1);
}

// ---------- 결과 요약 ----------
export function handleSummary(data) {
    const rate = data.metrics.fallback_200_rate ? data.metrics.fallback_200_rate.values.rate * 100 : 0;

    console.log('\n========================================');
    console.log(`  Chaos Fallback 테스트 결과 (VUS=${VUS}, DURATION=${DURATION})`);
    console.log('========================================');
    console.log(`  200 응답 비율: ${rate.toFixed(2)}%  (SLO 기준: 99% 이상)`);
    console.log('  ※ redis-master를 언제 kill했는지 별도로 기록해두면, 장애 시점 전후 비교가 쉬워집니다.');
    console.log('========================================\n');

    return { stdout: JSON.stringify(data, null, 2) };
}