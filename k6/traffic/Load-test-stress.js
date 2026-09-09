/**
 * NovelCraft - Phase 2: Stress Test (Breaking Point 탐색)
 *
 * 목적: VU를 단계적으로 올려 시스템이 어느 지점에서 한계에 도달하는지 탐색
 *       SLO(P95 < 500ms, Error Rate < 1%) 기준으로 Breaking Point 판정
 *
 * 실행: k6 run load-test-stress.js
 * 환경변수:
 *   BASE_URL   - ALB 주소
 *   NOVEL_IDS  - 콤마 구분 소설 ID 목록
 *   EP_IDS     - 콤마 구분 회차 ID 목록
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

// ─── SLO 정의 (Breaking Point 판정 기준) ────────────────────────
// P95 응답시간 500ms 초과 or 에러율 1% 초과 → Breaking Point
const SLO_P95_MS    = 500;
const SLO_ERROR_PCT = 1;

// ─── 환경 설정 ───────────────────────────────────────────────────
const BASE_URL  = __ENV.BASE_URL  || 'http://novelcraft-dev-alb-336387969.ap-northeast-2.elb.amazonaws.com';
const NOVEL_IDS = (__ENV.NOVEL_IDS || '1,2,3,4,5').split(',').map(Number);
const EP_IDS    = (__ENV.EP_IDS   || '1,2,3,4,5,6,7,8,9,10,11,12,13,14,15').split(',').map(Number); // 무료 회차만 (is_free=1)

// ─── VU 단계: 20 → 50 → 100 → 200 → 300 → 500 ──────────────────
// 각 단계 3분 유지 → CloudWatch 메트릭 안정화 대기
export const options = {
    setupTimeout: '300s', // 620개 토큰 발급 대기 (기본 60s → 5분으로 확장)
    scenarios: {
        stress_readers: {
            executor: 'ramping-vus',
            startVUs: 0,
            stages: [
                { duration: '1m',  target: 20  }, // 워밍업
                { duration: '3m',  target: 20  }, // 20 VU 유지
                { duration: '1m',  target: 50  }, // 50 VU 증가
                { duration: '3m',  target: 50  }, // 50 VU 유지
                { duration: '1m',  target: 100 }, // 100 VU 증가
                { duration: '3m',  target: 100 }, // 100 VU 유지
                { duration: '1m',  target: 200 }, // 200 VU 증가
                { duration: '3m',  target: 200 }, // 200 VU 유지
                { duration: '1m',  target: 300 }, // 300 VU 증가
                { duration: '3m',  target: 300 }, // 300 VU 유지
                { duration: '1m',  target: 500 }, // 500 VU 증가
                { duration: '3m',  target: 500 }, // 500 VU 유지
                { duration: '2m',  target: 0   }, // 쿨다운
            ],
            exec: 'readerJourney',
        },
        // 검색 트래픽 병행 (전체 VU의 20% 비중)
        stress_searchers: {
            executor: 'ramping-vus',
            startVUs: 0,
            stages: [
                { duration: '1m',  target: 5   },
                { duration: '3m',  target: 5   },
                { duration: '1m',  target: 10  },
                { duration: '3m',  target: 10  },
                { duration: '1m',  target: 20  },
                { duration: '3m',  target: 20  },
                { duration: '1m',  target: 40  },
                { duration: '3m',  target: 40  },
                { duration: '1m',  target: 60  },
                { duration: '3m',  target: 60  },
                { duration: '1m',  target: 100 },
                { duration: '3m',  target: 100 },
                { duration: '2m',  target: 0   },
            ],
            exec: 'searchJourney',
        },
    },
    thresholds: {
        // SLO 기준 → 이 임계값이 깨지는 VU 구간이 Breaking Point
        http_req_duration:    [`p(95)<${SLO_P95_MS}`],
        http_req_failed:      [`rate<${SLO_ERROR_PCT / 100}`],
        novel_list_latency:   [`p(95)<${SLO_P95_MS}`],
        novel_detail_latency: [`p(95)<${SLO_P95_MS}`],
        episode_read_latency: [`p(95)<${SLO_P95_MS * 2}`], // 회차 본문은 2배 허용
        search_latency:       [`p(95)<${SLO_P95_MS}`],
        error_rate:           [`rate<${SLO_ERROR_PCT / 100}`],
    },
};

// ─── Setup: 토큰 대량 발급 (최대 VU 600 대비) ────────────────────
export function setup() {
    const tokens = [];
    const total  = 620; // 최대 VU (600) + 여유분

    console.log(`[setup] ${total}개 토큰 발급 시작...`);

    // READER 계정: loadtest502@test.com ~ (502번부터 READER role)
    for (let i = 0; i < total; i++) {
        const email = `loadtest${502 + i}@test.com`;
        const res = http.post(
            `${BASE_URL}/api/auth/login`,
            JSON.stringify({ email: email, password: 'test1234' }),
            { headers: { 'Content-Type': 'application/json' } }
        );
        if (res.status === 200) {
            tokens.push(JSON.parse(res.body).data.accessToken);
        } else {
            tokens.push(null);
        }

        // 로그인 API 과부하 방지 - 10개마다 잠깐 대기
        if (i % 10 === 0) sleep(0.1);
    }

    const valid = tokens.filter(t => t !== null).length;
    console.log(`[setup] 토큰 준비 완료: ${valid} / ${total}`);

    if (valid < 100) {
        throw new Error(`유효 토큰 부족 (${valid}개) - 테스트 데이터 확인 필요`);
    }

    return { tokens, novelIds: NOVEL_IDS, epIds: EP_IDS };
}

// ─── 시나리오 1: 일반 독자 여정 (70%) ───────────────────────────
export function readerJourney(data) {
    const token   = data.tokens[(__VU - 1) % data.tokens.length];
    const headers = buildHeaders(token);
    const novelId = randomItem(data.novelIds);
    const epId    = randomItem(data.epIds);

    // 메인 진입: 랭킹 조회 - 토큰 없으면 스킵
    if (!token) { return; }
    group('랭킹_조회', () => {
        const res = http.get(
            `${BASE_URL}/api/novels/ranking`,
            { headers, tags: { name: '랭킹_조회' } }
        );
        track(res, rankingLatency);
    });
    sleep(rand(0.3, 0.8));

    // 소설 목록 탐색
    group('소설_목록_조회', () => {
        const res = http.get(
            `${BASE_URL}/api/v2/novels?page=0&size=20`,
            { headers, tags: { name: '소설_목록_조회' } }
        );
        track(res, novelListLatency);
    });
    sleep(rand(0.5, 1.0));

    // 소설 상세 조회
    group('소설_상세_조회', () => {
        const res = http.get(
            `${BASE_URL}/api/novels/${novelId}`,
            { headers, tags: { name: '소설_상세_조회' } }
        );
        track(res, novelDetailLatency);
    });
    sleep(rand(0.3, 0.8));

    // 회차 목록 조회
    group('회차_목록_조회', () => {
        const res = http.get(
            `${BASE_URL}/api/novels/${novelId}/episodes?page=0&size=20`,
            { headers, tags: { name: '회차_목록_조회' } }
        );
        track(res, episodeReadLatency);
    });
    sleep(rand(0.5, 1.0));

    // 회차 본문 열람 (가장 무거운 API - DB 조회 + 본문 반환)
    group('회차_본문_열람', () => {
        const res = http.get(
            `${BASE_URL}/api/v2/episodes/${epId}`,
            { headers, tags: { name: '회차_본문_열람' } }
        );
        track(res, episodeReadLatency);
    });
    sleep(rand(1.5, 3.0));
}

// ─── 시나리오 2: 검색 독자 여정 (20%) ───────────────────────────
export function searchJourney(data) {
    const token    = data.tokens[(__VU - 1) % data.tokens.length];
    const headers  = buildHeaders(token);
    const keywords = ['판타지', '로맨스', '회귀', '이세계', '힐링'];
    const keyword  = randomItem(keywords);

    // 인기 검색어 확인
    group('인기_검색어_조회', () => {
        const res = http.get(
            `${BASE_URL}/api/search/keywords/popular`,
            { headers, tags: { name: '인기_검색어_조회' } }
        );
        track(res, searchLatency);
    });
    sleep(rand(0.3, 0.8));

    // 키워드 검색
    group('소설_검색', () => {
        const res = http.get(
            `${BASE_URL}/api/search/v2/novels?keyword=${keyword}&page=0&size=20`,
            { headers, tags: { name: '소설_검색' } }
        );
        track(res, searchLatency);
    });
    sleep(rand(0.5, 1.0));

    // 검색 결과 중 소설 상세 조회
    group('검색결과_상세_조회', () => {
        const novelId = randomItem(data.novelIds);
        const res = http.get(
            `${BASE_URL}/api/novels/${novelId}`,
            { headers, tags: { name: '검색결과_상세_조회' } }
        );
        track(res, novelDetailLatency);
    });
    sleep(rand(1.0, 2.0));
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

    const novelListP95    = getP(m, 'novel_list_latency',    'p(95)');
    const novelDetailP95  = getP(m, 'novel_detail_latency',  'p(95)');
    const episodeReadP95  = getP(m, 'episode_read_latency',  'p(95)');
    const searchP95       = getP(m, 'search_latency',        'p(95)');
    const globalP95       = getP(m, 'http_req_duration',     'p(95)');
    const globalP99       = getP(m, 'http_req_duration',     'p(99)');
    const errRate         = getRate(m, 'error_rate');
    const totalReq        = getCount(m, 'total_requests');

    const sloBreached = (novelListP95 > SLO_P95_MS || novelDetailP95 > SLO_P95_MS || errRate > SLO_ERROR_PCT);

    const summary = `
========================================================
  NovelCraft - Stress Test 결과
  SLO 기준: P95 < ${SLO_P95_MS}ms, Error Rate < ${SLO_ERROR_PCT}%
========================================================
  VU 단계: 20 → 50 → 100 → 200 → 300 → 500
  총 요청: ${totalReq}건

  [API별 P95 응답시간]
  소설 목록 조회:  ${novelListP95.toFixed(0)}ms   ${novelListP95 > SLO_P95_MS ? '⚠️ SLO 초과' : '✅'}
  소설 상세 조회:  ${novelDetailP95.toFixed(0)}ms  ${novelDetailP95 > SLO_P95_MS ? '⚠️ SLO 초과' : '✅'}
  회차 열람:       ${episodeReadP95.toFixed(0)}ms  ${episodeReadP95 > SLO_P95_MS * 2 ? '⚠️ SLO 초과' : '✅'}
  검색:            ${searchP95.toFixed(0)}ms        ${searchP95 > SLO_P95_MS ? '⚠️ SLO 초과' : '✅'}

  [전체]
  P95: ${globalP95.toFixed(0)}ms
  P99: ${globalP99.toFixed(0)}ms
  에러율: ${errRate.toFixed(2)}%

  [판정]
  SLO 달성 여부: ${sloBreached ? '❌ Breaking Point 도달 - CloudWatch 확인 필요' : '✅ SLO 유지'}

  [다음 단계]
  → CloudWatch에서 어느 VU 구간에서 CPU/커넥션/쿼리 급등했는지 확인
  → 병목 API에 대해 load-test-api-focus.js 실행
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