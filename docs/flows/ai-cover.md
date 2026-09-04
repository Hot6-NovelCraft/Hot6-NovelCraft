# AI 커버 생성 (Cover AI)

> 대상 코드: `domain/coverai/**`
> 관련 파일: `CoverController`, `CoverService`, `CoverGenerationProducer/Consumer`, `GeminiClient`
> Kafka 토픽: `cover-generation-events` (consumer group: `cover-generation-service`, concurrency=3)

## 서비스 플로우 요약

1. **생성 요청** — 작가 본인 소유 소설인지 확인 후 `CoverJob(PENDING)`을 만들고, DB 트랜잭션이 커밋된 뒤에만 Kafka로 이벤트를 발행합니다. 응답은 `jobId`만 즉시 반환하는 비동기 방식입니다.
2. **비동기 처리(Consumer)** — Gemini 이미지 생성(최대 3회 재시도) → S3 업로드(최대 3회 재시도) → 포인트 300 차감 순으로 진행하며, 어느 단계든 실패하면 Job을 FAILED로 남기고 이미 차감한 포인트를 환불합니다.
3. **상태 폴링** — 클라이언트가 `jobId`로 상태를 주기적으로 조회합니다 (Redis 캐싱 없이 매번 DB 조회).

---

## FLOW A — 커버 생성 요청

```mermaid
flowchart TD
    AC1["POST /api/ai/novels/{id}/cover"]:::action --> AC2{"role = AUTHOR?"}:::decision
    AC2 -->|"No"| EXA1(["예외: NOT_AUTHOR"]):::exception
    AC2 -->|"Yes"| AC3{"본인 소유 소설?"}:::decision
    AC3 -->|"No"| EXA2(["예외: NOT_NOVEL_OWNER"]):::exception
    AC3 -->|"Yes"| AC4["CoverJob 생성<br/>status=PENDING, jobId=UUID"]:::action
    AC4 --> DB1[("cover_jobs")]:::db
    AC4 --> AC5[/"내부 이벤트 발행<br/>(AFTER_COMMIT 시점에만)"/]:::data
    AC5 --> AC6[["Kafka: cover-generation-events<br/>key=novelId"]]:::redis
    AC6 --> AC7[/"jobId 즉시 응답 (status=PENDING)"/]:::data

    classDef action fill:#101f3c,stroke:#101f3c,color:#ffffff,stroke-width:1px;
    classDef data fill:#d9b98a,stroke:#d9b98a,color:#1a1a1a,stroke-width:1px;
    classDef db fill:#ffffff,stroke:#333333,color:#1a1a1a,stroke-width:1.5px;
    classDef redis fill:#dc382d,stroke:#dc382d,color:#ffffff,stroke-width:1px;
    classDef decision fill:#ffffff,stroke:#c0392b,color:#c0392b,stroke-width:2px;
    classDef exception fill:#ffffff,stroke:#c0392b,color:#c0392b,stroke-width:2px;
```

## FLOW B — 비동기 처리 (Kafka Consumer)

```mermaid
flowchart TD
    CB1[["Kafka Consumer<br/>group=cover-generation-service, concurrency=3"]]:::redis --> CB2{"Job 존재?"}:::decision
    CB2 -->|"No"| EXCB1(["예외 (DLQ 미구성, 미처리)"]):::exception
    CB2 -->|"Yes"| CB3{"status != PENDING?<br/>(중복 메시지)"}:::decision
    CB3 -->|"Yes"| CB4[/"스킵, 로그만 남김"/]:::data
    CB3 -->|"No"| CB5["status = PROCESSING"]:::action
    CB5 --> DB1[("cover_jobs")]:::db
    CB5 --> CB6["프롬프트 생성 후 Gemini 이미지 생성<br/>(최대 3회 재시도)"]:::action
    CB6 --> EXT1[["외부: Google Gemini API<br/>gemini-3.1-flash-image-preview"]]:::redis
    CB6 --> CB7{"이미지 획득 성공?"}:::decision
    CB7 -->|"No (3회 초과)"| CB8["status = FAILED<br/>+ 포인트 환불 (차감된 경우)"]:::action
    CB7 -->|"Yes"| CB9["S3 업로드 (최대 3회 재시도)"]:::action
    CB9 --> EXT2[["외부: AWS S3<br/>covers/{novelId}/{uuid}.png"]]:::redis
    CB9 --> CB10{"업로드 성공?"}:::decision
    CB10 -->|"No"| CB8
    CB10 -->|"Yes"| CB11["포인트 300 차감 + PointHistory"]:::action
    CB11 --> DB2[("point")]:::db
    CB11 --> CB12["status = COMPLETED<br/>+ coverImageUrl 저장"]:::action
    CB12 --> DB1
    CB8 --> DB1

    classDef action fill:#101f3c,stroke:#101f3c,color:#ffffff,stroke-width:1px;
    classDef data fill:#d9b98a,stroke:#d9b98a,color:#1a1a1a,stroke-width:1px;
    classDef db fill:#ffffff,stroke:#333333,color:#1a1a1a,stroke-width:1.5px;
    classDef redis fill:#dc382d,stroke:#dc382d,color:#ffffff,stroke-width:1px;
    classDef decision fill:#ffffff,stroke:#c0392b,color:#c0392b,stroke-width:2px;
    classDef exception fill:#ffffff,stroke:#c0392b,color:#c0392b,stroke-width:2px;
```

## FLOW C — 상태 폴링

```mermaid
flowchart TD
    CP1["GET /api/ai/novels/cover/status/{jobId}"]:::action --> CP2{"jobId 존재?"}:::decision
    CP2 -->|"No"| EXCP1(["예외: JOB_NOT_FOUND"]):::exception
    CP2 -->|"Yes"| CP3{"요청자 = job.userId?"}:::decision
    CP3 -->|"No"| EXCP2(["예외: NOT_NOVEL_OWNER"]):::exception
    CP3 -->|"Yes"| CP4["현재 status / coverImageUrl 반환<br/>(Redis 캐싱 없음, 매번 DB 조회)"]:::action
    CP4 --> DB1[("cover_jobs")]:::db

    classDef action fill:#101f3c,stroke:#101f3c,color:#ffffff,stroke-width:1px;
    classDef db fill:#ffffff,stroke:#333333,color:#1a1a1a,stroke-width:1.5px;
    classDef decision fill:#ffffff,stroke:#c0392b,color:#c0392b,stroke-width:2px;
    classDef exception fill:#ffffff,stroke:#c0392b,color:#c0392b,stroke-width:2px;
```

## 핵심 로직 노트

- **Novel.coverImageUrl은 자동 갱신되지 않음**: Job이 COMPLETED 되어도 `Novel` 엔티티의 커버 URL은 그대로입니다. 클라이언트가 폴링으로 완료를 감지한 뒤 별도로 `PATCH /api/novels/{novelId}/cover`를 호출해야 실제 소설에 반영됩니다 (2단계 커밋 구조).
- **DLQ/재시도 정책 부재**: Kafka 컨슈머 레벨의 재시도(`@RetryableTopic`)나 Dead Letter Topic 설정이 없습니다. Job을 찾지 못하면 예외가 그대로 던져져 메시지가 깔끔하게 처리되지 못하고, Gemini/S3 재시도(각 3회)는 컨슈머 메서드 내부의 로컬 루프일 뿐입니다.
- **포인트 환불 실패는 로그만**: FAILED 처리 시 이미 차감된 포인트를 되돌리는 로직이 있지만, 이 환불 자체가 실패하면 별도 보상 트랜잭션 없이 로그만 남기고 넘어갑니다 (이중 실패 시 정산 불일치 가능성).
- **파티션/컨슈머 동시성**: 토픽 파티션 3개, 컨슈머 concurrency 3으로 병렬 처리하며, 같은 `novelId`를 메시지 키로 사용해 같은 소설의 여러 요청은 같은 파티션에서 순서를 보장합니다.
