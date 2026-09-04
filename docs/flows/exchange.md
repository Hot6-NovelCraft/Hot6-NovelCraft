# 정산 / 출금 (Exchange)

> 대상 코드: `domain/exchange/**` (BankAccount, Withdrawal — 수익 통계는 [revenue.md](./revenue.md) 참고)
> 관련 파일: `BankAccountService`, `WithdrawalService`, `AesEncryptionUtil`, `LocalBankVerificationClient`

## 서비스 플로우 요약

1. **계좌 등록 + 1원 인증** — 계좌번호는 AES-GCM으로 암호화해 저장하고, 1원 송금 코드를 발급해 5분 내 5회 이내로 인증해야 활성화됩니다.
2. **출금 신청** — Redis 락으로 동시 중복신청을 막고, `revenues` 테이블의 수익 합계에서 이미 출금한 금액을 뺀 값으로 가용 잔액을 계산합니다. **신청 시점에 즉시 잔액이 차감**됩니다.
3. **관리자 승인/거절** — Admin 도메인이 `WithdrawalService`에 위임하는 구조로, 승인 시 실제 송금 연동 없이 상태만 COMPLETED로 바뀌고, 거절 시 차감된 잔액이 환불(REFUND) 레코드로 복원됩니다.

---

## FLOW A — 계좌 등록 + 1원 인증

```mermaid
flowchart TD
    E1["계좌 등록 요청"]:::action --> E2{"은행 점검시간<br/>23:30~00:30?"}:::decision
    E2 -->|"Yes"| EXE1(["예외: BANK_API_UNAVAILABLE"]):::exception
    E2 -->|"No"| E3["기존 계좌 전체 삭제<br/>(재등록 시 이전 계좌 폐기)"]:::action
    E3 --> DB1[("bank_accounts")]:::db
    E3 --> E4["AES-GCM 계좌번호 암호화<br/>(nonce 매번 랜덤)"]:::action
    E4 --> E5{"동일 암호문이<br/>이미 인증완료됨?"}:::decision
    E5 -->|"Yes"| EXE2(["예외: BANK_ACCOUNT_DUPLICATE"]):::exception
    E5 -->|"No"| E6["예금주 검증"]:::action
    E6 --> EXT1[["외부: BankVerificationClient"]]:::redis
    E6 --> E7{"예금주 불일치?"}:::decision
    E7 -->|"Yes"| EXE3(["예외: HOLDER_MISMATCH"]):::exception
    E7 -->|"No"| E8["BankAccount 저장 (isVerified=false)"]:::action
    E8 --> DB1
    E8 --> E9["1원 송금 + 4자리 인증코드 발급<br/>AccountVerification 저장 (TTL 5분)"]:::action
    E9 --> DB2[("account_verifications")]:::db

    E10["인증코드 확인 요청"]:::action --> E11{"만료됨?"}:::decision
    E11 -->|"Yes"| EXE4(["예외: VERIFICATION_EXPIRED"]):::exception
    E11 -->|"No"| E12{"시도횟수 ≥ 5?"}:::decision
    E12 -->|"Yes"| EXE5(["예외: MAX_ATTEMPT_EXCEEDED"]):::exception
    E12 -->|"No"| E13["시도횟수 +1<br/>(비교 실행 전 선증가)"]:::action
    E13 --> E14{"코드 일치?"}:::decision
    E14 -->|"No"| E15[/"실패 응답 (남은횟수 반환)"/]:::data
    E14 -->|"Yes"| E16["계좌 + 인증 상태 verified=true"]:::action
    E16 --> DB1
    E16 --> R1[["Redis: revenue:overview 캐시 evict"]]:::redis

    classDef action fill:#101f3c,stroke:#101f3c,color:#ffffff,stroke-width:1px;
    classDef data fill:#d9b98a,stroke:#d9b98a,color:#1a1a1a,stroke-width:1px;
    classDef db fill:#ffffff,stroke:#333333,color:#1a1a1a,stroke-width:1.5px;
    classDef redis fill:#dc382d,stroke:#dc382d,color:#ffffff,stroke-width:1px;
    classDef decision fill:#ffffff,stroke:#c0392b,color:#c0392b,stroke-width:2px;
    classDef exception fill:#ffffff,stroke:#c0392b,color:#c0392b,stroke-width:2px;
```

## FLOW B — 출금 신청 & 승인/거절

```mermaid
flowchart TD
    W1["출금 신청"]:::action --> W2["Redis SETNX<br/>lock:withdrawal:{authorId} (TTL 5s)"]:::redis
    W2 --> W3{"락 획득 실패?"}:::decision
    W3 -->|"Yes"| EXW1(["예외: WITHDRAWAL_PENDING_EXISTS"]):::exception
    W3 -->|"No"| W4{"인증된 계좌 존재?"}:::decision
    W4 -->|"No"| EXW2(["예외: BANK_ACCOUNT_NOT_VERIFIED"]):::exception
    W4 -->|"Yes"| W5{"DB상 PENDING 출금<br/>이미 존재? (이중 방어)"}:::decision
    W5 -->|"Yes"| EXW3(["예외: WITHDRAWAL_PENDING_EXISTS"]):::exception
    W5 -->|"No"| W6{"최소금액 10,000원 이상?"}:::decision
    W6 -->|"No"| EXW4(["예외: WITHDRAWAL_BELOW_MINIMUM"]):::exception
    W6 -->|"Yes"| W7["가용잔액 = SUM(수익) - SUM(출금)"]:::action
    W7 --> DB1[("revenues, SUM 쿼리")]:::db
    W7 --> W8{"잔액 부족?"}:::decision
    W8 -->|"Yes"| EXW5(["예외: WITHDRAWAL_INSUFFICIENT_BALANCE"]):::exception
    W8 -->|"No"| W9["수수료 10% 계산<br/>Revenue(type=WITHDRAWAL) 즉시 INSERT<br/>(신청 시점에 잔액 선차감)"]:::action
    W9 --> DB1
    W9 --> W10["Withdrawal 생성 (PENDING)"]:::action
    W10 --> DB2[("withdrawals")]:::db
    W10 --> R1[["Redis: 수익/통계 캐시 evict"]]:::redis
    W10 --> W11["Lua 스크립트로 락 해제<br/>(GET-compare-DEL, uuid 일치 시에만)"]:::redis

    W12["관리자 승인/거절<br/>(Admin 도메인 → WithdrawalService 위임)"]:::action --> W13{"승인 or 거절?"}:::decision
    W13 -->|"승인"| W14["PENDING→PROCESSING→COMPLETED<br/>(실제 송금 연동 없음, 로그만 기록)"]:::action
    W13 -->|"거절"| W15["PENDING→REJECTED<br/>Revenue(type=REFUND) INSERT로 잔액 복원"]:::action
    W14 --> DB2
    W15 --> DB1
    W15 --> DB2
    W14 --> R1
    W15 --> R1

    classDef action fill:#101f3c,stroke:#101f3c,color:#ffffff,stroke-width:1px;
    classDef data fill:#d9b98a,stroke:#d9b98a,color:#1a1a1a,stroke-width:1px;
    classDef db fill:#ffffff,stroke:#333333,color:#1a1a1a,stroke-width:1.5px;
    classDef redis fill:#dc382d,stroke:#dc382d,color:#ffffff,stroke-width:1px;
    classDef decision fill:#ffffff,stroke:#c0392b,color:#c0392b,stroke-width:2px;
    classDef exception fill:#ffffff,stroke:#c0392b,color:#c0392b,stroke-width:2px;
```

## 핵심 로직 노트

- **Redis 락은 Redisson이 아닌 순수 SETNX**: `domain/exchange`에는 Redisson 의존성이 없고, `redisTemplate.opsForValue().setIfAbsent(...)` + Lua 스크립트(GET-compare-DEL)로 직접 분산락을 구현했습니다. DB 유니크 조건(`existsByAuthorIdAndStatus`)이 이중 안전장치로 붙어 있습니다.
- **잔액은 신청 시점에 선차감**: 출금 승인 대기(PENDING) 상태에서도 이미 `Revenue(WITHDRAWAL)` 레코드가 잔액에서 빠져 있으므로, `정산 개요(가용 잔액)`는 승인 전부터 줄어들어 보입니다. 거절 시에만 `REFUND` 레코드로 복원됩니다.
- **실제 은행 송금 연동 없음**: 관리자가 "승인"을 누르면 바로 PROCESSING→COMPLETED로 전이되며, 실제 은행 API 호출은 없고 로그만 남습니다 (주석상 "실제 송금 처리(현재는 로그만 기록)").
- **1원 인증 코드도 실제 발송되지 않음**: `LocalBankVerificationClient`는 로컬/개발 환경용 목(mock)으로, 인증코드를 사용자에게 실제로 전달하지 않고 서버 로그에만 남깁니다. 은행 점검시간(23:30~00:30) 시뮬레이션도 이 목 클라이언트에 하드코딩되어 있습니다.
