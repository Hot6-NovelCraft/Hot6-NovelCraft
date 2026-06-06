import http from 'k6/http';
import { check, sleep } from 'k6';

/** V1 테스트
 * 인덱스 미적용 + 쿼리 분할 (DB 쿼리 I/O 8회)
 * 시나리오 : 관리자 대시보드 진입 후 역할 필터 적용
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
 쿼리 분할 + 인덱스 미 적용 시, 가동 속도 및 부하 테스트, N+1 문제를 확인한다.
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
            name: 'V1_DB_NoIndex_Query_Split',
        },
    };

    /** 호출 1 - 어드민 대시보드 페이지 진입
     * 필터 없이 전체 통계 조회
     * defaultValue="ALL" 작동
     */
    const resAll = http.get(`${BASE_URL}/admin/dashboard/v1`, params);
    check(resAll, { 'v1 status 200 (ALL)': (r) => r.status === 200 });

    /** 호출 2 - 회원 역할 필터 적용 후 재조회
     * 관리자가 "일반 회원만 보기" 같은 필터링을 거는 실사용 시나리오
     * 실제 사용 가능한 UserRole enum값으로 교체 (USER/ADMIN/SUPER_ADMIN)
     */
    const resUserRole = http.get(`${BASE_URL}/admin/dashboard/v1?userRole=USER`, params);
    check(resUserRole, { 'v1 status 200 (FILTER)': (r) => r.status === 200 });

    // 다음 iteration 시작 전 1초 대기 (가상 유저당 호흡 조절)
    // sleep(1);
}