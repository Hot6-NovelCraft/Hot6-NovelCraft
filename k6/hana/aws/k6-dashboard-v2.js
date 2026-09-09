import http from 'k6/http';
import { check } from 'k6';

/** V2 테스트 (배포환경용)
 * 인덱스 적용 + 쿼리 병합 (DB 쿼리 I/O 3회)
 * 시나리오 : V1과 완전 동일, 엔드포인트만 /v2로 변경
 *
 * V1 스크립트와 반드시 같은 MAX_VUS 값으로 실행해야 두 버전 간 비교가 의미 있음.
 */

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080/api';
const TOKEN = __ENV.ADMIN_TOKEN || 'POSTMAN_TOKEN';
const MAX_VUS = Number(__ENV.MAX_VUS) || 300;        // V1/V3와 동일한 값으로 맞출 것

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

export default function () {
    const params = {
        headers: {
            'Authorization': `Bearer ${TOKEN}`,
            'Content-Type': 'application/json',
        },
        tags: { name: 'V2_DB_Index_Query_Merge' },
    };

    // 호출 1 - 필터 OFF
    const resAll = http.get(`${BASE_URL}/admin/dashboard/v2`, params);
    check(resAll, { 'v2 status 200 (ALL)': (r) => r.status === 200 });

    // 호출 2 - 필터 ON
    // 주의: 파라미터명은 role(userRole 아님), 값은 READER(USER는 존재하지 않는 enum값)
    //       기존엔 둘 다 틀려서 필터가 조용히 무시된 채(=필터 OFF와 동일) 실행되고 있었음
    const resUserRole = http.get(`${BASE_URL}/admin/dashboard/v2?role=READER`, params);
    check(resUserRole, { 'v2 status 200 (FILTER)': (r) => r.status === 200 });
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

    // SLO 목표치 대비 통과 여부를 boolean으로 미리 계산해서 이모지로 표시
    const p95Pass = p95 < 300;
    const p99Pass = p99 < 800;

    console.log('\n========================================');
    console.log('  V2 (인덱스 적용 + 쿼리 병합) 결과');
    console.log('========================================');
    console.log(`  총 요청 수      : ${totalReqs}`);
    console.log(`  요청 실패율     : ${errorRate.toFixed(2)}%`);
    console.log(`  체크 통과율     : ${checkPassRate.toFixed(2)}%  (status 200 체크 기준)`);
    console.log(`  응답시간 p95    : ${p95.toFixed(0)}ms  (SLO 목표 300ms 미만) ${p95Pass ? '✅ 충족' : '❌ 초과'}`);
    console.log(`  응답시간 p99    : ${p99.toFixed(0)}ms  (SLO 목표 800ms 미만) ${p99Pass ? '✅ 충족' : '❌ 초과'}`);
    console.log('  ※ V1 대비 개선폭을 비교해보세요 (인덱스 + 쿼리 병합 효과).');
    console.log('========================================\n');

    return { stdout: JSON.stringify(data, null, 2) };
}