# 서재 (Library)

> 대상 코드: `domain/library/**`
> 관련 파일: `LibraryController`, `LibraryService`, `LibraryQueryRepository`

## 서비스 플로우 요약

1. **서재에 소설 추가** — 클라이언트가 소설 정보(제목·작가명·커버 URL)를 스냅샷으로 함께 보내면, `(userId, novelId)` 유니크 제약으로 중복만 막고 그대로 저장합니다. 소설 실존 여부는 검증하지 않습니다.
2. **내 서재 목록 조회** — 타입 필터(READING/BOOKMARK/COMPLETED/PURCHASED) + 페이징 + 정렬(TITLE/LATEST)로 조회하며, 회차 수는 N+1을 피하기 위해 배치 조회합니다.

---

## FLOW A — 서재에 소설 추가

```mermaid
flowchart TD
    L1["POST /api/libraries/me<br/>(novelId, title, author, cover, type)"]:::action --> L2["Bean Validation"]:::action
    L2 --> L3{"(userId, novelId)<br/>이미 서재에 존재?"}:::decision
    L3 -->|"Yes"| EXL1(["예외: ALREADY_IN_LIBRARY (409)<br/>타입 변경(update) 경로 없음"]):::exception
    L3 -->|"No"| L4["Library INSERT<br/>UNIQUE(user_id, novel_id)"]:::action
    L4 --> DB1[("library")]:::db

    classDef action fill:#101f3c,stroke:#101f3c,color:#ffffff,stroke-width:1px;
    classDef data fill:#d9b98a,stroke:#d9b98a,color:#1a1a1a,stroke-width:1px;
    classDef db fill:#ffffff,stroke:#333333,color:#1a1a1a,stroke-width:1.5px;
    classDef decision fill:#ffffff,stroke:#c0392b,color:#c0392b,stroke-width:2px;
    classDef exception fill:#ffffff,stroke:#c0392b,color:#c0392b,stroke-width:2px;
```

## FLOW B — 내 서재 목록 조회

```mermaid
flowchart TD
    M1["GET /api/libraries/me<br/>type / page / size / sort"]:::action --> M2{"libraryType 파라미터 존재?"}:::decision
    M2 -->|"Yes"| M3["WHERE libraryType = ?"]:::action
    M2 -->|"No"| M4["전체 타입 조회"]:::action
    M3 --> DB1[("library, Querydsl")]:::db
    M4 --> DB1
    DB1 --> M5{"sort = TITLE?"}:::decision
    M5 -->|"Yes"| M6["ORDER BY novelTitle ASC"]:::action
    M5 -->|"No (LATEST 등)"| M7["ORDER BY createdAt DESC"]:::action
    M6 --> M8[/"페이지 내 novelId 목록 추출"/]:::data
    M7 --> M8
    M8 --> DB2[("episodes<br/>countByNovelIds (배치 1회 조회)")]:::db
    M8 --> M9[/"국립도서관 내 책장 병합<br/>MyLibraryResponse 조립"/]:::data

    classDef action fill:#101f3c,stroke:#101f3c,color:#ffffff,stroke-width:1px;
    classDef data fill:#d9b98a,stroke:#d9b98a,color:#1a1a1a,stroke-width:1px;
    classDef db fill:#ffffff,stroke:#333333,color:#1a1a1a,stroke-width:1.5px;
    classDef decision fill:#ffffff,stroke:#c0392b,color:#c0392b,stroke-width:2px;
```

## 핵심 로직 노트

- **PURCHASED 타입은 자동 연동되지 않음**: `domain.md`상으로는 `PURCHASED`가 "구매한 작품"을 뜻하지만, 실제로는 결제(Payment)·구매(Purchases) 도메인 어디에서도 구매 완료 후 서재에 자동으로 `PURCHASED` 항목을 추가하는 코드가 없습니다. 4가지 타입 모두 클라이언트가 직접 지정해서 보내는 값일 뿐입니다 — 결제 완료 이벤트를 구독해 자동 반영하는 로직 추가가 필요할 수 있는 지점입니다.
- **타입 변경 불가**: `(userId, novelId)` 조합이 유니크 제약이라, 같은 소설을 다른 타입(예: BOOKMARK → READING)으로 바꾸려면 새로 추가가 아니라 별도의 update API가 필요한데, 현재 `Library.changeType()` 메서드는 정의만 있고 호출하는 곳이 없습니다.
- **소설 실존성 미검증**: `LibraryService`는 `NovelRepository`를 의존하지 않아 존재하지 않는 `novelId`로도 추가가 가능합니다.
- **Redis 미사용**: 서재 도메인 자체는 캐싱하지 않으며, Calendar/추천AI 도메인이 읽기 전용으로 `LibraryRepository`를 참조합니다.
