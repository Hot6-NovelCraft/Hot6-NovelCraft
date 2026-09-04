# 수익 통계 (Revenue)

> 대상 코드: `domain/exchange/controller/RevenueController.java`, `service/RevenueService.java`, `service/StatisticsService.java`, `entity/Revenue.java`
> (계좌/출금 흐름은 [exchange.md](./exchange.md) 참고)

## 서비스 플로우 요약

1. **수익 발생** — 회차 구매가 완료되는 순간, 결제 트랜잭션과 **같은 트랜잭션 안에서** 작가별 `Revenue(EPISODE_SALE)` 레코드가 즉시 생성됩니다. 별도의 배치·이벤트 집계가 아닙니다.
2. **정산 개요 조회** — `총 수익 - 총 출금액`으로 가용 잔액을 계산하며, Redis에 30분 캐싱됩니다.
3. **기간별 통계** — WEEKLY/MONTHLY 단위로 Querydsl 집계 쿼리를 매 요청 실시간 실행하고, 결과를 1시간 캐싱합니다.

---

## FLOW A — 수익 발생 시점

```mermaid
flowchart TD
    RV1["회차 구매 완료<br/>EpisodePurchaseFacade"]:::action --> RV2["Redis 분산락<br/>purchase:lock:{userId}"]:::redis
    RV2 --> RV3["포인트 차감 + PointHistory 기록"]:::action
    RV3 --> DB1[("point")]:::db
    RV3 --> RV4["작가별 잔액 재계산<br/>SUM(EPISODE_SALE+SUBSCRIPTION+REFUND)<br/>- SUM(WITHDRAWAL)"]:::action
    RV4 --> DB2[("revenues")]:::db
    RV4 --> RV5["Revenue INSERT<br/>type=EPISODE_SALE (건별 1행)"]:::action
    RV5 --> DB2
    RV5 --> RV6[/"트랜잭션 커밋 후"/]:::data
    RV6 --> R1[["Redis: revenue:overview /<br/>revenue:statistics 캐시 삭제"]]:::redis
    RV6 --> KAFKA1[["NotificationEvent 발행"]]:::redis

    classDef action fill:#101f3c,stroke:#101f3c,color:#ffffff,stroke-width:1px;
    classDef data fill:#d9b98a,stroke:#d9b98a,color:#1a1a1a,stroke-width:1px;
    classDef db fill:#ffffff,stroke:#333333,color:#1a1a1a,stroke-width:1.5px;
    classDef redis fill:#dc382d,stroke:#dc382d,color:#ffffff,stroke-width:1px;
    classDef decision fill:#ffffff,stroke:#c0392b,color:#c0392b,stroke-width:2px;
```

## FLOW B — 정산 개요 조회

```mermaid
flowchart TD
    RB1["GET /api/revenues/me<br/>(정산 개요)"]:::action --> RB2{"Redis 캐시<br/>revenue:overview:{authorId} 존재?"}:::decision
    RB2 -->|"Hit"| RB3["캐시 응답 반환 (DB 조회 없음)"]:::redis
    RB2 -->|"Miss"| RB4["4종 SUM 쿼리<br/>EPISODE_SALE / SUBSCRIPTION / REFUND / WITHDRAWAL"]:::action
    RB4 --> DB1[("revenues")]:::db
    RB4 --> RB5["가용잔액 = (판매+구독+환불) - 출금<br/>※ 수수료(FeePolicy) 미반영"]:::action
    RB5 --> RB6["인증 계좌 정보 병합<br/>(AES 복호화 후 마스킹)"]:::action
    RB6 --> DB2[("bank_accounts")]:::db
    RB6 --> RB7["Redis 저장 (TTL 30분)"]:::redis

    classDef action fill:#101f3c,stroke:#101f3c,color:#ffffff,stroke-width:1px;
    classDef db fill:#ffffff,stroke:#333333,color:#1a1a1a,stroke-width:1.5px;
    classDef redis fill:#dc382d,stroke:#dc382d,color:#ffffff,stroke-width:1px;
    classDef decision fill:#ffffff,stroke:#c0392b,color:#c0392b,stroke-width:2px;
```

## FLOW C — 기간별 수익 통계 조회

```mermaid
flowchart TD
    RC1["GET /api/revenues/me/statistics<br/>period=WEEKLY|MONTHLY&year="]:::action --> RC2{"Redis 캐시<br/>revenue:statistics:{authorId}:{period}:{year}<br/>존재?"}:::decision
    RC2 -->|"Hit"| RC3["캐시 응답 반환"]:::redis
    RC2 -->|"Miss"| RC4["Querydsl 실시간 집계<br/>MONTHLY: DATE_FORMAT(created_at,'%Y-%m')<br/>WEEKLY: YEAR-W주차, type IN(EPISODE_SALE,SUBSCRIPTION)"]:::action
    RC4 --> DB1[("revenues, Querydsl groupBy")]:::db
    RC4 --> RC5["Redis 저장 (TTL 1시간)"]:::redis

    classDef action fill:#101f3c,stroke:#101f3c,color:#ffffff,stroke-width:1px;
    classDef db fill:#ffffff,stroke:#333333,color:#1a1a1a,stroke-width:1.5px;
    classDef redis fill:#dc382d,stroke:#dc382d,color:#ffffff,stroke-width:1px;
    classDef decision fill:#ffffff,stroke:#c0392b,color:#c0392b,stroke-width:2px;
```

## 핵심 로직 노트

- **SUBSCRIPTION 수익은 죽은 코드**: `RevenueType.SUBSCRIPTION`은 모든 합산/통계 쿼리에 포함되어 있지만, 실제로 이 타입의 `Revenue` 행을 생성하는 코드가 리포지토리 전체에 없습니다. 즉 현재 구독 수익은 항상 0으로 집계됩니다 — 구독 결제 완료 시 `RevenueService`를 호출하는 연동이 누락된 상태입니다.
- **정산 개요엔 수수료 미반영**: `RevenueOverviewResponse`의 가용 잔액은 수수료를 빼지 않은 순수 차액이며, `FeePolicy`(10% 수수료)는 실제로 출금을 신청할 때(`WithdrawalService`)만 적용됩니다. 따라서 정산 개요에 보이는 "출금 가능 금액"과 실제 입금액(actualAmount)에는 차이가 있습니다.
- **캐시 무효화 지점**: 회차 구매, 출금 신청/승인/거절 — 이 세 시점에서만 `revenue:overview`, `revenue:statistics:*` 캐시가 evict됩니다. 새로운 수익 발생 트리거를 추가할 때(예: 구독) 캐시 무효화도 같이 잊지 않아야 합니다.
- **통계는 배치가 아닌 실시간 집계**: 미리 집계해두는 테이블 없이 매 캐시-미스마다 Querydsl로 `revenues` 테이블을 직접 그룹핑합니다. 데이터가 커지면 이 지점이 성능 병목이 될 수 있습니다.
