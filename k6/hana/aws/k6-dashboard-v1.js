import http from 'k6/http';
import { check } from 'k6';

/** V1 테스트 (배포환경용)
 * 인덱스 미적용 + 쿼리 분할 (DB 쿼리 I/O 8회)
 * 시나리오 : 관리자 대시보드 진입 후 역할 필터 적용
 *
 * "최대치로 밀어서 역산" 방침 반영:
 * 로컬은 100VU 고정 3단계(30s→1m→30s)였지만, 배포환경에서는 최대 VU를 -e MAX_VUS 로 조절 가능하게 함.
 * V1/V2/V3는 반드시 같은 MAX_VUS 값으로 실행해야 세 버전 간 비교가 의미 있음 (변수 통제 원칙).
 */

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080/api';
const TOKEN = __ENV.ADMIN_TOKEN || 'POSTMAN_TOKEN'; // superAdmin 토큰을 -e ADMIN_TOKEN=... 으로 주입 (하드코딩 금지)
const MAX_VUS = Number(__ENV.MAX_VUS) || 300;        // 원본 100VU 대비 최대치 탐색용으로 상향, V2/V3와 반드시 동일하게 맞출 것

export const options = {
    stages: [
        { duration: '30s', target: MAX_VUS },
        { duration: '1m', target: MAX_VUS },
        { duration: '30s', target: 0 },
    ],
    thresholds: {
        // 원본과 동일한 임계값 유지 -> 로컬 결과와 "같은 기준선"으로 비교 가능
        http_req_duration: ['p(95)<1000'],
    },
};

export default function () {
    const params = {
        headers: {
            'Authorization': `Bearer ${TOKEN}`,
            'Content-Type': 'application/json',
        },
        // 그라파나에서 V1/V2/V3를 구분하는 태그 (원본 그대로 유지 - 대시보드 쿼리 재사용 가능)
        tags: { name: 'V1_DB_NoIndex_Query_Split' },
    };

    // 호출 1 - 필터 없이 전체 통계 조회
    const resAll = http.get(`${BASE_URL}/admin/dashboard/v1`, params);
    check(resAll, { 'v1 status 200 (ALL)': (r) => r.status === 200 });

    // 호출 2 - 회원 역할 필터 적용 후 재조회
    // 주의: 파라미터명은 role(userRole 아님), 값은 READER(USER는 존재하지 않는 enum값)
    //       기존엔 둘 다 틀려서 필터가 조용히 무시된 채(=필터 OFF와 동일) 실행되고 있었음
    const resUserRole = http.get(`${BASE_URL}/admin/dashboard/v1?role=READER`, params);
    check(resUserRole, { 'v1 status 200 (FILTER)': (r) => r.status === 200 });
}