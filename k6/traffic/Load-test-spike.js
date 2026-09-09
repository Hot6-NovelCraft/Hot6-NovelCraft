/**
 * NovelCraft - Phase 3: Spike Test (신작 출시 시나리오)
 *
 * 목적: 웹소설 플랫폼 특성상 인기 작가의 신작/신회차 출시 순간
 *       트래픽이 폭발적으로 몰리는 시나리오 재현
 *       → 포트폴리오 핵심 스토리: "실제 서비스 상황을 시뮬레이션"
 *
 * 시나리오:
 *   - 평상시: 50 VU 유지
 *   - 신회차 출시 순간: 0→500 VU 1분 내 급증
 *   - 집중 공격 대상: 소설 상세 + 신규 회차 조회 + 랭킹
 *   - 5분 후 트래픽 정상화
 *
 * 실행: k6 run load-test-spike.js -e NOVEL_ID=1 -e NEW_EP_ID=10
 */

import http from 'k6/http';
import { check, sleep, group } from 'k6';
import { Trend, Rate, Counter } from 'k6/metrics';

// ─── 커스텀 메트릭 ───────────────────────────────────────────────
const novelDetailLatency  = new Trend('novel_detail_latency',  true);
const episodeReadLatency  = new Trend('episode_read_latency',  true);
const rankingLatency      = new Trend('ranking_latency',       true);

const errorRate           = new Rate('error_rate');
const totalRequests       = new Counter('total_requests');

// ─── 환경 설정 ───────────────────────────────────────────────────
const BASE_URL   = __ENV.BASE_URL   || 'http://novelcraft-dev-alb-336387969.ap-northeast-2.elb.amazonaws.com';
const NOVEL_ID   = Number(__ENV.NOVEL_ID  || 1);   // 신작 출시 소설 ID
const NEW_EP_ID  = Number(__ENV.NEW_EP_ID || 1);   // 신규 회차 ID (무료 회차)
const NOVEL_IDS  = (__ENV.NOVEL_IDS || '87742,87823,87913,88465,115046').split(',').map(Number);

// ─── SLO ─────────────────────────────────────────────────────────
const SLO_P95_MS    = 1000; // Spike는 SLO 기준을 2배로 완화 (순간 폭발 허용)
const SLO_ERROR_PCT = 5;    // Spike 중 에러 5% 이내 허용

// ─── 옵션: 스파이크 패턴 ─────────────────────────────────────────
export const options = {
    setupTimeout: '300s', // 550개 토큰 발급 대기
    scenarios: {
        // 평상시 백그라운드 트래픽
        baseline_traffic: {
            executor: 'constant-vus',
            vus: 30,
            duration: '13m',
            exec: 'normalReading',
            startTime: '0s',
        },
        // 스파이크: 신회차 출시 순간
        spike_traffic: {
            executor: 'ramping-vus',
            startVUs: 0,
            startTime: '3m', // 3분 평상시 후 스파이크 시작
            stages: [
                { duration: '30s', target: 500 }, // 30초만에 500 VU 폭발
                { duration: '3m',  target: 500 }, // 3분 집중
                { duration: '30s', target: 100 }, // 트래픽 감소
                { duration: '3m',  target: 50  }, // 정상화
                { duration: '30s', target: 0   }, // 종료
            ],
            exec: 'newEpisodeSpike',
        },
    },
    thresholds: {
        http_req_duration:    [`p(95)<${SLO_P95_MS}`],
        http_req_failed:      [`rate<${SLO_ERROR_PCT / 100}`],
        novel_detail_latency: [`p(95)<${SLO_P95_MS}`],
        episode_read_latency: [`p(95)<${SLO_P95_MS * 1.5}`],
        error_rate:           [`rate<${SLO_ERROR_PCT / 100}`],
    },
};

// ─── Setup: 토큰 대량 발급 (Spike 500 VU 대비) ───────────────────
export function setup() {
    const tokens = [];
    const total  = 550;

    console.log(`[setup] ${total}개 토큰 발급 시작...`);

    // READER 계정: loadtest502@test.com ~ (502번부터 READER role)
    for (let i = 0; i < total; i++) {
        const email = `loadtest${502 + i}@test.com`;
        const res = http.post(
            `${BASE_URL}/api/auth/login`,
            JSON.stringify({ email: email, password: 'test1234' }),
            { headers: { 'Content-Type': 'application/json' } }
        );
        tokens.push(res.status === 200 ? JSON.parse(res.body).data.accessToken : null);
        if (i % 10 === 0) sleep(0.1);
    }

    const valid = tokens.filter(t => t !== null).length;
    console.log(`[setup] 완료: ${valid} / ${total}`);

    return { tokens, novelIds: NOVEL_IDS };
}

// ─── 시나리오 1: 평상시 일반 독자 트래픽 ────────────────────────
export function normalReading(data) {
    const token   = data.tokens[(__VU - 1) % data.tokens.length];
    const headers = buildHeaders(token);
    const novelId = randomItem(data.novelIds);

    if (!token) { return; }
    group('랭킹_조회', () => {
        const res = http.get(
            `${BASE_URL}/api/novels/ranking`,
            { headers, tags: { name: '랭킹_조회_평상시' } }
        );
        track(res, rankingLatency);
    });
    sleep(rand(0.5, 1.0));

    group('소설_목록', () => {
        const res = http.get(
            `${BASE_URL}/api/v2/novels?page=0&size=20`,
            { headers, tags: { name: '소설_목록_평상시' } }
        );
        track(res, novelDetailLatency);
    });
    sleep(rand(0.5, 1.5));

    group('소설_상세', () => {
        const res = http.get(
            `${BASE_URL}/api/novels/${novelId}`,
            { headers, tags: { name: '소설_상세_평상시' } }
        );
        track(res, novelDetailLatency);
    });
    sleep(rand(1.0, 3.0));
}

// ─── 시나리오 2: 신회차 출시 스파이크 ───────────────────────────
// 실제 패턴: 알림 받은 독자들이 동시에 신회차로 몰림
export function newEpisodeSpike(data) {
    const token   = data.tokens[(__VU - 1) % data.tokens.length];
    const headers = buildHeaders(token);

    // 알림 확인 (스파이크 진입 계기)
    group('알림_확인', () => {
        const res = http.get(
            `${BASE_URL}/api/notifications/unread-count`,
            { headers, tags: { name: '알림_확인_스파이크' } }
        );
        track(res, rankingLatency); // 가벼운 API
    });
    sleep(rand(0.1, 0.3)); // 알림 확인 후 빠르게 이동

    // 신작 소설 상세 조회 (모든 유저가 동일 소설로 몰림 - 핵심 병목)
    group('신작_소설_상세', () => {
        const res = http.get(
            `${BASE_URL}/api/novels/${NOVEL_ID}`,
            { headers, tags: { name: '신작_소설_상세_스파이크' } }
        );
        track(res, novelDetailLatency);
    });
    sleep(rand(0.2, 0.5));

    // 신규 회차 본문 열람 (가장 집중되는 지점)
    group('신규_회차_열람', () => {
        const res = http.get(
            `${BASE_URL}/api/v2/episodes/${NEW_EP_ID}`,
            { headers, tags: { name: '신규_회차_열람_스파이크' } }
        );
        track(res, episodeReadLatency);
    });
    sleep(rand(3.0, 8.0)); // 회차 읽는 시간

    // 다 읽고 랭킹 확인 (실제 독자 행동)
    group('랭킹_재확인', () => {
        const res = http.get(
            `${BASE_URL}/api/novels/ranking`,
            { headers, tags: { name: '랭킹_재확인_스파이크' } }
        );
        track(res, rankingLatency);
    });
    sleep(rand(0.5, 1.0));
}

// ─── 유틸 ────────────────────────────────────────────────────────
function buildHeaders(token) {
    const h = { 'Content-Type': 'application/json' };
    if (token) h['Authorization'] = token; // 기존 스크립트 방식: accessToken 값 그대로 (Bearer prefix 포함 여부는 서버 응답 확인)
    return h;
}

function track(res, trend) {
    totalRequests.add(1);
    trend.add(res.timings.duration);
    const ok = check(res, { 'status 2xx': (r) => r.status >= 200 && r.status < 300 });
    errorRate.add(!ok);
    return ok;
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

    const detailP95  = getP(m, 'novel_detail_latency',  'p(95)');
    const detailP99  = getP(m, 'novel_detail_latency',  'p(99)');
    const episodeP95 = getP(m, 'episode_read_latency',  'p(95)');
    const episodeP99 = getP(m, 'episode_read_latency',  'p(99)');
    const globalP99  = getP(m, 'http_req_duration',     'p(99)');
    const errRate    = getRate(m, 'error_rate');
    const totalReq   = getCount(m, 'total_requests');

    const summary = `
========================================================
  NovelCraft - Spike Test 결과
  시나리오: 신작 출시 순간 (평상시 30VU → 스파이크 500VU)
  대상 소설: novelId=${NOVEL_ID}, 신규회차: epId=${NEW_EP_ID}
========================================================
  총 요청: ${totalReq}건
  에러율:  ${errRate.toFixed(2)}% ${errRate > SLO_ERROR_PCT ? '⚠️ SLO 초과' : '✅'}

  [스파이크 구간 핵심 API]
  소설 상세 P95: ${detailP95.toFixed(0)}ms  P99: ${detailP99.toFixed(0)}ms
  신규 회차 P95: ${episodeP95.toFixed(0)}ms  P99: ${episodeP99.toFixed(0)}ms

  [전체 P99]  ${globalP99.toFixed(0)}ms

  [Spike SLO 기준: P95 < ${SLO_P95_MS}ms, Error < ${SLO_ERROR_PCT}%]
  판정: ${(detailP95 > SLO_P95_MS || errRate > SLO_ERROR_PCT) ? '❌ SLO 미달 - 캐시 전략 점검 필요' : '✅ SLO 유지'}

  [포트폴리오 포인트]
  → 동일 novelId/epId 집중 조회 → 캐시 히트율이 결정적
  → Redis 캐시 미적용 시: 모든 요청이 RDS로 → 커넥션 고갈
  → 캐시 적용 후 재측정으로 개선율 수치화 가능
========================================================
`;
    console.log(summary);
    return { stdout: summary };
}

function getP(m, metric, percentile) {
    return m[metric]?.values[percentile] ?? 0;
}
function getRate(m, metric) {
    return (m[metric]?.values.rate ?? 0) * 100;
}
function getCount(m, metric) {
    return m[metric]?.values.count ?? 0;
}