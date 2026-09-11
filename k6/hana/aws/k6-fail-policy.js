import http from 'k6/http';
import { check, sleep } from 'k6';
import { Rate, Counter } from 'k6/metrics';

/** =========================================================
 목적: Redis 장애(redis-master kill) 상황에서 API 위험도별 차등 정책이
       실제로 동작하는지 검증한다.

 - Fail-Open  : GET /api/novels/ranking (조회, permitAll)
                → Redis가 죽어도 무조건 200이 떨어져야 함 (가용성 우선)
 - Fail-Closed: PATCH /api/auth/users/me/password (변경, 인증 필요)
                → Redis가 죽어있는 동안은 503으로 차단되어야 함 (JwtFilter가
                  isSafeApi()==false로 판정 → 컨트롤러 도달 전에 즉시 차단, 보안 우선)

 ⚠️ 정상 상태코드는 200 / 503 뿐이다. 500이 하나라도 뜨면 그건 정책이 아니라
    버그(미처리 예외)이므로 절대 "정상"으로 카운트하지 않는다 — 아래
    unexpectedServerErrorCount로 따로 잡아서 즉시 드러나게 한다.

 ⚠️ 반드시 이 스크립트 실행 중간에 별도 SSH/SSM 세션에서
    docker kill <현재 master> 를 실행해야 의미 있는 결과가 나온다.
        → Master kill 전엔 password가 200(정상 변경)
        → Master kill 중엔 503(차단)이 정상
    로그 타임스탬프로 구간을 나눠서 봐야 함

 ⚠️ 비밀번호 변경 API는 호출마다 실제로 DB 값이 바뀌므로
    이 테스트는 loadtest191~200 (10명) 전용 계정만 사용한다.
    다른 시나리오(A, chaos-fallback)와 유저 풀이 겹치지 않게 하기 위함.

 ⚠️ [2026-09-11 라운드 1 결과 반영] 지난 실행에서 fail_closed 503 발동률이
    0%로 나왔는데, http_req_duration max(16.6초)가 PATCH 쪽 개별 로그
    어디에도 안 잡혀서 "로그인(AuthService.saveRefreshToken) 쪽에서 걸린
    지연이 아닌가" 하는 가설이 생겼다. 이번엔 로그인 요청도 timing 로그를
    남겨서 이 가설을 직접 검증한다.
 ========================================================= */

// ---------- 실행 환경 ----------
const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const VUS = Number(__ENV.VUS) || 10;
const DURATION = __ENV.DURATION || '2m';

// 비밀번호 변경용 전용 유저 10명 (loadtest191~200)
const PASSWORD_USERS = [];
for (let i = 191; i <= 200; i++) {
    PASSWORD_USERS.push(`loadtest${i}@test.com`);
}

// 최초 상태는 load-test-data.sql 시드값인 'test1234'
const PASSWORDS = ['test1234', 'test1234B1'];

// ---------- 커스텀 메트릭 ----------
const rankingOkRate = new Rate('fail_open_ranking_200_rate');          // Fail-Open: 200 비율
const passwordBlockedRate = new Rate('fail_closed_password_503_rate'); // Fail-Closed: 503 비율
const unexpectedServerErrorCount = new Counter('unexpected_500_count'); // Fail-Open/Closed 통틀어 500이 뜬 횟수 (0이어야 정상)

export const options = {
    scenarios: {
        fail_open_ranking: {
            executor: 'constant-vus',
            vus: VUS,
            duration: DURATION,
            exec: 'checkFailOpen',
        },
        fail_closed_password: {
            executor: 'constant-vus',
            vus: PASSWORD_USERS.length,
            duration: DURATION,
            exec: 'checkFailClosed',
        },
    },
    thresholds: {
        // 500이 단 1건이라도 나오면 임계값 위반으로 즉시 표시되게 함
        'unexpected_500_count': ['count==0'],
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

    if (res.status === 500) {
        unexpectedServerErrorCount.add(1);
        console.error(`[예상 못 한 500] fail-open-ranking, time=${new Date().toISOString()}`);
    }

    sleep(1);
}

// ---------- 시나리오 2: Fail-Closed (비밀번호 변경) ----------
export function checkFailClosed() {
    const user = PASSWORD_USERS[(__VU - 1) % PASSWORD_USERS.length];

    const oldPassword = PASSWORDS[__ITER % 2];
    const newPassword = PASSWORDS[(__ITER + 1) % 2];

    const loginRes = http.post(
        `${BASE_URL}/api/auth/login`,
        JSON.stringify({ email: user, password: oldPassword }),
        { headers: { 'Content-Type': 'application/json' }, tags: { name: 'fail-closed-login' } }
    );

    // [신규] 로그인 요청 timing 로그 — 지난 라운드의 16.6초 hang이
    // 여기(AuthService.saveRefreshToken)에서 난 것인지 직접 확인하기 위함
    console.log(JSON.stringify({
        type: 'fail-closed-login',
        iter: __ITER,
        vu: __VU,
        status: loginRes.status,
        duration_ms: loginRes.timings.duration,
        timestamp: new Date().toISOString(),
    }));

    if (loginRes.status !== 200) {
        // Redis 장애 중엔 로그인 자체도 영향받을 수 있어 실패 시 그냥 넘어감 (다음 이터레이션에서 재시도)
        console.warn(`[로그인 실패] ${user}, status: ${loginRes.status}`);
        sleep(1);
        return;
    }

    const token = JSON.parse(loginRes.body).data.accessToken;

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

    const isNormal = check(patchRes, {
        // 정상은 200(변경 성공) 또는 503(Redis 장애로 차단) 뿐. 500은 여기 포함하지 않음 — 500이 뜨면 이 check는 실패로 잡혀야 함
        'Fail-Closed: 200 또는 503 중 하나 (500은 버그)': (r) => r.status === 200 || r.status === 503,
    });
    passwordBlockedRate.add(patchRes.status === 503);

    if (patchRes.status === 500) {
        unexpectedServerErrorCount.add(1);
        console.error(`[예상 못 한 500] fail-closed-password, user=${user}, time=${new Date().toISOString()}`);
    }

    // 미해결 항목① 대응: 요청별 timestamp + 소요시간 로그
    // kill 직후 이 요청이 "즉시 503"인지 "15초 hang 후 503"인지 구분하기 위한 원자료
    console.log(JSON.stringify({
        type: 'fail-closed-password',
        iter: __ITER,
        vu: __VU,
        status: patchRes.status,
        duration_ms: patchRes.timings.duration,
        timestamp: new Date().toISOString(),
    }));

    // 비밀번호 변경이 실제로 성공(200)했을 때만 다음 이터레이션에서 A/B가 정상적으로 맞물림
    // 503으로 막혔다면 비밀번호는 그대로이므로, 다음 이터레이션도 같은 oldPassword로 재시도됨

    sleep(1);
}

// ---------- 결과 요약 ----------
export function handleSummary(data) {
    const openRate = data.metrics.fail_open_ranking_200_rate
        ? data.metrics.fail_open_ranking_200_rate.values.rate * 100 : 0;
    const closedRate = data.metrics.fail_closed_password_503_rate
        ? data.metrics.fail_closed_password_503_rate.values.rate * 100 : 0;
    const error500Count = data.metrics.unexpected_500_count
        ? data.metrics.unexpected_500_count.values.count : 0;

    console.log('\n========================================');
    console.log('  Fail-Open / Fail-Closed 차등 정책 테스트 결과');
    console.log('========================================');
    console.log(`  Fail-Open  (랭킹 조회) 200 비율     : ${openRate.toFixed(2)}%  (항상 높아야 정상)`);
    console.log(`  Fail-Closed(비번 변경) 503 비율     : ${closedRate.toFixed(2)}%  (redis kill 구간에서만 높아야 정상)`);
    console.log(`  예상 못 한 500 발생 건수            : ${error500Count}건  (0이어야 정상, 1건이라도 있으면 버그)`);
    console.log('  ※ 전체 구간 평균이 아니라, redis-master kill 전/후로 로그를 나눠서 비교해야 의미가 있습니다.');
    console.log('========================================\n');

    return { stdout: JSON.stringify(data, null, 2) };
}