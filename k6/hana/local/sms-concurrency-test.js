import http from 'k6/http';
import { check, sleep } from 'k6';

/** =========================================================
 1. 흐름
    SMS 인증번호 송신
        -> SMS 인증번호 확인 (인증번호 확인 임시토큰 발급)
        -> 1단계 공통 회원가입 (인증번호 임시토큰 입력 및 1단계 Redis 임시토큰 발급)
        -> 2단계 작가/독자 회원가입 (1단계 Redis 임시토큰 입력 및 2단계 완료 후 DB Insert)
 2. 부하 테스트 시나리오
    500명의 가상 유저가 동시에 몰려 들었을 때 총 500번의 회원가입 플로우 발생
 ========================================================= */

export const options = {
    scenarios: {
        signup_flow: {
            executor: 'shared-iterations',
            vus: 500,         // 500명이 동시에 접속
            iterations: 500,  // 총 500번의 회원가입 플로우 실행
            maxDuration: '1m', // 모두 완료될 때까지 최대 1분 대기
        },
    },
};

export default function () {
    // 로컬 환경 테스트용 주소
    const BASE_URL = 'http://host.docker.internal:8080';

    // 데이터 중복(409 Conflict) 방지를 위한 동적 고유 데이터 생성
    const phoneNo = `010${String(__VU).padStart(4, '0')}${String(__ITER).padStart(4, '0')}`.substring(0, 11);
    const dynamicEmail = `testuser_${__VU}_${__ITER}@test.com`;
    const dynamicNickname = `Nick_${__VU}_${__ITER}`;

    // =========================================================
    // SMS 인증 전송
    // =========================================================
    let res = http.post(`${BASE_URL}/api/auth/phone/send`, JSON.stringify({ phoneNo: phoneNo }), {
        headers: { 'Content-Type': 'application/json' },
    });

    check(res, { '1. SMS 전송 성공': (r) => r.status === 200 });

    // 에러 방어 로직 (서버가 뻗거나 에러가 나면 여기서 중단)
    if (res.status !== 200) {
        console.error(`[1단계 실패] Status: ${res.status}, Body: ${res.body}`);
        return;
    }

    // =========================================================
    // SMS 인증 확인 (난수 123456 고정 세팅 반영) -> 임시 토큰 받기
    // =========================================================
    res = http.post(`${BASE_URL}/api/auth/phone/verify`, JSON.stringify({
        phoneNo: phoneNo,
        verificationCode: '123456'
    }), {
        headers: {
            'Content-Type': 'application/json',
        },
    });

    check(res, { '2. SMS 인증 성공': (r) => r.status === 200 });

    if (res.status !== 200) {
        console.error(`[2단계 실패] Status: ${res.status}, Body: ${res.body}`);
        return;
    }


    const responseBody = res.json();
    const tempToken = (responseBody && responseBody.data) || null;

    // 디버깅용: 토큰이 제대로 뽑혔는지 터미널에 출력해보기
    console.log(`추출된 토큰: ${tempToken}`);

    // =========================================================
    // 기본 회원가입 진행
    // =========================================================
    res = http.post(`${BASE_URL}/api/auth/signup`, JSON.stringify({
        email: dynamicEmail,
        password: "one1234!!",
        nickname: dynamicNickname,
        phoneNo: phoneNo,
        birthDay: "1999-01-01",
        tempToken: tempToken
    }), {
        headers: { 'Content-Type': 'application/json' },
    });

    check(res, { '3. 회원가입 성공': (r) => r.status === 200 });

    if (res.status !== 200) {
        console.error(`[3단계 실패] Status: ${res.status}, Body: ${res.body}`);
        return;
    }

    // 백엔드 응답에 맞게 사용
    let signupToken = res.json().data.tempToken;

    // =========================================================
    // 독자(Reader) 부가 정보 입력 및 최종 권한 등록
    // =========================================================
    res = http.post(`${BASE_URL}/api/auth/signup/reader`, JSON.stringify({
        role: 'READER',
        nickname: dynamicNickname,
        preferredGenres: ["FANTASY", "MODERN"],
        readingGoal: "STEADY",
        notifyNewNovel: true,
        notifyEvent: false
    }), {
        headers: {
            'Content-Type': 'application/json',
            'Authorization': `Bearer ${signupToken}`
        },
    });

    check(res, { '4. 최종 역할 등록 성공': (r) => r.status === 200 });

    if (res.status !== 200) {
        console.error(`[4단계 실패] Status: ${res.status}, Body: ${res.body}`);
    }

    // 너무 빠른 요청으로 인한 로컬 PC 포트 고갈을 막기 위한 0.1초 휴식
    sleep(0.1);
}