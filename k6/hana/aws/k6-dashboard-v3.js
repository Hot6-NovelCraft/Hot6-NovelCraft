import http from 'k6/http';
import { check } from 'k6';

/** V3 테스트 (배포환경용)
 * 인덱스 적용 + 쿼리 병합 (DB 쿼리 I/O 3회) + 신규 카운트만 Redis 캐싱
 * 시나리오 : V1, V2와 완전 동일, 엔드포인트만 /live 로 변경
 *
 * V1/V2 스크립트와 반드시 같은 MAX_VUS 값으로 실행해야 세 버전 간 비교가 의미 있음.
 */

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080/api';
const TOKEN = __ENV.ADMIN_TOKEN || 'POSTMAN_TOKEN';
const MAX_VUS = Number(__ENV.MAX_VUS) || 300;        // V1/V2와 동일한 값으로 맞출 것

export const options = {
    stages: [
        { duration: '30s', target: MAX_VUS },
        { duration: '1m', target: MAX_VUS },
        { duration: '30s', target: 0 },
    ],
    thresholds: {
        http_req_duration: ['p(95)<1000'],
    },
};

/**
 * setup() : VU 부하가 시작되기 전, k6 메인 스레드에서 딱 1번만 실행되는 라이프사이클 훅
 *
 * AdminCacheService 코드 확인 결과, 캐시 키(admin:stats:new_*_today:{날짜})는
 * role 파라미터와 무관하게 "오늘 날짜" 하나로만 결정됨.
 * → default() 안의 필터 OFF / ON 두 호출이 사실상 같은 캐시를 공유하므로
 *   워밍업 호출은 한 번만 하면 됨 (두 번 할 필요 없음).
 *
 * 캐시 저장(RedisUtil.setWithSeconds)이 동기 방식이라 응답이 오는 시점에
 * 이미 캐시 저장이 끝나 있음 → 별도 sleep 불필요.
 */
export function setup() {
    const params = {
        headers: {
            'Authorization': `Bearer ${TOKEN}`,
            'Content-Type': 'application/json',
        },
        tags: { name: 'V3_Warmup' },  // 그라파나에서 워밍업 요청과 본 테스트 요청을 구분
    };

    // /live 엔드포인트 1회 호출 -> newUsersToday/newNovelsToday/newMentosToday
    // 3개 캐시 키가 이 한 번의 호출로 모두 채워짐 (getLiveDashboard 내부에서 3개 다 조회하므로)
    const warmup = http.get(`${BASE_URL}/admin/dashboard/live`, params);
    check(warmup, { 'warmup 200': (r) => r.status === 200 });
}

export default function () {
    const params = {
        headers: {
            'Authorization': `Bearer ${TOKEN}`,
            'Content-Type': 'application/json',
        },
        tags: { name: 'V3_Redis_Live' },
    };

    // 호출 1 - 필터 OFF (워밍업 완료된 캐시를 hit)
    const resAll = http.get(`${BASE_URL}/admin/dashboard/live`, params);
    check(resAll, { 'v3 status 200 (ALL)': (r) => r.status === 200 });

    // 호출 2 - 필터 ON (role은 DB 쿼리에만 영향, 캐시는 위와 동일하게 hit)
    const resUserRole = http.get(`${BASE_URL}/admin/dashboard/live?role=READER`, params);
    check(resUserRole, { 'v3 status 200 (FILTER)': (r) => r.status === 200 });
}

// ============================================
// [결과 요약] 실행 종료 시 콘솔에 한국어로 보기 쉽게 출력
// ============================================
export function handleSummary(data) {
    const p95 = data.metrics.http_req_duration.values['p(95)'];
    const p99 = data.metrics.http_req_duration.values['p(99)'];
    const errorRate = data.metrics.http_req_failed ? data.metrics.http_req_failed.values.rate * 100 : 0;
    const checkPassRate = data.metrics.checks ? data.metrics.checks.values.rate * 100 : 0;
    const totalReqs = data.metrics.http_reqs ? data.metrics.http_reqs.values.count : 0;

    const p95Pass = p95 < 300;
    const p99Pass = p99 < 800;

    console.log('\n========================================');
    console.log('  V3 (인덱스 + 쿼리 병합 + Redis 캐싱) 결과');
    console.log('========================================');
    console.log(`  총 요청 수      : ${totalReqs}`);
    console.log(`  요청 실패율     : ${errorRate.toFixed(2)}%`);
    console.log(`  체크 통과율     : ${checkPassRate.toFixed(2)}%  (status 200 체크 기준)`);
    console.log(`  응답시간 p95    : ${p95.toFixed(0)}ms  (SLO 목표 300ms 미만) ${p95Pass ? '✅ 충족' : '❌ 초과'}`);
    console.log(`  응답시간 p99    : ${p99.toFixed(0)}ms  (SLO 목표 800ms 미만) ${p99Pass ? '✅ 충족' : '❌ 초과'}`);
    console.log('  ※ V1/V2 대비 최종 개선폭이 가장 커야 정상입니다 (캐싱 효과 검증).');
    console.log('========================================\n');

    return { stdout: JSON.stringify(data, null, 2) };
}