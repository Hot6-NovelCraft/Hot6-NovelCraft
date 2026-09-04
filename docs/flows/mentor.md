# 멘토 / 멘토링 (Mentor)

> 대상 코드: `domain/mentor/**`, `domain/mentoring/**`
> 관련 파일: `MentorService`, `MentorCareerLevelScheduler`, `MentorshipService`, `MentoringService`, `MentorshipReviewService`

## 서비스 플로우 요약

1. **멘토 등록 신청** — AUTHOR가 경력등급·주력장르·전문분야 등을 입력해 신청하면, 경력등급별 기준(발행 회차 수·좋아요 수)을 충족할 경우 **자동 승인**되고, 미충족이거나 최고 등급(PROFICIENT)이면 관리자 심사 대기(PENDING) 상태가 됩니다.
2. **경력등급 자동 승급** — 매일 자정 스케줄러가 APPROVED 멘토를 순회하며 활동 지표를 재계산해 등급을 자동으로 올려줍니다.
3. **멘토링 신청** — 멘티가 멘토를 지정해 신청하면 멘토의 "즉시수락" 설정에 따라 바로 ACCEPTED 되거나 PENDING 상태로 멘토의 응답을 기다립니다.
4. **진행 · 피드백 · 완료** — 멘토가 세션 피드백을 남기고, 완료 처리 시 멘토의 멘티 슬롯이 반환되며, 멘티는 완료된 건에 한해 1회 만족도 평가를 남길 수 있습니다.

---

## FLOW A — 멘토 등록 신청 & 승인

```mermaid
flowchart TD
    A1["멘토 등록 신청 (AUTHOR)"]:::action --> A2{"PENDING/APPROVED<br/>기존 이력 존재?"}:::decision
    A2 -->|"Yes"| EXA1(["예외: 중복 신청 (409)"]):::exception
    A2 -->|"No"| A3{"경력등급 = PROFICIENT?"}:::decision
    A3 -->|"Yes"| A4["상태 PENDING<br/>(관리자 심사 대기)"]:::action
    A3 -->|"No"| A5[/"발행 회차수 · 총 좋아요수 집계"/]:::data
    A5 --> DB1[("novels / episodes")]:::db
    A5 --> A6{"등급별 자동승인 기준 충족?<br/>INTRODUCTION:발행≥50<br/>ELEMENTARY:발행≥50&좋아요≥50<br/>INTERMEDIATE:발행≥100&좋아요≥100"}:::decision
    A6 -->|"Yes"| A7["상태 APPROVED (자동승인)"]:::action
    A6 -->|"No"| A4
    A4 --> DB2[("mentors")]:::db
    A7 --> DB2
    A7 --> R1[["Redis: 관리자 대시보드<br/>신규 멘토 카운터 증가"]]:::redis

    DB2 --> A8["관리자: PROFICIENT PENDING 목록 조회"]:::action
    A8 --> A9{"관리자 승인/거절"}:::decision
    A9 -->|"승인"| A10["APPROVED, approvedAt 기록"]:::action
    A9 -->|"거절"| A11["REJECTED, 거절사유 기록"]:::action
    A10 --> DB2
    A11 --> DB2

    S1["매일 00:00 스케줄러<br/>(cron)"]:::action --> S2["APPROVED & non-PROFICIENT<br/>멘토 100건씩 페이징 조회"]:::action
    S2 --> DB2
    S2 --> S3{"자동 승진 기준<br/>재충족?"}:::decision
    S3 -->|"Yes"| S4["경력등급 UPGRADE<br/>+ 이력(MentorCareerHistory) 기록"]:::action
    S3 -->|"No"| S5[/"변경 없음, 다음 청크"/]:::data
    S4 --> DB3[("mentors / mentor_career_history")]:::db
    S5 --> S2

    classDef action fill:#101f3c,stroke:#101f3c,color:#ffffff,stroke-width:1px;
    classDef data fill:#d9b98a,stroke:#d9b98a,color:#1a1a1a,stroke-width:1px;
    classDef db fill:#ffffff,stroke:#333333,color:#1a1a1a,stroke-width:1.5px;
    classDef redis fill:#dc382d,stroke:#dc382d,color:#ffffff,stroke-width:1px;
    classDef decision fill:#ffffff,stroke:#c0392b,color:#c0392b,stroke-width:2px;
    classDef exception fill:#ffffff,stroke:#c0392b,color:#c0392b,stroke-width:2px;
```

## FLOW B — 멘토링 신청 → 진행 → 완료

```mermaid
flowchart TD
    B1["멘토링 신청 (멘티)"]:::action --> B2{"신청자 role = AUTHOR?"}:::decision
    B2 -->|"No"| EXB1(["예외: NOT_AUTHOR"]):::exception
    B2 -->|"Yes"| B3{"자기 자신에게 신청?"}:::decision
    B3 -->|"Yes"| EXB2(["예외: SELF_APPLY"]):::exception
    B3 -->|"No"| B4{"PENDING/ACCEPTED<br/>이력 존재? (1인 1건)"}:::decision
    B4 -->|"Yes"| EXB3(["예외: ALREADY_EXISTS"]):::exception
    B4 -->|"No"| B5{"멘토 잔여 슬롯 > 0?"}:::decision
    B5 -->|"No"| EXB4(["예외: SLOT_FULL"]):::exception
    B5 -->|"Yes"| B6["Mentorship 생성<br/>status=PENDING"]:::action
    B6 --> DB1[("mentorships")]:::db
    B6 --> B7{"멘토 allowInstant<br/>(즉시수락) 설정?"}:::decision
    B7 -->|"Yes"| B8["슬롯 차감 + status=ACCEPTED"]:::action
    B7 -->|"No"| B9[/"알림 이벤트: 신청 도착"/]:::data
    B8 --> EVT1[/"NotificationEvent<br/>(AFTER_COMMIT에만 발행)"/]:::data
    B9 --> EVT1
    EVT1 --> KAFKA1[["Kafka: 알림 이벤트 발행"]]:::redis

    B10["멘토: 수락/거절 처리"]:::action --> B11{"status == PENDING?"}:::decision
    B11 -->|"No"| EXB5(["예외: ALREADY_PROCESSED"]):::exception
    B11 -->|"Yes"| B12{"수락 or 거절"}:::decision
    B12 -->|"수락"| B13["슬롯 차감 + ACCEPTED"]:::action
    B12 -->|"거절"| B14["REJECTED"]:::action
    B13 --> EVT1
    B14 --> EVT1

    B15["멘토링 진행 중"]:::action --> B16["세션 피드백 작성 (V2)"]:::action
    B16 --> DB2[("mentorships<br/>SELECT ... FOR UPDATE")]:::db
    B16 -.->|"PESSIMISTIC_WRITE Lock<br/>동시 세션번호 충돌 방지"| DB2

    B15 --> B17["완료 처리 (멘토 또는 멘티)"]:::action
    B17 --> B18["슬롯 복구 + status=COMPLETED"]:::action
    B18 --> DB1
    B18 --> EVT1
    B18 --> B19{"이미 리뷰 작성됨?"}:::decision
    B19 -->|"Yes"| EXB6(["예외: REVIEW_ALREADY_EXISTS"]):::exception
    B19 -->|"No"| B20["MentorshipReview 저장<br/>(별점 + 후기, 1건 한정)"]:::action
    B20 --> DB3[("mentorship_reviews")]:::db

    classDef action fill:#101f3c,stroke:#101f3c,color:#ffffff,stroke-width:1px;
    classDef data fill:#d9b98a,stroke:#d9b98a,color:#1a1a1a,stroke-width:1px;
    classDef db fill:#ffffff,stroke:#333333,color:#1a1a1a,stroke-width:1.5px;
    classDef redis fill:#dc382d,stroke:#dc382d,color:#ffffff,stroke-width:1px;
    classDef decision fill:#ffffff,stroke:#c0392b,color:#c0392b,stroke-width:2px;
    classDef exception fill:#ffffff,stroke:#c0392b,color:#c0392b,stroke-width:2px;
```

## 핵심 로직 노트

- **자동승인 스코어링**: 신청 시 `resolveInitialStatus()`가 경력등급별 임계치(발행 회차 수·좋아요 수)를 즉시 계산해 PENDING/APPROVED를 결정합니다. PROFICIENT 등급만 무조건 관리자 심사로 넘어갑니다.
- **관리자 승인 시 역할 변경 없음**: `Mentor.approve()`는 `Mentor` 엔티티 상태만 APPROVED로 바꿀 뿐, `User.role`을 MENTOR로 바꾸는 로직은 어디에도 없습니다 (코드상 `changeRole` 메서드는 정의만 되어 있고 호출되지 않음).
- **채팅방 자동 생성 없음**: `page-structure.md`에는 "ACCEPTED → 채팅 바로가기"라고 되어 있지만, 실제로는 멘토링 수락/승인 로직에서 `ChatRoom`을 생성하는 코드가 없습니다. 채팅방은 별도 도메인에서 독립적으로 다뤄집니다.
- **동시성 제어는 피드백 V2에만 존재**: 두 도메인 전체에서 유일한 락은 `MentorshipRepository.findByIdWithLock` (PESSIMISTIC_WRITE) 하나이며, 세션 번호 중복을 막기 위한 용도입니다. Redisson/분산락은 사용하지 않습니다.
- **낙관적 락**: `Mentor` 엔티티에는 `@Version` 필드가 있어 슬롯 증감(`decreaseSlot`/`increaseSlot`) 시 낙관적 락으로 동시성을 방지하지만, `Mentorship` 엔티티에는 없습니다.
