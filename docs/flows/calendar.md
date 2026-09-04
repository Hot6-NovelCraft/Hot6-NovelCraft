# 독서 캘린더 (Calendar)

> 대상 코드: `domain/calendar/**`
> 관련 파일: `CalendarController`, `CalendarService`, `ReadingRecordRepository`

## 서비스 플로우 요약

1. **독서 기록 추가** — READER/AUTHOR만 접근 가능. 플랫폼 소설(`PLATFORM`)은 서재(Library)에 있는지만 확인하고, 외부 도서(`EXTERNAL`)는 별도 검증 없이 자유 입력을 그대로 저장합니다.
2. **캘린더 뷰 조회** — 날짜 범위(최대 1년)로 기록을 조회한 뒤, SQL이 아닌 **Java 스트림에서 일자별로 그룹핑**하여 응답합니다.
3. **월별 통계** — 해당 월의 기록을 가져와 총 읽은 페이지·완독 수·일평균·최다 열람일을 역시 Java 스트림으로 집계합니다.

---

## FLOW A — 독서 기록 추가

```mermaid
flowchart TD
    C1["POST /api/calendars/me/records"]:::action --> C2{"권한 READER/AUTHOR?<br/>(SecurityConfig URL 패턴,<br/>@PreAuthorize 아님)"}:::decision
    C2 -->|"No"| EXC1(["403 Forbidden"]):::exception
    C2 -->|"Yes"| C3["Bean Validation<br/>(readDate ≤ 오늘 등)"]:::action
    C3 --> C4{"source = PLATFORM?"}:::decision
    C4 -->|"Yes"| C5["서재 보유 여부 확인<br/>findByUserIdAndNovelId"]:::action
    C5 --> DB1[("library")]:::db
    C5 --> C6{"서재에 없음?"}:::decision
    C6 -->|"Yes"| EXC2(["예외: BOOK_NOT_IN_LIBRARY (404)"]):::exception
    C6 -->|"No"| C7["ReadingRecord INSERT"]:::action
    C4 -->|"EXTERNAL"| C7
    C7 --> DB2[("reading_records")]:::db

    classDef action fill:#101f3c,stroke:#101f3c,color:#ffffff,stroke-width:1px;
    classDef data fill:#d9b98a,stroke:#d9b98a,color:#1a1a1a,stroke-width:1px;
    classDef db fill:#ffffff,stroke:#333333,color:#1a1a1a,stroke-width:1.5px;
    classDef redis fill:#dc382d,stroke:#dc382d,color:#ffffff,stroke-width:1px;
    classDef decision fill:#ffffff,stroke:#c0392b,color:#c0392b,stroke-width:2px;
    classDef exception fill:#ffffff,stroke:#c0392b,color:#c0392b,stroke-width:2px;
```

## FLOW B — 캘린더 뷰 & 월별 통계

```mermaid
flowchart TD
    D1["GET /api/calendars/me<br/>(날짜범위 조회)"]:::action --> D2{"범위 > 1년?"}:::decision
    D2 -->|"Yes"| EXD1(["예외: DATE_RANGE_TOO_LARGE (400)"]):::exception
    D2 -->|"No"| D3["JPQL SELECT<br/>readDate BETWEEN start~end"]:::action
    D3 --> DB1[("reading_records")]:::db
    D3 --> D4[/"Java 스트림 groupingBy(date)<br/>일자별 novelCount/episodeCount 집계<br/>(기록 0건인 날짜도 채움)"/]:::data

    D5["GET /api/calendars/me/statistics<br/>(월별 통계)"]:::action --> D6{"요청 월 > 현재 월?"}:::decision
    D6 -->|"Yes"| EXD2(["예외: INVALID_STAT_DATE (400)"]):::exception
    D6 -->|"No"| D7["JPQL SELECT<br/>YEAR(readDate)/MONTH(readDate) 필터"]:::action
    D7 --> DB1
    D7 --> D8[/"Java 스트림 집계<br/>총페이지합 · 완독수 · 일평균 · 최다열람일"/]:::data

    classDef action fill:#101f3c,stroke:#101f3c,color:#ffffff,stroke-width:1px;
    classDef data fill:#d9b98a,stroke:#d9b98a,color:#1a1a1a,stroke-width:1px;
    classDef db fill:#ffffff,stroke:#333333,color:#1a1a1a,stroke-width:1.5px;
    classDef decision fill:#ffffff,stroke:#c0392b,color:#c0392b,stroke-width:2px;
    classDef exception fill:#ffffff,stroke:#c0392b,color:#c0392b,stroke-width:2px;
```

## 핵심 로직 노트

- **Redis/Querydsl 미사용**: 캘린더 도메인 전체에서 `RedisTemplate`/`@Cacheable`/Querydsl이 전혀 쓰이지 않습니다. 모든 조회는 매 요청마다 MySQL을 직접 읽고 Java에서 집계합니다 — 트래픽이 커지면 가장 먼저 캐싱을 검토해야 할 지점입니다.
- **중복/upsert 검증 없음**: 같은 유저·같은 날짜·같은 소설로 여러 번 기록을 추가해도 막는 로직(유니크 제약, upsert)이 없어 매번 INSERT됩니다.
- **단방향 결합**: Novel/Episode 리포지토리는 전혀 참조하지 않고, `PLATFORM` 소스 검증에 한해 Library 도메인만 읽기 전용으로 참조합니다.
