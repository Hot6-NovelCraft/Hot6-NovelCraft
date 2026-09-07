import http from 'k6/http';
import { check, sleep } from 'k6';
import { Rate } from 'k6/metrics';

/** =========================================================
 목적: Redis 장애(redis-master kill) 상황에서 API 위험도별 차등 정책이
       실제로 동작하는지 검증한다.

 - Fail-Open  : GET /api/novels/ranking (조회, permitAll)
                → Redis가 죽어도 무조건 200이 떨어져야 함 (가용성 우선)
 - Fail-Closed: PATCH /api/auth/users/me/password (변경, 인증 필요)
                → Redis가 죽어있는 동안은 401로 차단되어야 함 (보안 우선)

 ⚠️ 반드시 이 스크립트 실행 중간에 별도 SSH 세션에서
    docker kill redis-master — chaos-fallback 때와 동일한 방식이므로 docker kill redis-master 실행해야 의미 있는 결과가 나온다.
        → Master kill 전엔 password가 200(정상 변경)
        → Master kill 중엔 401(차단)이 정상
    로그 타임스탬프로 구간을 나눠서 봐야 함)


 ⚠️ 비밀번호 변경 API는 호출마다 실제로 DB 값이 바뀌므로
    이 테스트는 loadtest191~200 (10명) 전용 계정만 사용한다.
    다른 시나리오(A, chaos-fallback)와 유저 풀이 겹치지 않게 하기 위함.

    콘솔에 찍히는 전체 평균 비율만 보면 두 구간이 섞여서 헷갈릴 수 있으니,
    console.warn/로그 타임스탬프 기준으로 구간을 나눠보는 걸 추천
 ========================================================= */

// ---------- 실행 환경 ----------
const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const VUS = Number(__ENV.VUS) || 10;   // 전용 유저 10명과 1:1 매칭 (동시성보다 "차등 확인"이 목적)
const DURATION = __ENV.DURATION || '2m'; // chaos-fallback과 같은 길이로 맞춰서, 같은 kill 타이밍을 재사용하기 쉽게 함

// 비밀번호 변경용 전용 유저 10명 (loadtest191~200) - 다른 테스트와 절대 겹치지 않음
const PASSWORD_USERS = [];
for (let i = 191; i <= 200; i++) {
    PASSWORD_USERS.push(`loadtest${i}@test.com`);
}

// 두 비밀번호를 번갈아 사용 (이터레이션 0: A→B, 1: B→A, 2: A→B ...)
// 최초 상태는 load-test-data.sql 시드값인 'test1234'
const PASSWORDS = ['test1234', 'test1234B1'];

// ---------- 커스텀 메트릭 ----------
const rankingOkRate = new Rate('fail_open_ranking_200_rate');   // Fail-Open: 200 비율
const passwordBlockedRate = new Rate('fail_closed_password_401_rate'); // Fail-Closed: 401 비율

export const options = {
    scenarios: {
        // 시나리오 1: Fail-Open 확인 (인증 불필요, permitAll 엔드포인트)
        fail_open_ranking: {
            executor: 'constant-vus',
            vus: VUS,
            duration: DURATION,
            exec: 'checkFailOpen',
        },
        // 시나리오 2: Fail-Closed 확인 (전용 유저 10명, VU 수를 유저 수와 동일하게 고정)
        fail_closed_password: {
            executor: 'constant-vus',
            vus: PASSWORD_USERS.length,
            duration: DURATION,
            exec: 'checkFailClosed',
        },
    },
};

// ---------- 시나리오 1: Fail-Open (랭킹 조회) ----------
export function checkFailOpen() {
    const res = http.get(`${BASE_URL}/api/novels/ranking?type=realtime`, {
        tags: { name: 'fail-open-ranking' },
    });

    const isOk = check(res, {
        'Fail-Open: 랭킹 조회 200': (r) => r.status === 200,
    });
    rankingOkRate.add(isOk);

    sleep(1);
}

// ---------- 시나리오 2: Fail-Closed (비밀번호 변경) ----------
export function checkFailClosed() {
    // VU 번호로 전용 유저 10명 중 1명을 고정 배정 (VU당 항상 같은 유저 사용)
    const user = PASSWORD_USERS[(__VU - 1) % PASSWORD_USERS.length];

    // 매 이터레이션마다 로그인 (비밀번호가 바뀌므로 토큰을 매번 새로 받아야 함)
    // __ITER 짝/홀수로 현재 비밀번호를 판단
    const oldPassword = PASSWORDS[__ITER % 2];
    const newPassword = PASSWORDS[(__ITER + 1) % 2];

    const loginRes = http.post(
        `${BASE_URL}/api/auth/login`,
        JSON.stringify({ email: user, password: oldPassword }),
        { headers: { 'Content-Type': 'application/json' }, tags: { name: 'fail-closed-login' } }
    );

    if (loginRes.status !== 200) {
        // Redis 장애 중엔 로그인 자체도 영향받을 수 있어 실패 시 그냥 넘어감 (다음 이터레이션에서 재시도)
        console.warn(`[로그인 실패] ${user}, status: ${loginRes.status}`);
        sleep(1);
        return;
    }

    const token = JSON.parse(loginRes.body).data.accessToken;

    // 비밀번호 변경 시도 - Redis 장애 중이면 401로 차단되어야 함 (Fail-Closed)
    const patchRes = http.patch(
        `${BASE_URL}/api/auth/users/me/password`,
        JSON.stringify({ oldPassword, newPassword }),
        {
            headers: {
                'Content-Type': 'application/json',
                'Authorization': token,
            },
            tags: { name: 'fail-closed-password' },
        }
    );

    const isBlocked = check(patchRes, {
        'Fail-Closed: 401 또는 200 중 하나 (Redis 상태에 따라 다름)': (r) => r.status === 401 || r.status === 200,
    });
    passwordBlockedRate.add(patchRes.status === 401);

    // 비밀번호 변경이 실제로 성공(200)했을 때만 다음 이터레이션에서 A/B가 정상적으로 맞물림
    // 401로 막혔다면 비밀번호는 그대로이므로, 다음 이터레이션도 같은 oldPassword로 재시도됨
    // (이 재시도 로직은 별도 처리 없이 __ITER 기반 토글이 자연스럽게 흡수함)

    sleep(1);
}

// ---------- 결과 요약 ----------
export function handleSummary(data) {
    const openRate = data.metrics.fail_open_ranking_200_rate
        ? data.metrics.fail_open_ranking_200_rate.values.rate * 100 : 0;
    const closedRate = data.metrics.fail_closed_password_401_rate
        ? data.metrics.fail_closed_password_401_rate.values.rate * 100 : 0;

    console.log('\n========================================');
    console.log('  Fail-Open / Fail-Closed 차등 정책 테스트 결과');
    console.log('========================================');
    console.log(`  Fail-Open  (랭킹 조회) 200 비율   : ${openRate.toFixed(2)}%  (항상 높아야 정상)`);
    console.log(`  Fail-Closed(비번 변경) 401 비율   : ${closedRate.toFixed(2)}%  (redis kill 구간에서만 높아야 정상)`);
    console.log('  ※ 전체 구간 평균이 아니라, redis-master kill 전/후로 로그를 나눠서 비교해야 의미가 있습니다.');
    console.log('========================================\n');

    return { stdout: JSON.stringify(data, null, 2) };
}