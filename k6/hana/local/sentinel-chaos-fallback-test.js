import http from 'k6/http';
import { check, sleep } from 'k6';

/** =========================================================
 1. 흐름
    더미 데이터에 등록된 유저 또는 Admin의 포스트맨 토큰을 입력한다.
        -> JWT 블랙리스트 검증이 유효한 마이페이지 API 를 타겟으로 한다.
 2. 테스트 시나리오
 2분동안 Redis가 죽어도 무조건 200이 떨어져야 한다
 ========================================================= */

export const options = {
    // 트래픽을 좀 더 강하게 주어 Failover 시 스레드 점유 문제가 없는지 확인
    vus: 100,
    duration: '2m',
};

    // 유효한 Access Token 입력 (DB에 있는 유저의 토큰)
    const ACCESS_TOKEN = 'ACCESS_TOKEN_HERE';

export default function () {
    // JWT 블랙리스트 검증(Redis 의존성)이 포함된 마이페이지 API 타겟
    const url = 'http://localhost:8080/api/auth/users/me';

    const params = {
        headers: {
            'Authorization': `Bearer ${ACCESS_TOKEN}`,
            'Content-Type': 'application/json',
        },
    };

    const res = http.get(url, params);

    // 검증 : Redis가 죽어도 DB 폴백으로 무조건 200이 떨어져야하고 500은 0이어야 함
    check(res, {
        'DB Fallback 성공 (200)': (r) => r.status === 200,
        '서버 터짐 (500 - 0% 여야 함)': (r) => r.status === 500,
    });

    // 1초에 1번씩 요청
    sleep(1);
}