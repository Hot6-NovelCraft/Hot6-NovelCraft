import http from 'k6/http';
import { check, sleep } from 'k6';
import { SharedArray } from 'k6/data';
import { Trend, Rate } from 'k6/metrics';

// ============================================
// [설정] 실행 환경
// ============================================
// BASE_URL: 로컬은 기본값(localhost:8080) 사용, 배포환경은 실행 시 -e BASE_URL=https://<ALB주소> 로 주입
const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';

// STAGE: smoke / load / stress 중 하나를 -e STAGE=load 로 선택 (기본값 load)
// 세 단계를 한 번에 돌리지 않고 STAGE별로 따로 실행 -> 단계별 결과를 깔끔하게 분리해서 기록하기 위함
const STAGE = __ENV.STAGE || 'load';

// ============================================
// [커스텀 메트릭] SLO를 도메인별로 따로 측정하기 위한 지표
// ============================================
// 로그인은 bcrypt 연산이 섞여있어 조회 API보다 무거우므로 별도 Trend로 분리 측정
const loginLatency = new Trend('login_latency');
const searchLatency = new Trend('search_latency');
const rankingLatency = new Trend('ranking_latency');
const recommendLatency = new Trend('recommend_latency');

// SLI: "로그인->검색->랭킹->추천" 4단계가 전부 성공해야 저니 성공으로 집계
const journeySuccessRate = new Rate('journey_success_rate');

// ============================================
// [테스트 데이터] load-test-data.sql로 미리 심어둔 200명 재사용
// ============================================
const users = new SharedArray('users', function () {
    const arr = [];
    for (let i = 1; i <= 200; i++) {
        arr.push({ email: `loadtest${i}@test.com`, password: 'test1234' });
    }
    return arr;
});

// 검색 키워드를 매번 다르게 줘야 함 (동일 키워드만 반복하면 캐시 효과로 지연시간이 실제보다 좋게 왜곡됨)
const searchKeywords = ['백산', '판타지', '로맨스', '무협', '회귀', '환생', '전생', '히어로'];

// ============================================
// [단계별 부하 프로파일] 앞서 합의한 SLO 설계 문서 기준값
// ============================================
const STAGE_CONFIGS = {
    // Smoke: 스크립트/배포환경 정상 동작만 확인 (최소 VU)
    smoke: {
        executor: 'constant-vus',
        vus: 2,
        duration: '1m',
    },
    // Load(Average): DAU 1만 / 피크 동접 300명 가정 중 "평균 트래픽"인 50VU를 5분간 재현
    load: {
        executor: 'ramping-vus',
        startVUs: 0,
        stages: [
            { duration: '1m', target: 50 }, // ramp-up
            { duration: '3m', target: 50 }, // 유지
            { duration: '1m', target: 0 },  // ramp-down
        ],
    },
    // Stress: 50 -> 400까지 단계적으로 올려서 SLO가 어디서 깨지는지 탐색
    stress: {
        executor: 'ramping-vus',
        startVUs: 50,
        stages: [
            { duration: '2m', target: 50 },
            { duration: '2m', target: 100 },
            { duration: '2m', target: 200 },
            { duration: '2m', target: 300 },
            { duration: '2m', target: 400 },
        ],
    },
};

export const options = {
    scenarios: {
        user_journey: {
            ...STAGE_CONFIGS[STAGE],
            exec: 'userJourney',
        },
    },
    thresholds: {
        // SLO① 가용성: 전체 요청 에러율 0.5% 미만 (99.5% 이상 성공)
        http_req_failed: ['rate<0.005'],

        // SLO② 지연시간: 조회성 API(검색/랭킹/추천)는 p95<300ms, p99<800ms
        search_latency: ['p(95)<300', 'p(99)<800'],
        ranking_latency: ['p(95)<300', 'p(99)<800'],
        recommend_latency: ['p(95)<300', 'p(99)<800'],

        // 로그인은 bcrypt 검증이 있어 조회 API보다 여유있게 p95<500ms
        login_latency: ['p(95)<500'],

        // SLI: 4단계 전체 저니 성공률 99% 이상
        journey_success_rate: ['rate>0.99'],
    },
};

// ============================================
// [메인 시나리오] 로그인 -> 검색 -> 랭킹 -> AI 추천
// ============================================
export function userJourney() {
    // VU 번호 기준으로 200명 풀에서 유저를 순환 배정 (기존 k6-event-participate.js와 동일한 컨벤션)
    const user = users[(__VU - 1) % users.length];

    // ---------- 1단계: 로그인 ----------
    const loginRes = http.post(
        `${BASE_URL}/api/auth/login`,
        JSON.stringify({ email: user.email, password: user.password }),
        { headers: { 'Content-Type': 'application/json' }, tags: { name: 'login' } }
    );
    loginLatency.add(loginRes.timings.duration);

    const loginOk = check(loginRes, {
        '로그인 200': (r) => r.status === 200,
    });

    if (!loginOk) {
        // 로그인이 실패하면 이후 단계는 의미가 없으므로 저니 실패로 기록하고 종료
        journeySuccessRate.add(false);
        sleep(1);
        return;
    }

    const token = JSON.parse(loginRes.body).data.accessToken;
    const headers = {
        'Content-Type': 'application/json',
        'Authorization': token, // 기존 스크립트와 동일하게 Bearer 접두사 없이 그대로 사용
    };

    sleep(0.5); // 로그인 직후 화면을 보는 실제 사용자 행동을 흉내낸 think time

    // ---------- 2단계: 검색 ----------
    const keyword = searchKeywords[Math.floor(Math.random() * searchKeywords.length)];
    const searchRes = http.get(
        `${BASE_URL}/api/search/v2/novels?keyword=${encodeURIComponent(keyword)}`,
        { headers, tags: { name: 'search' } }
    );
    searchLatency.add(searchRes.timings.duration);

    const searchOk = check(searchRes, {
        '검색 200': (r) => r.status === 200,
    });

    sleep(0.5);

    // ---------- 3단계: 랭킹 조회 (실시간/주간 랜덤 선택) ----------
    const rankingType = Math.random() < 0.5 ? 'realtime' : 'weekly';
    const rankingRes = http.get(
        `${BASE_URL}/api/novels/ranking?type=${rankingType}`,
        { headers, tags: { name: 'ranking' } }
    );
    rankingLatency.add(rankingRes.timings.duration);

    const rankingOk = check(rankingRes, {
        '랭킹 200': (r) => r.status === 200,
    });

    sleep(0.5);

    // ---------- 4단계: AI 추천 ----------
    const recommendRes = http.get(
        `${BASE_URL}/api/ai/recommendation`,
        { headers, tags: { name: 'recommend' } }
    );
    recommendLatency.add(recommendRes.timings.duration);

    const recommendOk = check(recommendRes, {
        '추천 200': (r) => r.status === 200,
    });

    // 4단계가 전부 성공했을 때만 "저니 성공"으로 집계 (SLI 정의 그대로)
    journeySuccessRate.add(loginOk && searchOk && rankingOk && recommendOk);

    sleep(1); // 다음 이터레이션 전 think time
}

// ============================================
// [결과 요약] 실행 종료 시 SLO 대비 결과를 콘솔에 바로 출력
// ============================================
export function handleSummary(data) {
    const errorRate = data.metrics.http_req_failed ? data.metrics.http_req_failed.values.rate * 100 : 0;
    const journeyRate = data.metrics.journey_success_rate ? data.metrics.journey_success_rate.values.rate * 100 : 0;

    console.log('\n========================================');
    console.log(`  시나리오 A 결과 (STAGE=${STAGE}, BASE_URL=${BASE_URL})`);
    console.log('========================================');
    console.log(`  에러율: ${errorRate.toFixed(2)}%  (SLO 기준: 0.5% 미만)`);
    console.log(`  저니 성공률: ${journeyRate.toFixed(2)}%  (SLO 기준: 99% 이상)`);
    console.log('========================================\n');

    return {
        stdout: JSON.stringify(data, null, 2),
    };
}