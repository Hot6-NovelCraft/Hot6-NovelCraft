import http from 'k6/http';
import { check } from 'k6';

/** =========================================================
 1. 흐름
 SMS 인증번호 송신
 -> SMS 인증번호 확인 (인증번호 확인 임시토큰 발급)
 -> 1단계 공통 회원가입 (인증번호 임시토큰 입력 및 1단계 Redis 임시토큰 발급)
 -> 2단계 작가/독자 회원가입 (1단계 Redis 임시토큰 입력 및 2단계 완료 후 DB Insert)

 2. 부하 테스트 시나리오
 단 하나의 전화번호로 악의적인 유저가 1개의 번호로 미친 듯이 인증 요청을 보낼 때 방어 확인
 ========================================================= */

export const options = {
    scenarios: {
        abuse_test: {
            executor: 'shared-iterations',
            vus: 10,       // 10명의 가상 유저가 대기
            iterations: 10, // 한 번호로 정확히 총 10번의 요청
            maxDuration: '10s',
        },
    },
};

export default function () {
    const BASE_URL = 'http://host.docker.internal:8080';

    // 타겟 전화번호 하나로 고정!
    const targetPhoneNo = '01099999999';

    let res = http.post(`${BASE_URL}/api/auth/phone/send`, JSON.stringify({ phoneNo: targetPhoneNo }), {
        headers: { 'Content-Type': 'application/json' },
    });

    // 서버가 어떤 상태 코드를 뱉는지 추적합니다.
    check(res, {
        '정상 발송 및 허용 에러 (200 OK)': (r) => r.status === 200 || (r.status >= 400 && r.status < 500),
        '서버 에러 (500 터짐)': (r) => r.status !== 500,
    });
}