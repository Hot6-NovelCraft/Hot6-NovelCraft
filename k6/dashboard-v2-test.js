import http from 'k6/http';
import { check, sleep } from 'k6';

/** V2 테스트
 * 인덱스 적용 + 쿼리 병합 (DB 쿼리 I/O 3회)
 * 시나리오 : V1과 완전 동일, 엔드포인트만 /v2로 변경
 * 부하 테스트 옵션 : 30초, ramp up -> 1분 유지, ramp down 30초 (총 2분)
 * 가상 유저 : 최대 20명
 */

export const options = {
    stages: [
        { duration: '30s', target: 100 },
        { duration: '1m', target: 100 },
        { duration: '30s', target: 0 },
    ],
    thresholds: {
        http_req_duration: ['p(95)<1000'],
    },
};

/** =========================================================
 1. 흐름
 superAdmin 로그인
 -> superAdmin 로그인 토큰 입력
 2. 부하 테스트 시나리오
 더미 데이터 유저 10만 건, 소설 5만 건을 넣는다.
 쿼리 통합 + 인덱스 적용 시, 가동 속도 및 부하 테스트, N+1 문제를 확인한다.
 ========================================================= */

const BASE_URL = 'http://localhost:8080/api';

// 포스트맨 발급 토큰 넣기
const TOKEN = "POSTMAN_TOKEN"

export default function () {
    const params = {
        headers: {
            'Authorization': `Bearer ${TOKEN}`,
            'Content-Type': 'application/json',
        },
        // 그라파나 V1, V2, V3 매트릭 구분 태그
        tags: {
            name: 'V2_DB_Index_Query_Merge',
        },
    };

    // 호출 1 - 필터 OFF
    const resAll = http.get(`${BASE_URL}/admin/dashboard/v2`, params);
    check(resAll, { 'v2 status 200 (ALL)': (r) => r.status === 200 });

    // 호출 2 - 필터 ON
    const resUserRole = http.get(`${BASE_URL}/admin/dashboard/v2?userRole=USER`, params);
    check(resUserRole, { 'v2 status 200 (FILTER)': (r) => r.status === 200 });

    //sleep(1);
}