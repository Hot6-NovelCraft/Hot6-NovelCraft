/**
 * NovelCraft - Phase 4: Kafka 파이프라인 부하 테스트
 *
 * 목적: 읽기 트래픽(300 VU) + 쓰기 트래픽(100 VU) 동시 부하
 *       → notification-topic Kafka 파이프라인 검증
 *       → EC2 CPU, RDS 커넥션, Kafka Consumer Lag 측정
 *
 * 시나리오:
 *   - readers (300 VU): 기존 독자 여정 (읽기)
 *   - writers (100 VU): 좋아요 + 댓글 + 팔로우 (쓰기 → Kafka 트리거)
 *
 * 실행: k6 run load-test-kafka.js \
 *   -e NOVEL_IDS=87742,87823,87913,88465,115046 \
 *   -e EP_IDS=1,2,3,4,5,6,7,8,9,10,11,12,13,14,15 \
 *   -e AUTHOR_IDS=1,2,3,4,5
 */

import http from 'k6/http';
import { check, sleep, group } from 'k6';
import { Trend, Rate, Counter } from 'k6/metrics';

// ─── 커스텀 메트릭 ───────────────────────────────────────────────
const novelDetailLatency  = new Trend('novel_detail_latency',  true);
const episodeReadLatency  = new Trend('episode_read_latency',  true);
const rankingLatency      = new Trend('ranking_latency',       true);
const likeLatency         = new Trend('like_latency',          true);
const commentLatency      = new Trend('comment_latency',       true);
const followLatency       = new Trend('follow_latency',        true);

const errorRate           = new Rate('error_rate');
const writeErrorRate      = new Rate('write_error_rate');
const totalRequests       = new Counter('total_requests');
const kafkaTriggerCount   = new Counter('kafka_trigger_count'); // Kafka 트리거 카운트

// ─── 환경 설정 ───────────────────────────────────────────────────
const BASE_URL   = __ENV.BASE_URL   || 'http://novelcraft-dev-alb-336387969.ap-northeast-2.elb.amazonaws.com';
const NOVEL_IDS  = (__ENV.NOVEL_IDS  || '87742,87823,87913,88465,115046').split(',').map(Number);
const EP_IDS     = (__ENV.EP_IDS     || '1,2,3,4,5,6,7,8,9,10,11,12,13,14,15').split(',').map(Number);
const AUTHOR_IDS = (__ENV.AUTHOR_IDS || '500217,500298,500388,500940,501521').split(',').map(Number);

// ─── SLO ─────────────────────────────────────────────────────────
const SLO_P95_READ_MS  = 500;   // 읽기 P95
const SLO_P95_WRITE_MS = 1000;  // 쓰기 P95 (DB 트랜잭션 포함)
const SLO_ERROR_PCT    = 1;

// ─── 옵션 ────────────────────────────────────────────────────────
export const options = {
    setupTimeout: '300s',
    scenarios: {
        // 읽기 트래픽 (300 VU) - 기존 독자 여정
        readers: {
            executor: 'ramping-vus',
            startVUs: 0,
            stages: [
                { duration: '1m',  target: 100 }, // 워밍업
                { duration: '3m',  target: 300 }, // 목표 VU
                { duration: '5m',  target: 300 }, // 유지
                { duration: '1m',  target: 0   }, // 쿨다운
            ],
            exec: 'readerJourney',
        },
        // 쓰기 트래픽 (100 VU) - Kafka 트리거
        writers: {
            executor: 'ramping-vus',
            startVUs: 0,
            stages: [
                { duration: '1m',  target: 30  }, // 워밍업
                { duration: '3m',  target: 100 }, // 목표 VU
                { duration: '5m',  target: 100 }, // 유지
                { duration: '1m',  target: 0   }, // 쿨다운
            ],
            exec: 'writerJourney',
            startTime: '0s',
        },
    },
    thresholds: {
        http_req_duration:    [`p(95)<${SLO_P95_WRITE_MS}`],
        http_req_failed:      [`rate<${SLO_ERROR_PCT / 100}`],
        novel_detail_latency: [`p(95)<${SLO_P95_READ_MS}`],
        episode_read_latency: [`p(95)<${SLO_P95_READ_MS * 2}`],
        like_latency:         [`p(95)<${SLO_P95_WRITE_MS}`],
        comment_latency:      [`p(95)<${SLO_P95_WRITE_MS}`],
        follow_latency:       [`p(95)<${SLO_P95_WRITE_MS}`],
        error_rate:           [`rate<${SLO_ERROR_PCT / 100}`],
        write_error_rate:     [`rate<${SLO_ERROR_PCT / 100}`],
    },
};

// ─── Setup: 토큰 발급 (읽기 300 + 쓰기 100 = 420개) ─────────────
export function setup() {
    const readerTokens = [];
    const writerTokens = [];

    console.log('[setup] READER 토큰 발급 시작 (320개)...');
    for (let i = 0; i < 320; i++) {
        const email = `loadtest${502 + i}@test.com`;
        const res = http.post(
            `${BASE_URL}/api/auth/login`,
            JSON.stringify({ email, password: 'test1234' }),
            { headers: { 'Content-Type': 'application/json' } }
        );
        readerTokens.push(res.status === 200 ? JSON.parse(res.body).data.accessToken : null);
        if (i % 10 === 0) sleep(0.1);
    }

    console.log('[setup] AUTHOR 토큰 발급 시작 (110개)...');
    for (let i = 0; i < 110; i++) {
        const email = `loadtest${1 + i}@test.com`;
        const res = http.post(
            `${BASE_URL}/api/auth/login`,
            JSON.stringify({ email, password: 'test1234' }),
            { headers: { 'Content-Type': 'application/json' } }
        );
        writerTokens.push(res.status === 200 ? JSON.parse(res.body).data.accessToken : null);
        if (i % 10 === 0) sleep(0.1);
    }

    const validReaders = readerTokens.filter(t => t !== null).length;
    const validWriters = writerTokens.filter(t => t !== null).length;
    console.log(`[setup] READER: ${validReaders}/320, AUTHOR: ${validWriters}/110`);

    if (validReaders < 100) throw new Error(`READER 토큰 부족 (${validReaders}개)`);
    if (validWriters < 30)  throw new Error(`AUTHOR 토큰 부족 (${validWriters}개)`);

    return { readerTokens, writerTokens, novelIds: NOVEL_IDS, epIds: EP_IDS, authorIds: AUTHOR_IDS };
}

// ─── 시나리오 1: 읽기 여정 (독자) ───────────────────────────────
export function readerJourney(data) {
    const token   = data.readerTokens[(__VU - 1) % data.readerTokens.length];
    const headers = buildHeaders(token);
    const novelId = randomItem(data.novelIds);
    const epId    = randomItem(data.epIds);

    if (!token) return;

    group('랭킹_조회', () => {
        const res = http.get(`${BASE_URL}/api/novels/ranking`, { headers });
        track(res, rankingLatency, errorRate);
    });
    sleep(rand(0.3, 0.8));

    group('소설_상세_조회', () => {
        const res = http.get(`${BASE_URL}/api/novels/${novelId}`, { headers });
        track(res, novelDetailLatency, errorRate);
    });
    sleep(rand(0.3, 0.8));

    group('회차_목록_조회', () => {
        const res = http.get(`${BASE_URL}/api/novels/${novelId}/episodes?page=0&size=20`, { headers });
        track(res, episodeReadLatency, errorRate);
    });
    sleep(rand(0.5, 1.0));

    group('회차_본문_열람', () => {
        const res = http.get(`${BASE_URL}/api/v2/episodes/${epId}`, { headers });
        track(res, episodeReadLatency, errorRate);
    });
    sleep(rand(1.5, 3.0));
}

// ─── 시나리오 2: 쓰기 여정 (Kafka 트리거) ───────────────────────
// 좋아요 → notification-topic → DB 저장 + WebSocket 전송
export function writerJourney(data) {
    const token    = data.writerTokens[(__VU - 1) % data.writerTokens.length];
    const headers  = buildHeaders(token);
    const epId     = randomItem(data.epIds);
    const authorId = randomItem(data.authorIds);

    if (!token) return;

    // 좋아요 토글 → Kafka notification-topic 트리거
    group('회차_좋아요', () => {
        const res = http.post(
            `${BASE_URL}/api/episodes/${epId}/like`,
            null,
            { headers }
        );
        const ok = check(res, { '좋아요 성공': (r) => r.status === 200 || r.status === 201 });
        likeLatency.add(res.timings.duration);
        totalRequests.add(1);
        writeErrorRate.add(!ok);
        errorRate.add(!ok);
        if (ok) kafkaTriggerCount.add(1);
    });
    sleep(rand(0.5, 1.0));

    // 댓글 작성 → Kafka notification-topic 트리거
    group('댓글_작성', () => {
        const res = http.post(
            `${BASE_URL}/api/episodes/${epId}/comments`,
            JSON.stringify({ content: `테스트 댓글 ${Date.now()}` }),
            { headers }
        );
        const ok = check(res, { '댓글 성공': (r) => r.status === 200 || r.status === 201 });
        commentLatency.add(res.timings.duration);
        totalRequests.add(1);
        writeErrorRate.add(!ok);
        errorRate.add(!ok);
        if (ok) kafkaTriggerCount.add(1);
    });
    sleep(rand(1.0, 2.0));

    // 작가 팔로우 → Kafka notification-topic 트리거
    group('작가_팔로우', () => {
        const res = http.post(
            `${BASE_URL}/api/auth/authors/${authorId}/follow`,
            null,
            { headers }
        );
        // 팔로우/언팔로우 토글이라 200 or 409(이미 팔로우) 모두 허용
        const ok = check(res, { '팔로우 성공': (r) => r.status === 200 || r.status === 201 || r.status === 409 });
        followLatency.add(res.timings.duration);
        totalRequests.add(1);
        writeErrorRate.add(!ok);
        errorRate.add(!ok);
        if (res.status === 200 || res.status === 201) kafkaTriggerCount.add(1);
    });
    sleep(rand(2.0, 4.0));
}

// ─── 유틸 ────────────────────────────────────────────────────────
function buildHeaders(token) {
    const h = { 'Content-Type': 'application/json' };
    if (token) h['Authorization'] = token;
    return h;
}

function track(res, trend, rate) {
    totalRequests.add(1);
    trend.add(res.timings.duration);
    const ok = check(res, { 'status 2xx': (r) => r.status >= 200 && r.status < 300 });
    rate.add(!ok);
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

    const detailP95   = getP(m, 'novel_detail_latency', 'p(95)');
    const episodeP95  = getP(m, 'episode_read_latency',  'p(95)');
    const likeP95     = getP(m, 'like_latency',          'p(95)');
    const commentP95  = getP(m, 'comment_latency',       'p(95)');
    const followP95   = getP(m, 'follow_latency',        'p(95)');
    const globalP95   = getP(m, 'http_req_duration',     'p(95)');
    const globalP99   = getP(m, 'http_req_duration',     'p(99)');
    const errRate     = getRate(m, 'error_rate');
    const writeErr    = getRate(m, 'write_error_rate');
    const totalReq    = getCount(m, 'total_requests');
    const kafkaTrig   = getCount(m, 'kafka_trigger_count');

    const summary = `
========================================================
  NovelCraft - Kafka 파이프라인 부하 테스트 결과
  읽기 300 VU + 쓰기 100 VU 동시 부하
========================================================
  총 요청:         ${totalReq}건
  Kafka 트리거:    ${kafkaTrig}건 (좋아요+댓글+팔로우)
  전체 에러율:     ${errRate.toFixed(2)}%  ${errRate > SLO_ERROR_PCT ? '⚠️ SLO 초과' : '✅'}
  쓰기 에러율:     ${writeErr.toFixed(2)}% ${writeErr > SLO_ERROR_PCT ? '⚠️ SLO 초과' : '✅'}

  [읽기 API P95]
  소설 상세:  ${detailP95.toFixed(0)}ms  ${detailP95 > SLO_P95_READ_MS  ? '⚠️' : '✅'}
  회차 열람:  ${episodeP95.toFixed(0)}ms ${episodeP95 > SLO_P95_READ_MS * 2 ? '⚠️' : '✅'}

  [쓰기 API P95 - Kafka 트리거]
  좋아요:     ${likeP95.toFixed(0)}ms    ${likeP95    > SLO_P95_WRITE_MS ? '⚠️' : '✅'}
  댓글:       ${commentP95.toFixed(0)}ms ${commentP95 > SLO_P95_WRITE_MS ? '⚠️' : '✅'}
  팔로우:     ${followP95.toFixed(0)}ms  ${followP95  > SLO_P95_WRITE_MS ? '⚠️' : '✅'}

  [전체]
  P95: ${globalP95.toFixed(0)}ms
  P99: ${globalP99.toFixed(0)}ms

  [측정 포인트 - Grafana에서 확인]
  → EC2 CPU: 읽기 단독 대비 증가율
  → RDS 커넥션: 쓰기 트랜잭션으로 커넥션 증가
  → Redis: notification WebSocket 전송 처리
  → Kafka Consumer Lag: notification-topic 처리 지연 여부

  [SLO 판정]
  읽기: ${detailP95 > SLO_P95_READ_MS || episodeP95 > SLO_P95_READ_MS * 2 ? '❌ SLO 미달' : '✅ 유지'}
  쓰기: ${likeP95 > SLO_P95_WRITE_MS || commentP95 > SLO_P95_WRITE_MS ? '❌ SLO 미달' : '✅ 유지'}
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