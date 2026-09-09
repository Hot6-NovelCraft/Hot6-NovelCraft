/**
 * NovelCraft - Phase 1: Baseline 성능 측정
 *
 * 목적: 낮은 부하에서 정상 응답 수치(P50/P95/P99) 확보
 *       → Stress Test의 비교 기준선 설정
 *
 * 실행: k6 run load-test-baseline.js
 */

import http from 'k6/http';
import { check, sleep, group } from 'k6';
import { Trend, Rate, Counter } from 'k6/metrics';

// ─── 커스텀 메트릭 ───────────────────────────────────────────────
const novelListLatency    = new Trend('novel_list_latency',    true);
const novelDetailLatency  = new Trend('novel_detail_latency',  true);
const episodeReadLatency  = new Trend('episode_read_latency',  true);
const rankingLatency      = new Trend('ranking_latency',       true);
const searchLatency       = new Trend('search_latency',        true);

const errorRate           = new Rate('error_rate');
const totalRequests       = new Counter('total_requests');

// ─── 환경 설정 ───────────────────────────────────────────────────
const BASE_URL  = __ENV.BASE_URL  || 'http://novelcraft-dev-alb-336387969.ap-northeast-2.elb.amazonaws.com';
const NOVEL_IDS = (__ENV.NOVEL_IDS || '1,2,3,4,5').split(',').map(Number);
const EP_IDS    = (__ENV.EP_IDS   || '1,2,3,4,5,6,7,8,9,10,11,12,13,14,15').split(',').map(Number); // 무료 회차만 (is_free=1)

// ─── 옵션: 낮은 부하로 Baseline 측정 ────────────────────────────
export const options = {
    scenarios: {
        baseline_readers: {
            executor: 'constant-vus',
            vus: 20,          // 낮은 VU → 정상 상태 수치 확보
            duration: '3m',
            exec: 'readerJourney',
        },
    },
    thresholds: {
        // Baseline에서는 여유있게 설정 (한계 탐색 목적 아님)
        http_req_duration:   ['p(95)<2000'],
        http_req_failed:     ['rate<0.01'],
        novel_list_latency:  ['p(95)<1000', 'p(99)<2000'],
        novel_detail_latency:['p(95)<1000', 'p(99)<2000'],
        episode_read_latency:['p(95)<1500', 'p(99)<3000'],
        error_rate:          ['rate<0.01'],
    },
};

// ─── Setup: 테스트 계정 토큰 준비 ────────────────────────────────
export function setup() {
    const tokens = [];

    // Baseline은 20 VU → 30개 토큰 준비 (여유분)
    // READER 계정: loadtest502@test.com ~ (502번부터 READER role)
    for (let i = 0; i < 30; i++) {
        const email = `loadtest${502 + i}@test.com`;
        const res = http.post(
            `${BASE_URL}/api/auth/login`,
            JSON.stringify({ email: email, password: 'test1234' }),
            { headers: { 'Content-Type': 'application/json' } }
        );
        if (res.status === 200) {
            const body = JSON.parse(res.body);
            tokens.push(body.data.accessToken);
        } else {
            tokens.push(null);
            console.warn(`[setup] ${email} 로그인 실패: ${res.status}`);
        }
    }

    const valid = tokens.filter(t => t !== null).length;
    console.log(`[setup] 토큰 준비 완료: ${valid} / 30`);

    if (valid < 15) {
        throw new Error(`유효 토큰 부족 (${valid}개) - 테스트 데이터 확인 필요`);
    }

    return { tokens, novelIds: NOVEL_IDS, epIds: EP_IDS };
}

// ─── 메인 시나리오: 일반 독자 여정 ──────────────────────────────
export function readerJourney(data) {
    const token   = data.tokens[(__VU - 1) % data.tokens.length];
    const headers = buildHeaders(token);
    const novelId = randomItem(data.novelIds);
    const epId    = randomItem(data.epIds);

    // Step 1: 랭킹 조회 (메인 페이지 진입) - 토큰 없으면 스킵
    if (!token) { return; }
    group('랭킹 조회', () => {
        const res = http.get(`${BASE_URL}/api/novels/ranking`, { headers });
        trackMetric(res, rankingLatency, '랭킹 조회 성공');
    });
    sleep(rand(0.5, 1.0));

    // Step 2: 소설 목록 조회
    group('소설 목록 조회', () => {
        const res = http.get(`${BASE_URL}/api/v2/novels?page=0&size=20`, { headers });
        trackMetric(res, novelListLatency, '소설 목록 조회 성공');
    });
    sleep(rand(0.5, 1.5));

    // Step 3: 소설 상세 조회
    group('소설 상세 조회', () => {
        const res = http.get(`${BASE_URL}/api/novels/${novelId}`, { headers });
        trackMetric(res, novelDetailLatency, '소설 상세 조회 성공');
    });
    sleep(rand(0.5, 1.0));

    // Step 4: 회차 목록 조회
    group('회차 목록 조회', () => {
        const res = http.get(`${BASE_URL}/api/novels/${novelId}/episodes?page=0&size=20`, { headers });
        trackMetric(res, episodeReadLatency, '회차 목록 조회 성공');
    });
    sleep(rand(1.0, 2.0));

    // Step 5: 회차 본문 열람 (핵심 - 가장 무거운 API)
    group('회차 본문 열람', () => {
        const res = http.get(`${BASE_URL}/api/v2/episodes/${epId}`, { headers });
        trackMetric(res, episodeReadLatency, '회차 본문 열람 성공');
    });
    sleep(rand(2.0, 4.0)); // 실제 독자는 회차 읽는 시간 있음
}

// ─── 유틸 ────────────────────────────────────────────────────────
function buildHeaders(token) {
    const h = { 'Content-Type': 'application/json' };
    if (token) h['Authorization'] = token; // 기존 스크립트 방식: accessToken 값 그대로 (Bearer prefix 포함 여부는 서버 응답 확인)
    return h;
}

function trackMetric(res, trend, checkName) {
    totalRequests.add(1);
    trend.add(res.timings.duration);
    const ok = check(res, { [checkName]: (r) => r.status === 200 });
    errorRate.add(!ok);
}

function randomItem(arr) {
    return arr[Math.floor(Math.random() * arr.length)];
}

function rand(min, max) {
    return Math.random() * (max - min) + min;
}

// ─── 결과 요약 ───────────────────────────────────────────────────
export function handleSummary(data) {
    const m = data.metrics;

    const summary = `
========================================================
  NovelCraft - Baseline 성능 측정 결과
========================================================
  VU: 20 | Duration: 3min

  [소설 목록 조회]
  P50: ${p(m, 'novel_list_latency', 'p(50)')}ms
  P95: ${p(m, 'novel_list_latency', 'p(95)')}ms
  P99: ${p(m, 'novel_list_latency', 'p(99)')}ms

  [소설 상세 조회]
  P50: ${p(m, 'novel_detail_latency', 'p(50)')}ms
  P95: ${p(m, 'novel_detail_latency', 'p(95)')}ms
  P99: ${p(m, 'novel_detail_latency', 'p(99)')}ms

  [회차 열람]
  P50: ${p(m, 'episode_read_latency', 'p(50)')}ms
  P95: ${p(m, 'episode_read_latency', 'p(95)')}ms
  P99: ${p(m, 'episode_read_latency', 'p(99)')}ms

  [랭킹 조회]
  P50: ${p(m, 'ranking_latency', 'p(50)')}ms
  P95: ${p(m, 'ranking_latency', 'p(95)')}ms

  [전체]
  총 요청 수:  ${val(m, 'total_requests')}
  에러율:      ${pct(m, 'error_rate')}%
  전체 P95:    ${p(m, 'http_req_duration', 'p(95)')}ms
  전체 P99:    ${p(m, 'http_req_duration', 'p(99)')}ms
========================================================
  → 이 수치가 SLO 기준선이 됩니다
  → Stress Test에서 VU를 올리며 이 수치가 언제 무너지는지 확인
========================================================
`;
    console.log(summary);
    return { stdout: summary };
}

function p(m, metric, percentile) {
    return (m[metric]?.values[percentile] ?? 0).toFixed(2);
}
function val(m, metric) {
    return m[metric]?.values.count ?? m[metric]?.values.value ?? 0;
}
function pct(m, metric) {
    return ((m[metric]?.values.rate ?? 0) * 100).toFixed(2);
}