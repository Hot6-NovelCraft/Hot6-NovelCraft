# NovelCraft API 명세

> Base URL: `/api`  
> 인증: `Authorization: Bearer {accessToken}` 헤더  
> 모든 응답은 `BaseResponse<T>` 래퍼로 감싸짐

---

## 공통 응답 형식

### BaseResponse
```json
{
  "success": true,
  "status": "200",
  "message": "성공 메시지",
  "data": { ... }
}
```

### PageResponse (페이징 응답)
```json
{
  "content": [...],
  "currentPage": 0,
  "totalPages": 5,
  "totalElements": 48,
  "size": 10,
  "isLast": false
}
```

### 에러 응답
```json
{
  "success": false,
  "status": "400",
  "message": "에러 메시지",
  "data": null
}
```

| HTTP 상태 | 상황 |
|----------|------|
| 200 | 성공 |
| 201 | 생성 완료 |
| 400 | 유효성 검사 실패, 잘못된 요청 |
| 401 | 인증 실패 (토큰 없음/만료) |
| 403 | 권한 없음 |
| 404 | 리소스 없음 |
| 409 | 중복/충돌 |
| 500 | 서버 오류 |

---

## 인증 / 회원 (`/api/auth`)

### 로그인
```
POST /api/auth/login
인증: 불필요
```
**Request**
```json
{
  "email": "user@example.com",
  "password": "password123"
}
```
**Response `data`**
```json
{
  "email": "user@example.com",
  "nickname": "작가닉네임",
  "role": "AUTHOR",
  "accessToken": "eyJ...",
  "refreshToken": "eyJ..."
}
```

---

### 이메일 중복 확인
```
GET /api/auth/email/check?email={email}
인증: 불필요
```
**Response** `data: null` (200 = 사용 가능, 409 = 중복)

---

### 닉네임 중복 확인
```
GET /api/auth/nickname/check?nickname={nickname}
인증: 불필요
```

---

### 휴대폰 인증 코드 발송
```
POST /api/auth/phone/send
인증: 불필요
```
**Request** `{ "phoneNo": "01012345678" }`

---

### 휴대폰 인증 코드 확인
```
POST /api/auth/phone/verify
인증: 불필요
```
**Request** `{ "phoneNo": "01012345678", "code": "123456" }`  
**Response `data`** `"tempToken"` (임시 토큰 - 회원가입 2단계에 사용)

---

### 회원가입 (공통 정보)
```
POST /api/auth/signup
인증: 불필요
```
**Request**
```json
{
  "email": "user@example.com",
  "password": "password123",
  "nickname": "닉네임",
  "phoneNo": "01012345678",
  "phoneToken": "tempToken"
}
```

---

### 독자 프로필 등록
```
POST /api/auth/signup/reader
인증: 필요 (공통 가입 후 발급된 토큰)
```

---

### 작가 프로필 등록 / 수정
```
POST /api/auth/signup/author    (최초 등록)
PATCH /api/auth/users/me/author  (수정)
인증: 필요
```
**Request**
```json
{
  "bio": "작가 소개",
  "careerLevel": "INTERMEDIATE",
  "mainGenre": "FANTASY",
  "penName": "필명",
  "allowMenteeRequest": true
}
```

---

### 내 정보 조회
```
GET /api/auth/users/me
인증: 필요
```
**Response `data`** - 역할에 따라 다른 필드 포함

---

### 비밀번호 변경
```
PATCH /api/auth/users/me/password
인증: 필요
```
**Request** `{ "oldPassword": "...", "newPassword": "..." }`

---

### 로그아웃
```
POST /api/auth/logout
인증: 필요
```

---

### 회원 탈퇴
```
DELETE /api/auth/users/delete
인증: 필요
```

---

### 계정 복구
```
PATCH /api/auth/users/restore
인증: 불필요
```
**Request** `{ "email": "...", "phoneNo": "..." }`

---

### 작가 팔로우/언팔로우
```
POST /api/auth/authors/{authorId}/follow
인증: 필요
```
**Response `data`** `{ "authorId": 1, "following": true }`

---

## 소설 (`/api/novels`, `/api/v1/novels`, `/api/v2/novels`)

### 소설 목록 (비로그인 가능)
```
GET /api/v2/novels?genre=FANTASY&status=ONGOING&page=0&size=20
인증: 불필요
```
**Query Params**
| 파라미터 | 타입 | 설명 |
|---------|------|------|
| `genre` | `MainGenre` (optional) | 장르 필터 |
| `status` | `NovelStatus` (optional) | 상태 필터 |
| `page` | int | 페이지 번호 (기본 0) |
| `size` | int | 페이지 크기 (기본 20) |

**Response `data`** `PageResponse<NovelListResponse>`
```json
{
  "content": [
    {
      "novelId": 1,
      "title": "소설 제목",
      "authorName": "작가명",
      "genre": "FANTASY",
      "status": "ONGOING",
      "coverImageUrl": "https://...",
      "likeCount": 120,
      "viewCount": 5000
    }
  ],
  "currentPage": 0,
  "totalPages": 3,
  "totalElements": 50,
  "size": 20,
  "isLast": false
}
```

---

### 소설 상세
```
GET /api/novels/{novelId}
인증: 불필요
```

---

### 랭킹 조회
```
GET /api/novels/ranking/realtime
GET /api/novels/ranking/weekly
인증: 불필요
```

---

### 소설 생성
```
POST /api/novels
인증: 필요 (AUTHOR)
```
**Request**
```json
{
  "title": "소설 제목",
  "description": "줄거리",
  "genre": "FANTASY",
  "tags": ["REGRESSION", "ISEKAI"],
  "isAdult": false,
  "coverImageUrl": "https://..."
}
```

---

### 소설 수정
```
PATCH /api/novels/{novelId}
인증: 필요 (본인 작가)
```

---

### 소설 삭제
```
DELETE /api/novels/{novelId}
인증: 필요 (본인 작가)
```

---

### 북마크 토글
```
POST /api/novels/{novelId}/bookmark
인증: 필요
```
**Response `data`** `{ "novelId": 1, "bookmarked": true }`

---

### 작가 본인 소설 목록
```
GET /api/author/novels?page=0&size=10
인증: 필요 (AUTHOR)
```

---

### 소설 위키 목록
```
GET /api/novels/{novelId}/wiki
인증: 필요
```

### 소설 위키 생성
```
POST /api/novels/{novelId}/wiki
인증: 필요
```
**Request** `{ "category": "CHARACTER", "title": "주인공", "content": "..." }`

### 소설 위키 삭제
```
DELETE /api/novels/{novelId}/wiki/{wikiId}
인증: 필요
```

---

## 회차 (`/api/episodes`, `/api/novels/{novelId}/episodes`)

### 회차 목록 (비로그인 가능)
```
GET /api/novels/{novelId}/episodes?page=0&size=20
인증: 불필요
```

### 회차 본문 조회
```
GET /api/v2/episodes/{episodeId}
인증: 불필요 (무료 회차) / 필요 (유료 회차)
```

### 회차 생성
```
POST /api/novels/{novelId}/episodes
인증: 필요 (AUTHOR)
```
**Request**
```json
{
  "title": "1화 제목",
  "content": "회차 내용...",
  "episodeNumber": 1,
  "isFree": true,
  "price": 0
}
```

### 회차 수정
```
PATCH /api/episodes/{episodeId}
인증: 필요 (본인 작가)
```

### 회차 삭제
```
DELETE /api/episodes/{episodeId}
인증: 필요 (본인 작가)
```

### 회차 발행
```
POST /api/episodes/{episodeId}/publish
인증: 필요 (AUTHOR)
```

### 회차 단건 구매
```
POST /api/episodes/{episodeId}/purchase
인증: 필요
```

### 소설 전권 구매
```
POST /api/novels/{novelId}/episodes/purchase
인증: 필요
```

### 댓글 작성
```
POST /api/episodes/{episodeId}/comments
인증: 필요
```
**Request** `{ "content": "댓글 내용" }`

### 댓글 목록
```
GET /api/episodes/{episodeId}/comments?page=0&size=20
인증: 불필요
```

### 댓글 삭제
```
DELETE /api/comments/{commentId}
인증: 필요 (본인)
```

### 좋아요 토글
```
POST /api/episodes/{episodeId}/like
인증: 필요
```

### 작가 회차 통계
```
GET /api/novels/{novelId}/episodes/stats
인증: 필요 (AUTHOR)
```

---

## 멘토십 (`/api/mentorships`, `/api/mentors`, `/api/mentorings`)

### 멘토 목록 (비로그인 가능)
```
GET /api/mentorships/mentors?genre=FANTASY&careerLevel=INTERMEDIATE&page=0&size=20
인증: 불필요
```
**Query Params**
| 파라미터 | 타입 | 설명 |
|---------|------|------|
| `genre` | String (optional) | 장르 필터 |
| `careerLevel` | `CareerLevel` (optional) | 경력 등급 필터 |
| `page` | int | 페이지 |
| `size` | int | 크기 (기본 20) |

**Response `data`** `Page<MentorshipListResponse>`
```json
{
  "content": [
    {
      "mentorId": 1,
      "nickname": "멘토닉네임",
      "careerLevel": "INTERMEDIATE",
      "mainGenres": ["FANTASY", "SF"],
      "specialFields": ["세계관 구축", "캐릭터 설계"],
      "mentoringStyle": ["피드백 중심", "대화형"],
      "awardsCareer": "2023 공모전 우수상",
      "maxMentees": 3
    }
  ]
}
```

---

### 멘토 상세 조회
```
GET /api/mentorships/mentors/{mentorId}
인증: 불필요
```
**Response `data`** `MentorshipDetailResponse`
```json
{
  "mentorId": 1,
  "nickname": "멘토닉네임",
  "careerLevel": "INTERMEDIATE",
  "mainGenres": ["FANTASY"],
  "specialFields": ["세계관 구축", "문체 교정"],
  "mentoringStyle": ["피드백 중심"],
  "awardsCareer": "...",
  "bio": "멘토 소개글",
  "maxMentees": 3
}
```

---

### 멘토링 신청
```
POST /api/mentorships
인증: 필요
```
**Request**
```json
{
  "mentorId": 1,
  "motivation": "신청 동기 (선택, max 500자)",
  "currentNovelId": 42,
  "manuscriptUrl": "https://s3.../manuscript.txt"
}
```
> `currentNovelId`, `manuscriptUrl` 은 선택 필드

**Response `data`**
```json
{ "mentorshipId": 10 }
```

---

### 내 멘토링 이력 조회
```
GET /api/mentorships/v2/me/history?status=PENDING
인증: 필요
```
**Query Params** `status`: `PENDING` | `ACCEPTED` | `REJECTED` | `COMPLETED` (optional, 없으면 전체)

**Response `data`** `List<MentorshipHistoryResponse>`
```json
[
  {
    "mentorshipId": 10,
    "mentorNickname": "멘토닉네임",
    "status": "PENDING",
    "appliedAt": "2024-01-15T10:30:00"
  }
]
```

---

### 멘토 만족도 평가
```
POST /api/mentorships/{mentorshipId}/reviews
인증: 필요
```
**Request**
```json
{
  "rating": 5,
  "content": "후기 내용 (선택, max 100자)"
}
```

---

### 받은 멘토링 신청 목록 (멘토용)
```
GET /api/v2/mentorings/received?page=0&size=10
인증: 필요 (멘토)
```

---

### 멘티 수락
```
PATCH /api/v2/mentorings/{mentoringId}/mentees/{menteeId}/accept
인증: 필요 (멘토)
```

### 멘티 거절
```
PATCH /api/v2/mentorings/{mentoringId}/mentees/{menteeId}/reject
인증: 필요 (멘토)
```

### 멘토링 완료
```
PATCH /api/v2/mentorings/{mentoringId}/complete
인증: 필요 (멘토)
```

### 원고 다운로드 URL 조회
```
GET /api/v2/mentorings/{mentoringId}/documents
인증: 필요 (멘토)
```

### 피드백 작성
```
POST /api/v2/mentorings/{mentoringId}/feedbacks
인증: 필요 (멘토)
```
**Request** `{ "content": "피드백 내용", "sessionNumber": 1 }`

---

### 멘토 등록 신청
```
POST /api/mentors
인증: 필요 (AUTHOR)
```
**Request**
```json
{
  "bio": "자기소개 (10~500자, 필수)",
  "careerLevel": "INTERMEDIATE",
  "mainGenres": ["FANTASY", "SF"],
  "specialFields": ["세계관 구축", "캐릭터 설계"],
  "maxMentees": 3,
  "allowInstant": false,
  "mentoringStyles": ["피드백 중심"],
  "awardsCareer": "수상 경력 (선택)",
  "preferredMenteeDesc": "환영하는 멘티 유형 (선택)"
}
```
> `careerLevel` 값: `INTRODUCTION` | `ELEMENTARY` | `INTERMEDIATE` | `PROFICIENT`  
> `specialFields` 최소 2개 필수, `mainGenres` 최소 1개 필수  
> `maxMentees` 1~5 범위

**Response `data`**
```json
{
  "applicationId": 1,
  "status": "PENDING",
  "appliedAt": "2024-01-15T10:30:00"
}
```

---

### 내 멘토 신청 상태 조회
```
GET /api/mentors/me/status
인증: 필요
```
**Response `data`**
```json
{
  "mentorId": 1,
  "status": "PENDING",
  "rejectReason": null
}
```

### 내 멘토 프로필 조회
```
GET /api/mentors/me
인증: 필요 (승인된 멘토)
```

### 내 멘토 프로필 수정
```
PUT /api/mentors/me
인증: 필요 (멘토)
```

### 내 멘토링 통계
```
GET /api/mentors/me/statistics
인증: 필요 (멘토)
```

### 내 멘토링 통계 상세
```
GET /api/mentors/me/statistics/detail
인증: 필요 (멘토)
```
**Response `data`** `{ "totalMentees": 10, "completedSessions": 8, "averageSatisfaction": 4.5 }`

### 내 멘티 목록
```
GET /api/mentors/v2/me/mentees
인증: 필요 (멘토)
```

---

## 결제 (`/api/payments`)

### 결제 준비
```
POST /api/payments/prepare
인증: 필요
```
**Request** `{ "amount": 5000, "productName": "포인트 5000P", "productType": "POINT" }`  
**Response `data`** `{ "paymentId": "order_xxx", "merchantUid": "..." }`

### 결제 확인
```
POST /api/payments/confirm
인증: 필요
```
**Request** `{ "paymentId": "order_xxx", "impUid": "imp_xxx" }`

### 결제 취소
```
POST /api/payments/{paymentId}/cancel
인증: 필요
```
**Request** `{ "reason": "취소 사유" }`

### 결제 이력
```
GET /api/payments?page=0&size=10
인증: 필요
```

---

## 구독 (`/api/subscriptions`)

### 구독 준비
```
POST /api/subscriptions/prepare
인증: 필요
```

### 구독 완료
```
POST /api/subscriptions/complete
인증: 필요
```

### 내 구독 현황
```
GET /api/subscriptions/me
인증: 필요
```

### 구독 해지
```
DELETE /api/subscriptions/{id}
인증: 필요
```

---

## 포인트 (`/api/points`)

### 포인트 잔액
```
GET /api/points/balance
인증: 필요
```
**Response `data`** `{ "balance": 15000 }`

---

## 정산 / 출금 (`/api/revenues/me`)

### 정산 개요
```
GET /api/revenues/me
인증: 필요 (AUTHOR)
```

### 수익 통계
```
GET /api/revenues/me/statistics?period=MONTHLY&year=2024
인증: 필요 (AUTHOR)
```

### 계좌 인증 시작
```
POST /api/revenues/me/account/verify
인증: 필요 (AUTHOR)
```
**Request** `{ "bankCode": "004", "accountNumber": "123-456-789", "holderName": "홍길동" }`

### 계좌 인증 확인
```
POST /api/revenues/me/account/verify/confirm
인증: 필요 (AUTHOR)
```
**Request** `{ "verificationAmount": 1 }`

### 출금 신청
```
POST /api/revenues/me/exchanges
인증: 필요 (AUTHOR)
```
**Request** `{ "amount": 10000 }`

### 출금 이력
```
GET /api/revenues/me/exchanges?status=PENDING&startDate=2024-01-01&endDate=2024-01-31&page=0
인증: 필요 (AUTHOR)
```

### 출금 상세
```
GET /api/revenues/me/exchanges/{id}
인증: 필요 (AUTHOR)
```

---

## 서재 (`/api/libraries`)

### 서재에 추가
```
POST /api/libraries/{novelId}
인증: 필요
```
**Request** `{ "libraryType": "BOOKMARK" }`

### 내 서재 목록
```
GET /api/libraries/me?libraryType=READING&page=0&size=20&sort=recentRead
인증: 필요
```

---

## 검색 (`/api/search`)

### 소설 검색 (V2 권장)
```
GET /api/search/v2/novels?keyword=판타지&page=0&size=20
인증: 불필요
```

### 태그 검색
```
GET /api/search/v2/tags?tags=REGRESSION&tags=ISEKAI
인증: 불필요
```

### 작가 검색
```
GET /api/search/v2/authors?keyword=홍길동
인증: 불필요
```

### 인기 검색어
```
GET /api/search/keywords/popular
인증: 불필요
```

### 최근 검색어
```
GET /api/search/keywords/recent
인증: 필요
```

### 인기 태그
```
GET /api/search/tags/popular
인증: 불필요
```

---

## 알림 (`/api/notifications`)

### 알림 목록
```
GET /api/notifications?page=0&size=20
인증: 필요
```

### 읽지 않은 알림 수
```
GET /api/notifications/unread-count
인증: 필요
```
**Response `data`** `{ "count": 3 }`

### 단건 읽음
```
PATCH /api/notifications/{notificationId}/read
인증: 필요
```

### 전체 읽음
```
PATCH /api/notifications/read-all
인증: 필요
```

---

## 이벤트 (`/api/events`)

### 이벤트 목록
```
GET /api/events?status=ONGOING&page=0&size=20
인증: 불필요
```

### 이벤트 상세
```
GET /api/events/{eventId}
인증: 불필요
```

### 이벤트 참여
```
POST /api/events/{eventId}/participants
인증: 필요
```

---

## 신고 (`/api/reports`)

### 신고 등록
```
POST /api/reports
인증: 필요
```
**Request** `{ "targetId": 1, "targetType": "EPISODE_COMMENT", "reason": "신고 사유" }`

---

## 채팅 (`/api/chatrooms`)

### 채팅방 생성
```
POST /api/chatrooms
인증: 필요
```
**Request** `{ "mentorshipId": 10 }`

### 내 채팅방 목록
```
GET /api/chatrooms
인증: 필요
```

### 메시지 이력
```
GET /api/chatrooms/{roomId}/messages?page=0&size=50
인증: 필요
```

### 읽음 처리
```
PATCH /api/chatrooms/{roomId}/read
인증: 필요
```

### 채팅방 나가기
```
DELETE /api/chatrooms/{roomId}/leave
인증: 필요
```

### WebSocket (실시간 메시지)
```
연결: ws://host/ws-chat
구독: /topic/chatroom/{roomId}
발행: /app/chat.sendMessage
```

---

## 독서 캘린더 (`/api/calendars`)

> 권한: `READER` 또는 `AUTHOR`

### 독서 기록 추가
```
POST /api/calendars/me/records
인증: 필요
```

### 독서 기록 목록
```
GET /api/calendars/me/records?date=2024-01-15&novelId=1&page=0&size=20
인증: 필요
```

### 캘린더 조회
```
GET /api/calendars/me?startDate=2024-01-01&endDate=2024-01-31
인증: 필요
```

### 월별 통계
```
GET /api/calendars/me/statistics?year=2024&month=1
인증: 필요
```

---

## AI 기능

### 커버 이미지 생성 (비동기)
```
POST /api/ai/novels/{novelId}/cover
인증: 필요
```
**Response `data`** `{ "jobId": "job_xxx", "status": "PENDING" }`

### 커버 Job 상태 확인
```
GET /api/ai/novels/cover/status/{jobId}
인증: 필요
```
**Response `data`** `{ "jobId": "job_xxx", "status": "COMPLETED", "coverImageUrl": "https://..." }`

---

### 회차 AI 리뷰 (V2 비동기 권장)
```
POST /api/ai/author/v2/episodes/{episodeId}/ai-review
인증: 필요 (AUTHOR)
```
**Response `data`** `{ "jobId": "job_xxx", "status": "PROCESSING" }`

### AI 리뷰 Job 상태 확인
```
GET /api/ai/author/v2/jobs/{jobId}
인증: 불필요
```

---

### AI 추천
```
GET /api/ai/recommendation
인증: 불필요 (비로그인 시 트렌드, 로그인 시 개인화)
```

---

### AI 고객지원 채팅 (SSE 스트리밍)
```
POST /api/ai-support/chat
인증: 필요
Content-Type: application/json
Accept: text/event-stream
```
**Request** `{ "message": "질문 내용" }`  
**Response** `Flux<String>` (SSE 스트림)

### 채팅 세션 초기화
```
DELETE /api/ai-support/chat/session
인증: 필요
```

---

## 파일 업로드 (`/api/files`)

### 채팅 파일 업로드
```
POST /api/files/chat
인증: 필요
Content-Type: multipart/form-data
```
**Form Data** `file: (binary)` (max 10MB, 이미지/문서)  
**Response `data`** `{ "fileUrl": "https://s3.../..." }`

### 원고 파일 업로드
```
POST /api/files/manuscripts/upload
인증: 필요
Content-Type: multipart/form-data
```
**Form Data** `file: (binary)` (txt, hwp만)

---

## 관리자 (`/api/admin`)

> 모든 엔드포인트: `ADMIN` 또는 `SUPER_ADMIN` 권한 필요

### 대시보드
```
GET /api/admin/dashboard/v2
GET /api/admin/dashboard/live
GET /api/admin/dashboard/history?targetDate=2024-01-15
```

### 사용자 관리 (SUPER_ADMIN)
```
GET  /api/admin/users/pending
PATCH /api/admin/users/{userId}/approve
PATCH /api/admin/users/{userId}/reject
```

### 신고 관리
```
GET   /api/admin/reports?status=PENDING&targetType=EPISODE_COMMENT&page=0
PATCH /api/admin/reports/{reportId}
```

### 멘토 신청 관리
```
GET   /api/admin/mentors (추정)
PATCH /api/admin/mentors/{mentorId}/approve
PATCH /api/admin/mentors/{mentorId}/reject
```

---

## 국립도서관 (`/api/v1/national-library`)

### 도서 검색
```
GET /api/v1/national-library/books/search?keyword=해리포터&page=0&size=10
인증: 불필요
```

### 도서 저장
```
POST /api/v1/national-library/books
인증: 불필요
```

### 도서 상세
```
GET /api/v1/national-library/books/{bookId}
인증: 불필요
```

### 내 책장에 추가
```
POST /api/v1/national-library/books/shelf
인증: 필요
```

---

## 토큰 갱신 방식

- 액세스 토큰 만료(401) 시 `Refresh-Token: Bearer {refreshToken}` 헤더와 함께 원래 요청 재시도
- 서버가 새 `Authorization` 헤더로 응답하면 클라이언트에서 저장
- 리프레시 토큰도 만료되면 로그아웃 처리
