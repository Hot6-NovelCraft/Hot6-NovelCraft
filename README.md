# 📚 NovelCraft
## 누구나 작가가 되는 세상, 이야기가 빛나는 순간, 웹소설 플랫폼

> **"당신의 이야기가 세상을 만납니다"**
> 
> 단순한 소설 감상 서비스를 넘어, 신입 작가의 성장과 독자의 독서 습관 형성을 동시에 지원하는 웹소설 플랫폼

---

## 📋 목차

- [프로젝트 개요](#-프로젝트-개요)
- [팀원 소개](#-팀원-소개)
- [주요 기능](#-주요-기능)
- [기술 스택](#-기술-스택)
- [시스템 아키텍처](#-시스템-아키텍처)
- [시작하기](#-시작하기)
- [환경 변수 설정](#-환경-변수-설정)
- [사용 방법](#-사용-방법)
- [프로젝트 구조](#-프로젝트-구조)
- [배포](#-배포)
- [기여 방법](#-기여-방법)

---

## 🌟 프로젝트 개요

**NovelCraft**는 웹소설 작가와 독자를 위한 통합 창작 플랫폼입니다.

> 웹소설 및 웹툰 시장은 최근 몇 년간 급격히 성장, 국내 콘텐츠 산업의 핵심 분야로 자리 잡았습니다.
>
> 하지만 기존 플랫폼은 독자 중심에 치우쳐 있어 작가가 작품을 관리하고 독자와 소통하며 성장할 수 있는 작가 친화적 생태계가 부족하다는 것에서 출발했습니다.
>
> 그래서 저희는 작가와 독자, 그리고 멘토-멘티 구조까지 아우르는 창작 생태계 플랫폼을 기획하게 되었습니다.

### 핵심 가치

| 가치 | 설명 |
|---|---|
| ✍️ **창작 지원** | 소설 등록·연재·관리를 위한 작가 전용 에디터 환경 |
| 📖 **독서 경험** | 포인트 결제, 서재 관리, 독서 캘린더로 독자 경험 극대화 |
| 🤝 **성장 생태계** | 멘토-멘티 매칭, 1:1 채팅, 피드백 시스템으로 작가 성장 지원 |

---

## 👥 팀원 소개

| 이름      | 역할      | 담당 도메인                                                          |
|---------|---------|-----------------------------------------------------------------|
| **전민우** | 팀장      | Mentor, Calender, Library, Revenue, Event, Exchange, AI Cover   |
| **정은식** | 팀원      | Novel, Episode, Mentorship, Report, AI Review                   |
| **정인호** | 팀원      | Payment, Subscription, Chat, Notification, AI Support           |
| **서하나** | 팀원      | Security (JWT), User, Ranking, Search, Admin, AI Recommendation |

---

## ✨ 주요 기능

### 1. 📝 소설 & 회차 관리 (작가)

- **소설 등록/수정/삭제** — 제목, 장르, 태그, 표지 이미지 관리
- **회차 CRUD** — 초안(DRAFT) 저장 후 발행(PUBLISH) 2단계 워크플로
- **설정집 (Wiki)** — 캐릭터/세계관/플롯/용어집 카테고리별 관리 (Redis 캐싱)
- **작가 전용 에디터** — 본인 소설 전체 상태 (DRAFT 포함) 조회
- **소설 상태 관리** — PENDING (준비) → ONGOING (연재) → COMPLETED (완결) / HIATUS (보류)

### 2. 📖 독서 & 구매 (독자)

- **소설 목록/상세 조회** — V1(JPA), V2(QueryDSL + Redis 캐싱) 이중 구조
- **회차 열람** — V1(JPA 단건), V2(Hot Key 감지 + 벌크 캐싱) 성능 최적화
- **포인트 결제** — 회차 단건 구매 / 소설 전체 구매(10% 할인)
- **서재 관리** — 읽는 중 / 완독 / 찜 / 구매 타입별 분류
- **독서 캘린더** — 일별 독서 기록, 월간 통계(총 페이지, 완독 수, 일평균), 국립도서관 API를 통해 외부 책 검색 기능

### 3. 🔍 검색 & 발견

- **소설 제목 검색** — QueryDSL 기반, 로그인 시 검색어 히스토리 자동 저장
- **태그 검색** — 복수 태그 선택, 태그별 그룹핑 결과 반환
- **작가 통합 검색** — 닉네임 유사 작가 목록 + 대표작 3개 노출
- **인기 검색어/태그 TOP5** — Redis ZSet 기반 실시간 집계
- **내 최근 검색어** — 사용자별 최근 10개, 당일 자정 만료

### 4. 🏆 소설 랭킹

- **실시간 랭킹 TOP5** — 매 정각 초기화
- **주간 랭킹 TOP5** — 매주 일요일 00:01 초기화
- **어뷰징 방지** — 사용자 + 소설 단위 1시간 중복 조회 차단 (Redis)

### 5. 💳 결제 & 포인트

- **PortOne V2 연동** — 카드, 간편결제(카카오/토스/네이버) 지원
- **결제 준비(Prepare)** — 결제창 열기 전 PENDING 레코드 선생성
- **웹훅 보정** — `/confirm` 누락 시 웹훅으로 자동 포인트 충전
- **멱등성 보장** — `transactionId` 기반 중복 웹훅 처리 방지
- **전액 환불** — 포인트 선차감 → PortOne 환불 → 실패 시 보상 트랜잭션

### 6. 🎓 멘토링 시스템

- **멘토 등록** — 경력 수준(INTRODUCTION ~ PROFICIENT)별 자동/수동 승인
- **멘토링 신청/수락/거절** — 멘티 관리, 원고 파일 다운로드
- **피드백 작성** — 진행 중인 멘토링에만 피드백 가능, 세션 수 자동 집계
- **등급 자동 승급** — 매일 자정 발행 회차 수 + 좋아요 수 기준 배치 처리

### 7. 💬 실시간 채팅

- **1:1 채팅방** — 멘토십 기반 자동 생성 (동시 요청 방어)
- **WebSocket(STOMP) + Redis Pub-Sub** — 멀티 인스턴스 환경 메시지 브로드캐스트
- **파일 첨부** — S3 업로드 후 URL 검증, 10MB 이하 이미지/문서 지원
- **읽음 처리** — 채팅방 입장 시 상대방 메시지 일괄 읽음 처리

### 8. 👤 회원 & 인증

- **SMS 인증** — coolSMS 외부 API 인증/인가 절차
- **일반 회원가입** — 공통 → 독자/작가 추가 정보 2단계 가입
- **소셜 로그인** — Google, Kakao, Naver OAuth2 (확장 가능), 소셜 공통 → 독자/작가 추가 정보 2단계 가입
- **JWT Silent Refresh** — AccessToken 만료 시 RefreshToken으로 자동 재발급
- **회원 탈퇴 & 복구** — 30일 유예 기간, 만료 시 자동 비식별화

### 9. 🤖 AI 서비스

- **작가 전용 표지 생성** — 해당 작가가 소유한 소설의 정보를 자동으로 조회, 적절한 소설 표지 생성, Kafka 비동기 처리
- **작가 전용 리뷰 댓글** — 사용자 행동 기반 맞춤 웹소설 추천과 비로그인 맞춤 인기 태그 랭킹 웹소설 추천
- **사용자 맞춤 추천** — 작가가 회차 발행 전 AI 독자 반응 미리보기, Kafka 비동기 처리
- **사용자 문의 채팅** — 미리 적재된 FAQ 문서를 기반으로 스트리밍 답변하는 고객센터 챗봇


>💡 작가 전용 AI 기능은 포인트 소비 기반으로 운영되며, 사용 내역은 포인트 히스토리에서 확인할 수 있습니다.

### 10. 🛡️ 관리자(Admin) 기능

- **관리자 계정 생성** — 별도 가입 API(`/api/auth/signup/admin`)로 ADMIN 역할 계정 생성, SMS 인증 필수
- **멘토 심사** — PROFICIENT(전문) 등급 멘토는 관리자가 직접 수동 승인/거절 처리
- **멘토 등급 관리** — 자동 승급 대상에서 제외된 PROFICIENT 등급은 관리자 판단으로 승급
- **플랫폼 운영 관리** — 사용자 신고 처리, 부적절 소설 관리, 서비스 정책 공지 등 운영 전반 담당 및 대시보드 통계


>💡 관리자 계정은 `UserRole.ADMIN` 및 `UserRole.SUPER_ADMIN`으로 식별되며, 일반 회원가입 경로와 분리된 전용 API를 통해서만 생성할 수 있습니다.

---

## 🛠 기술 스택

| 분류                             | 기술                                                                                 |
|--------------------------------|------------------------------------------------------------------------------------|
| **Language**                   | Java 17, Gradle                                                                    |
| **Backend**                    | Spring Boot 3.x, Spring Data JPA, QueryDSL, Spring Framework                       |
| **Security**                   | Spring Security + JWT, jjwt(0.12.6), AES-128 Encryption, Token Blacklist           |
| **Database**                   | MySQL 8.x, Redis (Chech)                                                           |
| **Storage & Distributed Lock** | Redisson, Redis Sentinel                                                           |
| **Messging & Streaming**       | Apache Kafka (KRaft modle), Spring Kafka, WebSocket + STOMP                        |
| **Open API**                   | Google/Kakao/Naver Spring OAuth2 Client, CoolSMS, PortOne V2 SDK, 국립중앙도서관 Open API |
| **Cloud Infra**                | AWS (EC2, S3, RDS), Kafka, Docker                                                  |
| **CICD**                       | Jenkins                                                                            |
| **Open AI**                    | Gpt-4o-mini, Gpt-4.1-nano, Gemini, Spring AI                                       |
| **Monitoring**                 | k6, Grafana, Prometheus, Micrometer Prometheus, JMX Exporter, Kafka UI, InfluxDB   |
| **Real Time**                  | Spring WebSoket, SSE                                                               |
| **Test**                       | Postman, Mockito, JUnit5, TestContainer                                            |
| **Tools & Others**             | IntelliJ IDEA, CodeRabbit, GitHub, ERDCloud, Figma, Miro, Slack, Notion, Zep       |

### DevOps

```
Docker · Jenkins · AWS EC2 
```

---

## 🏗 시스템 아키텍처

```
┌─────────────────────────────────────────────────────────┐
│                        Client                           │
│              (Web Browser / Mobile App)                 │
└───────────────────────┬─────────────────────────────────┘
                        │ HTTPS / WebSocket(STOMP)
                        ▼
┌─────────────────────────────────────────────────────────┐
│                  Spring Boot API Server                 │
│  ┌──────────┐  ┌──────────┐  ┌──────────┐ ┌──────────┐  │
│  │ REST API │  │ WebSocket│  │ Scheduler│ │ Admin API│  │
│  └──────────┘  └──────────┘  └──────────┘ └──────────┘  │
│  ┌───────────────────────────────────────────────────┐  │
│  │           Service Layer (Business Logic)          │  │
│  │  ┌─────────┐ ┌────────┐ ┌──────────┐ ┌─────────┐  │  │
│  │  │ Payment │ │ AI Rec │ │ Mentoring│ │Dashboard│  │  │
│  │  └─────────┘ └─┬──────┘ └──────────┘ └─────────┘  │  │
│  └────────────────│──────────────────────────────────┘  │
└───────┬───────────│────┬───────────────────┬────────────┘
        │           │    │                   │
        │           │    ▼                   ▼
        │           │ ┌───────────────┐ ┌───────────────┐
        │           │ │ Redis Sentinel│ │    AWS S3     │
        │           │ │(Cache/Pub-Sub/│ │ (File Upload) │
        │           │ │ Lua Lock/TTL) │ └───────────────┘
        ▼           │ └───────────────┘
┌────────────┐      │    ┌───────────────────────────┐
│  MySQL 8   │      └─>  │  External AI API Services │
│ (Primary)  │           │ (Gemini / OpenAI Models)  │
└────────────┘           └───────────────────────────┘
```

---

## 🚀 시작하기

### 사전 요구사항

- Java 17+
- MySQL 8.0+
- Redis 7.x+
- Gradle 8.x

### 1. 저장소 클론

```bash
git clone https://github.com/Hot6-NovelCraft/Hot6-NovelCraft.git
cd hot6-novelcraft
```

### 2. MySQL 데이터베이스 생성

```sql
CREATE DATABASE novelcraft
  CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;
```

### 3. 환경 변수 설정

아래 [환경 변수 설정](#-환경-변수-설정) 섹션을 참고해 `application.yml` 또는 `.env`를 구성하세요.

### 4. 빌드 및 실행

```bash
# 빌드
./gradlew clean build -x test

# 실행 (local 프로파일)
./gradlew bootRun --args='--spring.profiles.active=local'

# 또는 JAR 직접 실행
java -jar build/libs/hot6novelcraft-0.0.1-SNAPSHOT.jar \
  --spring.profiles.active=local
```

### 5. 서버 확인

```
http://localhost:8080
```

> 💡 `local` 또는 `dev` 프로파일로 실행하면 `DataInitializer`가 자동으로 테스트 더미 데이터 (작가 3명, 소설 6편, Redis 랭킹)를 삽입합니다.

---

## ⚙️ 환경 변수 설정

`src/main/resources/application.yml`에 아래 항목을 설정하세요. 민감 정보는 반드시 환경 변수나 Secret Manager로 관리하세요.

```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/novelcraft?serverTimezone=Asia/Seoul&characterEncoding=UTF-8
    username: ${DB_USERNAME}
    password: ${DB_PASSWORD}

  data:
    redis:
      host: ${REDIS_HOST:localhost}
      port: ${REDIS_PORT:6379}

jwt:
  secret:
    key: ${JWT_SECRET_KEY}            # Base64 인코딩된 256bit 이상 키

# AWS S3 (이미지 및 표지 업로드)
spring:
  cloud:
    aws:
      credentials:
        access-key: ${AWS_ACCESS_KEY}
        secret-key: ${AWS_SECRET_KEY}
      s3:
        bucket: ${S3_BUCKET_NAME}

# 결제 및 SMS
portone:
  api-secret: ${PORTONE_API_SECRET}
  webhook-secret: ${PORTONE_WEBHOOK_SECRET}

coolsms:
  api-key: ${COOLSMS_API_KEY}
  secret-key: ${COOLSMS_SECRET_KEY}
  send-number: ${COOLSMS_SEND_NUMBER}

# AI 연동 설정 (추천 시스템 및 커버 생성용)
ai:
  gemini:
    api-key: ${GEMINI_API_KEY}
    model: gemini-pro
  openai:
    api-key: ${OPENAI_API_KEY}

# 국립중앙도서관 API
nationallibrary:
  api:
    key: ${NL_API_KEY}
```

### 필수 환경 변수 요약

| 변수명                                 | 설명 | 필수 여부 |
|-------------------------------------|---|--|
| `DB_USERNAME` / `DB_PASSWORD`       | MySQL 접속 정보 | ✅ 필수 |
| `JWT_SECRET_KEY`                    | JWT 서명 키 (Base64) | ✅ 필수 |
| `REDIS_HOST` / `REDIS_PORT`         | Redis 접속 정보 | ✅ 필수 |
| `GOOGLE_CLIENT_ID/SECRET`           | Google OAuth2 | 소셜 로그인 사용 시 |
| `KAKAO_CLIENT_ID/SECRET`            | Kakao OAuth2 | 소셜 로그인 사용 시 |
| `NAVER_CLIENT_ID/SECRET`            | Kakao OAuth2 | 소셜 로그인 사용 시 |
| `AWS_ACCESS_KEY` / `S3_BUCKET_NAME` | AWS S3 | 파일 업로드 사용 시 |
| `PORTONE_API_SECRET`                | PortOne V2 | 결제 기능 사용 시 |
| `COOLSMS_API_KEY`                   | CoolSMS | SMS 인증 사용 시 |
| `NL_API_KEY`                        | 국립중앙도서관 | 도서 검색 사용 시 |
| `GEMINI_API_KEY` / `OPENAI_API_KEY `| AI 기능 호출용 API 키 | ✅ AI 기능 시 |

---

## 📡 사용 방법

### API 기본 정보

```
Base URL : http://localhost:8080/api
인증 방식 : Bearer Token (Authorization 헤더)
응답 형식 : application/json
```

### 공통 응답 형식

```json
{
  "success": true,
  "status": "200",
  "message": "소설 목록 조회 성공(V2)",
  "data": { ... }
}
```

### 주요 API 예시

#### 🔐 로그인

```bash
POST /api/auth/login
Content-Type: application/json

{
  "email": "author@test.com",
  "password": "test1234!"
}
```

**응답**

```json
{
  "success": true,
  "data": {
    "email": "author@test.com",
    "nickname": "백산",
    "role": "AUTHOR",
    "accessToken": "Bearer eyJ...",
    "refreshToken": "Bearer eyJ..."
  }
}
```

#### 📚 소설 목록 조회 (V2 — 필터링 + 캐싱)

```bash
GET /api/v2/novels?genre=FANTASY&status=ONGOING&page=0&size=10
```

#### ✍️ 소설 등록 (작가 전용)

```bash
POST /api/novels
Authorization: Bearer {accessToken}
Content-Type: application/json

{
  "title": "이세계 먼치킨 마법사",
  "description": "평범한 회사원이 이세계에서 최강이 되는 이야기",
  "genre": "FANTASY",
  "tags": ["ISEKAI", "MUNCHKIN", "REGRESSION"]
}
```

#### 📺 회차 발행

```bash
# 1. 회차 생성 (DRAFT 상태)
POST /api/novels/{novelId}/episodes
Authorization: Bearer {accessToken}

{
  "episodeNumber": 1,
  "title": "1화 — 시작",
  "content": "회차 본문 내용..."
}

# 2. 발행
POST /api/episodes/{episodeId}/publish
Authorization: Bearer {accessToken}
```

#### 💳 포인트 결제 흐름

```bash
# Step 1. 결제 준비 (PENDING 레코드 생성)
POST /api/payments/prepare
{ "amount": 10000 }
# → paymentKey 반환

# Step 2. 프론트에서 PortOne SDK로 결제창 오픈

# Step 3. 결제 확인 & 포인트 충전
POST /api/payments/confirm
{ "paymentId": "{paymentKey}", "amount": 10000 }
```

#### 🔍 소설 검색

```bash
# 제목 검색
GET /api/search/v2/novels?keyword=먼치킨

# 태그 검색
GET /api/search/v2/tags?tags=FANTASY&tags=MUNCHKIN

# 인기 검색어 TOP5
GET /api/search/keywords/popular
```

#### 🏆 소설 랭킹

```bash
# 실시간 랭킹
GET /api/novels/ranking?type=realtime

# 주간 랭킹
GET /api/novels/ranking?type=weekly
```

---

## 📁 프로젝트 구조

```
src/main/java/com/example/hot6novelcraft/
│
├── common/                          # 공통 모듈
│   ├── config/                      # 설정 클래스
│   │   ├── SecurityConfig.java      # Spring Security 설정
│   │   ├── RedisConfig.java         # Redis 직렬화 설정
│   │   ├── RedissonConfig.java      # 분산 락 설정
│   │   ├── QuerydslConfig.java      # QueryDSL JPAQueryFactory
│   │   ├── WebSocketConfig.java     # STOMP WebSocket 설정
│   │   ├── S3Config.java            # AWS S3 클라이언트
│   │   ├── PortOneConfig.java       # 결제 SDK 설정
│   │   └── EpisodePurchaseConfig.java # 할인율 설정
│   ├── dto/
│   │   ├── BaseResponse.java        # 공통 응답 래퍼
│   │   └── PageResponse.java        # 페이징 응답 래퍼
│   ├── entity/
│   │   └── BaseEntity.java          # createdAt 공통 엔티티
│   ├── exception/
│   │   ├── GlobalExceptionHandler.java
│   │   ├── ServiceErrorException.java
│   │   └── domain/                  # 도메인별 에러 코드 Enum
│   └── security/
│       ├── JwtUtil.java             # JWT 생성/검증/파싱
│       ├── JwtFilter.java           # JWT 인증 필터
│       ├── RedisUtil.java           # Redis 블랙리스트 + 분산 락
│       └── StompChannelInterceptor.java # WebSocket JWT 인증
│
└── domain/                          # 도메인 모듈
    ├── admin/                       # 관리자
    │   ├── entity/                  # AdminStatistics, ReportManagement
    │   ├── controller/              # AdminDashboardController, AdminMentorController, ...
    │   ├── service/                 # AdminCacheService, AdminDashboardService, ...
    │   └── repository/
    │
    ├── user/                        # 회원 (가입, 로그인, 프로필)
    │   ├── entity/                  # User, AuthorProfile, ReaderProfile, AuthorFollow
    │   ├── controller/              # SignupController, AuthController, AuthorFollowController
    │   ├── service/                 # AuthService, SignupService, SmsService, ...
    │   └── repository/
    │
    ├── novel/                       # 소설
    │   ├── entity/                  # Novel, NovelBookmark, NovelWiki
    │   ├── controller/              # NovelController, NovelWikiController, NovelBookmarkController
    │   ├── service/                 # NovelService, NovelRankingService, NovelWikiService, ...
    │   ├── repository/              # CustomNovelRepositoryImpl (QueryDSL)
    │   └── scheduler/               # NovelWeeklyRankingScheduler
    │
    ├── episode/                     # 회차
    │   ├── entity/                  # Episode, EpisodeLike, EpisodeComment
    │   ├── controller/              # EpisodeController, EpisodeLikeController, ...
    │   ├── service/                 # EpisodeService, EpisodeCacheService, ...
    │   └── repository/              # CustomEpisodeRepositoryImpl (QueryDSL)
    │
    ├── payment/                     # 결제
    │   ├── entity/                  # Payment
    │   ├── controller/              # PaymentController, WebhookController
    │   └── service/                 # PaymentService, PaymentTransactionService, WebhookService, ...
    │
    ├── point/                       # 포인트
    │   ├── entity/                  # Point, PointHistory
    │   └── service/                 # PointService, EpisodePurchaseFacade, ...
    │
    ├── mentor/                      # 멘토
    │   ├── entity/                  # Mentor, MentorFeedback, MentorCareerHistory
    │   ├── controller/              # MentorController
    │   ├── service/                 # MentorService
    │   └── scheduler/               # MentorCareerLevelScheduler
    │
    ├── mentoring/                   # 멘토링 (멘토-멘티 매칭)
    │   ├── entity/                  # Mentorship, MentorshipReview
    │   ├── controller/              # MentoringController
    │   └── service/                 # MentoringService
    │
    ├── chatroom/                    # 채팅방
    │   ├── entity/                  # ChatRoom
    │   ├── controller/              # ChatRoomController, ChatController (WebSocket)
    │   ├── service/                 # ChatService
    │   └── chatredispubsub/         # ChatRedisPublisher, ChatRedisSubscriber
    │
    ├── search/                      # 검색
    │   ├── controller/              # SearchController
    │   ├── service/                 # SearchService (Redis 검색 히스토리)
    │   └── repository/              # CustomSearchRepositoryImpl (QueryDSL)
    │
    ├── library/                     # 서재
    │   ├── entity/                  # Library
    │   ├── controller/              # LibraryController
    │   └── service/                 # LibraryService
    │
    ├── calendar/                    # 독서 캘린더
    │   ├── entity/                  # ReadingRecord
    │   ├── controller/              # CalendarController
    │   └── service/                 # CalendarService
    │
    └── nationallibrary/             # 국립도서관 연동
        ├── infrastructure/          # NationalLibraryApiClient
        └── service/                 # NationalLibraryService
```

---

## 🚢 배포

### Docker를 이용한 로컬 환경 구성

```yaml
# docker-compose.yml
version: '3.8'
services:
  mysql:
    image: mysql:8.0
    environment:
      MYSQL_DATABASE: novelcraft
      MYSQL_ROOT_PASSWORD: ${DB_PASSWORD}
    ports:
      - "3306:3306"
    volumes:
      - mysql_data:/var/lib/mysql

  redis:
    image: redis:7-alpine
    ports:
      - "6379:6379"

  app:
    build: .
    ports:
      - "8080:8080"
    environment:
      SPRING_PROFILES_ACTIVE: dev
      DB_USERNAME: root
      DB_PASSWORD: ${DB_PASSWORD}
      REDIS_HOST: redis
    depends_on:
      - mysql
      - redis

volumes:
  mysql_data:
```

```bash
docker-compose up -d
```

### 프로덕션 배포 (AWS EC2)

```bash
# 1. JAR 빌드
./gradlew clean build -x test

# 2. EC2로 전송
scp -i key.pem build/libs/*.jar ec2-user@{EC2_IP}:/home/ec2-user/

# 3. 서버에서 실행
ssh -i key.pem ec2-user@{EC2_IP}
java -jar -Dspring.profiles.active=prod novelcraft-*.jar &
```

> 🔒 프로덕션 환경에서는 민감 정보를 **AWS Secrets Manager** 또는 **환경 변수**로 관리하고, `coolsms.test-mode=false`로 설정하세요.

---

## 🤝 기여 방법

### 브랜치 전략

```
main          ← 배포 브랜치 (직접 커밋 금지)
  └── develop ← 통합 개발 브랜치
        └── feature/{기능명}   ← 기능 개발
        └── fix/{버그명}        ← 버그 수정
        └── refactor/{대상}    ← 리팩토링
```

### 커밋 컨벤션

```
feat     : 새로운 기능 추가
fix      : 버그 수정
refactor : 코드 리팩토링 (기능 변경 없음)
docs     : 문서 수정
test     : 테스트 코드
chore    : 빌드/설정 변경
perf     : 성능 개선
```

**예시**

```
feat: 소설 전체 구매 10% 할인 기능 추가
fix: 랭킹 스케줄러 주간 초기화 cron 표현식 오류 수정
refactor: EpisodeService 벌크 캐시 조회 메서드 분리
```

### PR(Pull Request) 규칙

1. `develop` 브랜치로 PR 생성
2. PR 템플릿 양식 준수 (변경 사항, 테스트 방법, 스크린샷)
3. 최소 **2명** 이상의 코드 리뷰 승인 후 머지
4. 머지 후 해당 feature 브랜치 삭제

### 기여 절차

```bash
# 1. 저장소 포크 후 클론
git clone https://github.com/your-id/hot6-novelcraft.git

# 2. feature 브랜치 생성
git checkout -b feature/my-new-feature

# 3. 코드 작성 후 커밋
git add .
git commit -m "feat: 새로운 기능 설명"

# 4. 원격 저장소에 푸시
git push origin feature/my-new-feature

# 5. GitHub에서 PR 생성
```

---

## 📄 라이선스

This project is licensed under the MIT License. See the [LICENSE](./LICENSE) file for details.

---

<p align="center">
  Made with 🔥 by <strong>Team HOT6</strong>
</p>