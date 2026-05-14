# NovelCraft 도메인별 기능 정리

> 작성 기준: 실제 백엔드 Controller / Entity / Enum 분석 결과

---

## 목차

1. [인증 / 회원 (User & Auth)](#1-인증--회원)
2. [소설 (Novel)](#2-소설)
3. [회차 (Episode)](#3-회차)
4. [멘토십 (Mentorship)](#4-멘토십)
5. [결제 (Payment)](#5-결제)
6. [구독 (Subscription)](#6-구독)
7. [포인트 (Point)](#7-포인트)
8. [정산 / 출금 (Exchange)](#8-정산--출금)
9. [서재 (Library)](#9-서재)
10. [검색 (Search)](#10-검색)
11. [알림 (Notification)](#11-알림)
12. [이벤트 (Event)](#12-이벤트)
13. [신고 (Report)](#13-신고)
14. [채팅 (Chat)](#14-채팅)
15. [독서 캘린더 (Calendar)](#15-독서-캘린더)
16. [AI 기능](#16-ai-기능)
17. [국립도서관 연동 (NationalLibrary)](#17-국립도서관-연동)
18. [파일 업로드 (File)](#18-파일-업로드)
19. [관리자 (Admin)](#19-관리자)

---

## 1. 인증 / 회원

### 역할(Role) 체계
| Role | 설명 |
|------|------|
| `READER` | 독자 - 소설 구독·열람, 독서 캘린더 |
| `AUTHOR` | 작가 - 소설·회차 작성, 정산 |
| `MENTOR` | 멘토 (승인된 작가) |
| `ADMIN` | 관리자 |
| `SUPER_ADMIN` | 최고 관리자 |

### CareerLevel Enum (작가 경력 등급)
| 값 | 한국어 |
|----|--------|
| `INTRODUCTION` | 입문 |
| `ELEMENTARY` | 초보 |
| `INTERMEDIATE` | 중급 |
| `PROFICIENT` | 숙련 |

### 주요 기능
- 이메일 / 소셜(OAuth2: Google) 로그인
- 회원가입: 공통 정보 → 역할(READER / AUTHOR) 선택 후 프로필 등록
- 이메일 중복 확인, 닉네임 중복 확인, 휴대폰 인증
- 계정 탈퇴 (soft delete) + 30일 이내 복구
- 작가 프로필 수정 (장르, 소개, 멘토링 허용 여부 등)
- JWT 액세스 토큰 + 리프레시 토큰 (헤더 `Authorization: Bearer {token}`)
- 성인 인증 (휴대폰 번호 재인증)

---

## 2. 소설

### NovelStatus Enum
| 값 | 한국어 |
|----|--------|
| `PENDING` | 보류 |
| `ONGOING` | 연재중 |
| `COMPLETED` | 완결 |
| `HIATUS` | 중단 |
| `PUBLISHED` | 전체 이용가 |

### MainGenre Enum
| 값 | 한국어 |
|----|--------|
| `FANTASY` | 판타지 |
| `ROMANCE_FANTASY` | 로판 |
| `MODERN` | 현대 |
| `CHIVALROUS` | 무협 |
| `BL` | BL |
| `SF` | SF |
| `HORROR` | 공포 |
| `CLASSIC` | 고전 |
| `DAILY_LIFE` | 일상물 |

### MainTag Enum
`ISEKAI(이세계)`, `REGRESSION(회귀)`, `HEALING(힐링)`, `ACTION(액션)`, `ROMANCE(로맨스)`, `COMEDY(개그)`, `MYSTERY(미스터리)`, `MARTIAL_ARTS(무공)`, `VILLAIN(빌런)`, `MUNCHKIN(먼치킨)`, `ACADEMY(아카데미)`, `CONTRACT(계약)`, `POSSESSION(빙의)`, `REINCARNATION(환생)`, `HAREM(하렘)`, `REVENGE(복수)`, `GROWTH(성장)`, `DUNGEON(던전)`, `ADULT(성인)`

### WikiCategory Enum
| 값 | 한국어 |
|----|--------|
| `CHARACTER` | 캐릭터 |
| `WORLDBUILDING` | 세계관 |
| `PLOT` | 플롯 메모 |
| `GLOSSARY` | 용어집 |

### 주요 기능
- 소설 CRUD (작가만 생성·수정·삭제)
- 소설 목록 조회: 장르·상태 필터, 페이징
- 신작 목록 (비로그인 가능)
- 실시간·주간 랭킹 조회 (비로그인 가능)
- 소설 북마크 토글
- 소설 위키 (캐릭터·세계관·플롯 메모 등) - CRUD
- AI 커버 이미지 생성 (비동기 Job 방식)

---

## 3. 회차

### EpisodeStatus Enum
| 값 | 한국어 |
|----|--------|
| `DRAFT` | 초안 |
| `PUBLISHED` | 발행 |

### 주요 기능
- 회차 CRUD (작가만)
- 회차 발행 (DRAFT → PUBLISHED)
- 무료 / 유료 회차 구분 (isFree 필드)
- 회차 구매 (단권 / 소설 전권 일괄 구매)
- 회차 댓글 작성·삭제·목록
- 회차 좋아요 토글
- 작가용 회차 통계 (조회수, 좋아요, 구매수)
- V1(기본) / V2(N+1 개선, soft-delete 적용) 분리

---

## 4. 멘토십

### MentorStatus Enum (멘토 등록 상태)
| 값 | 한국어 |
|----|--------|
| `PENDING` | 심사 중 |
| `APPROVED` | 승인됨 |
| `REJECTED` | 거절됨 |

### MentorshipStatus Enum (멘토링 세션 상태)
| 값 | 한국어 |
|----|--------|
| `PENDING` | 신청 대기 |
| `ACCEPTED` | 수락됨 / 진행 중 |
| `REJECTED` | 거절됨 |
| `COMPLETED` | 완료 |

### 주요 기능

**멘티 입장:**
- 멘토 목록 조회 (장르·경력등급 필터, 비로그인 가능)
- 멘토 상세 조회
- 멘토링 신청 (소설 ID·신청 동기·원고 URL 선택 첨부)
- 내 멘토링 이력 조회 (상태별 필터)
- 멘토 만족도 평가 (별점·후기)

**멘토 입장:**
- 멘토 등록 신청 (bio, 경력등급, 주력장르, 전문분야, 최대멘티수, 즉시수락 여부 등)
- 신청 상태 확인
- 내 프로필 조회·수정
- 받은 신청 목록·상세 조회
- 멘티 수락 / 거절
- 멘토링 완료 처리
- 피드백 작성 (V1/V2)
- 원고 다운로드 URL 조회
- 멘티 목록 조회 (V2: N+1 개선)
- 통계 조회 (신청 대기·이번 달 수락·거절 수)
- 통계 상세 (총 멘티, 완료 세션, 평균 만족도)

---

## 5. 결제

### PaymentStatus Enum
| 값 | 한국어 |
|----|--------|
| `PENDING` | 결제 대기 |
| `COMPLETED` | 결제 완료 |
| `FAILED` | 결제 실패 |
| `REFUNDED` | 환불됨 |

### 주요 기능
- PortOne 연동 결제 (prepare → 결제창 → confirm)
- 결제 취소·환불
- 결제 이력 조회
- Webhook 수신 처리 (PortOne → 서버)

---

## 6. 구독

### SubscriptionStatus Enum
| 값 | 한국어 |
|----|--------|
| `ACTIVE` | 구독 중 |
| `CANCELLED` | 해지됨 |
| `PAUSED` | 일시정지 |

### 주요 기능
- 구독 플랜 결제 (prepare → complete)
- 내 구독 현황 조회
- 구독 해지

---

## 7. 포인트

### 주요 기능
- 포인트 잔액 조회
- 포인트는 결제·구독·회차 구매 시 사용

---

## 8. 정산 / 출금

### WithdrawalStatus Enum
| 값 | 한국어 |
|----|--------|
| `PENDING` | 신청 중 |
| `APPROVED` | 승인됨 |
| `REJECTED` | 거절됨 |
| `COMPLETED` | 완료 |

### StatisticsPeriod Enum
`DAILY` / `WEEKLY` / `MONTHLY`

### 주요 기능
- 정산 개요 조회 (총 수익, 출금 가능 금액)
- 기간별 수익 통계 (일별·주별·월별)
- 계좌 인증 (은행 계좌 등록 → 1원 인증)
- 출금 신청·목록·상세 조회
- 출금 상태별 필터, 날짜 범위 필터

---

## 9. 서재

### LibraryType Enum
| 값 | 한국어 |
|----|--------|
| `READING` | 읽는 중 |
| `BOOKMARK` | 즐겨찾기 |
| `COMPLETED` | 완독 |
| `PURCHASED` | 구매한 작품 |

### 주요 기능
- 서재에 소설 추가
- 내 서재 목록 조회 (타입별 필터, 페이징, 정렬)

---

## 10. 검색

### 주요 기능
- 소설 키워드 검색 (V1 기본 / V2 N+1 개선)
- 태그 기반 검색 (복수 태그)
- 작가 검색
- 인기 검색어 조회 (비로그인 가능)
- 최근 검색어 조회 (로그인 필요)
- 인기 태그 조회

---

## 11. 알림

### NotificationType (일부)
멘토링 수락/거절, 회차 구매, 이벤트 알림 등 이벤트 드리븐 다수

### 주요 기능
- 알림 목록 조회 (페이징)
- 읽지 않은 알림 수 조회
- 알림 단건 읽음 처리
- 전체 읽음 처리

---

## 12. 이벤트

### EventStatus Enum
| 값 | 한국어 |
|----|--------|
| `PENDING` | 예정 |
| `ONGOING` | 진행 중 |
| `COMPLETED` | 종료 |

### 주요 기능
- 이벤트 목록 조회 (상태 필터, 비로그인 가능)
- 이벤트 상세 조회
- 이벤트 참여 신청

---

## 13. 신고

### ReportStatus Enum
| 값 | 한국어 |
|----|--------|
| `PENDING` | 처리 대기 |
| `RESOLVED` | 해결됨 |
| `REJECTED` | 기각됨 |
| `CLOSED` | 종료 |

### ReportTargetType Enum
`EPISODE_COMMENT`, `USER_MENTORSHIP`

### 주요 기능
- 신고 등록 (댓글, 멘토십 대상)
- 관리자: 신고 목록 조회·처리

---

## 14. 채팅

### MessageType Enum
`TEXT`, `IMAGE`, `FILE`

### 주요 기능
- 멘토십 기반 채팅방 생성
- 내 채팅방 목록 조회
- 메시지 이력 조회 (페이징)
- 채팅방 나가기
- 읽음 처리
- **WebSocket(STOMP)** 실시간 메시지 송수신 (`/ws-chat/**`)
- 이미지·파일 첨부

---

## 15. 독서 캘린더

### ReadingStatus Enum (추정)
`READING`, `COMPLETED`, `DROPPED`

### 주요 기능
- 독서 기록 추가
- 독서 기록 목록 조회 (날짜·소설 필터)
- 캘린더 뷰 (날짜 범위별 일별 데이터)
- 월별 독서 통계
- **READER / AUTHOR 권한만 접근 가능**

---

## 16. AI 기능

### 커버 AI (`/api/ai/novels`)
- 소설 커버 이미지 자동 생성 (비동기 Job)
- Job 상태: `PENDING` → `PROCESSING` → `COMPLETED` / `FAILED`
- 상태 폴링으로 완료 확인

### 리뷰 AI (`/api/ai/author`)
- 회차 AI 리뷰 생성 (V1: 동기 / V2: 비동기 Job)
- V2: Job 상태 폴링 (`PROCESSING` → `COMPLETED` / `FAILED`)

### 추천 AI (`/api/ai/recommendation`)
- 비로그인: 트렌드 기반 추천
- 로그인: 사용자 행동 기반 개인화 추천

### AI 고객지원 채팅 (`/api/ai-support`)
- SSE(Server-Sent Events) 스트리밍 응답
- 세션 초기화 기능
- **로그인 필수**

---

## 17. 국립도서관 연동

### 주요 기능
- 도서 키워드 검색
- 도서 저장
- 도서 상세 조회
- 내 책장에 도서 추가

---

## 18. 파일 업로드

### 주요 기능
- 채팅 파일 업로드 (이미지·문서, max 10MB)
- 원고 파일 업로드 (txt, hwp)
- 업로드 후 S3 URL 반환 → 다른 API에 URL 전달

---

## 19. 관리자

### 권한 체계
- `ADMIN`: 일반 관리자 (대시보드, 신고 처리)
- `SUPER_ADMIN`: 최고 관리자 (어드민 계정 승인 포함)

### 주요 기능
- 대시보드 조회 (V1/V2/실시간/히스토리)
  - 신규 사용자, 신규 소설, 매출, 멘토 신청 등 종합 지표
- 사용자 목록·승인·거절 (SUPER_ADMIN 전용)
- 신고 목록 조회·처리
- 멘토 신청 목록·승인·거절
