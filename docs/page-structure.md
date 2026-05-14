# NovelCraft 프론트엔드 페이지 구조

---

## 목차

1. [전체 페이지 목록](#1-전체-페이지-목록)
2. [사용자 플로우](#2-사용자-플로우)
3. [페이지별 상세 설계](#3-페이지별-상세-설계)
4. [관리자 페이지](#4-관리자-페이지)
5. [상태 관리 포인트](#5-상태-관리-포인트)
6. [에러 처리 전략](#6-에러-처리-전략)
7. [권한별 접근 제어](#7-권한별-접근-제어)

---

## 1. 전체 페이지 목록

### 공개 페이지 (비로그인 접근 가능)
| 페이지 | 경로 | 설명 |
|--------|------|------|
| 메인 | `/` | 추천·랭킹·신작 노출 |
| 소설 목록 | `/novel-list` | 장르·상태 필터, 페이징 |
| 소설 상세 | `/novel-detail?id={novelId}` | 소설 정보, 회차 목록 |
| 회차 읽기 | `/episode-read?id={episodeId}` | 무료 회차 본문 |
| 멘토 목록 | `/mentor-list` | 멘토 찾기 |
| 이벤트 목록 | `/events` | 이벤트 탐색 |
| 이벤트 상세 | `/events/{eventId}` | 이벤트 상세·참여 |
| 도서 검색 | `/library-search` | 국립도서관 도서 검색 |
| 로그인 | `/login` | 이메일 로그인, 소셜 로그인 |
| 회원가입 | `/signup` | 가입 단계별 폼 |

### 로그인 필요 페이지
| 페이지 | 경로 | 권한 |
|--------|------|------|
| 마이페이지 | `/mypage` | 전체 |
| 서재 | `/mypage?tab=library` | 전체 |
| 알림 | `/mypage?tab=notification` | 전체 |
| 독서 캘린더 | `/calendar` | READER, AUTHOR |
| 결제 | `/payment` | 전체 |
| 결제 이력 | `/mypage?tab=payment` | 전체 |
| 구독 관리 | `/mypage?tab=subscription` | 전체 |
| 에디터 | `/editor` | AUTHOR |
| 작가 소설 관리 | `/editor?tab=novels` | AUTHOR |
| 회차 에디터 | `/editor?novelId={id}` | AUTHOR |
| 정산 | `/settlement` | AUTHOR |
| 멘토 대시보드 | `/mentor-dashboard` | MENTOR |
| 채팅 | `/chat?mentorshipId={id}` | 전체 |
| AI 고객지원 | `/ai-support` | 전체 |

### 관리자 페이지
| 페이지 | 경로 | 권한 |
|--------|------|------|
| 관리자 대시보드 | `/admin` | ADMIN, SUPER_ADMIN |
| 사용자 관리 | `/admin/users` | SUPER_ADMIN |
| 신고 관리 | `/admin/reports` | ADMIN, SUPER_ADMIN |
| 멘토 신청 관리 | `/admin/mentors` | ADMIN, SUPER_ADMIN |

---

## 2. 사용자 플로우

### 2-1. 회원가입 플로우
```
[회원가입 시작]
  → 이메일·비밀번호·닉네임 입력
  → 이메일 중복 확인 (실시간)
  → 닉네임 중복 확인 (실시간)
  → 휴대폰 인증 (코드 발송 → 입력 → 임시 토큰 발급)
  → 공통 가입 완료 (accessToken 발급)
  → 역할 선택: [독자] / [작가]
    ↳ 독자: 선호 장르, 독서 목표 입력 → 완료
    ↳ 작가: 장르, 소개, 필명, 경력 등급 입력 → 완료
  → 메인 페이지 이동
```

### 2-2. 소설 탐색 → 열람 플로우
```
[메인 페이지 / 소설 목록]
  → 장르·태그·상태 필터 선택
  → 소설 카드 클릭 → 소설 상세
    ↳ 북마크, 서재에 추가
    ↳ 회차 목록 확인
      → 무료 회차 클릭 → 바로 읽기
      → 유료 회차 클릭
        ↳ 비로그인 → 로그인 유도
        ↳ 로그인 + 포인트 부족 → 충전 유도
        ↳ 로그인 + 포인트 충분 → 구매 확인 → 읽기
```

### 2-3. 작가 소설 등록 플로우
```
[에디터] → 소설 생성
  → 제목, 장르, 태그, 소개, 커버 이미지 입력
    ↳ AI 커버 생성 (비동기 polling, PENDING→COMPLETED)
  → 소설 생성 완료
  → 회차 작성
    → 제목, 본문 작성
    → 무료/유료 설정, 가격 설정
    → 저장 (DRAFT) / 발행 (PUBLISHED)
```

### 2-4. 멘토링 신청 플로우 (멘티)
```
[멘토 목록] → 장르·경력 필터
  → 멘토 카드 클릭 → 멘토 상세 모달
    → [멘토링 신청하기] 클릭
      ↳ 비로그인 → 로그인 페이지
      ↳ 로그인 → 신청 폼
        → 소설 선택 (선택), 신청 동기 입력 (선택), 원고 업로드 (선택)
        → 신청 완료 → PENDING 상태
  → [내 멘토링 이력] 탭
    → 상태별 확인 (PENDING/ACCEPTED/REJECTED/COMPLETED)
    → ACCEPTED → 채팅 바로가기
    → COMPLETED → 만족도 평가 (별점 1~5, 후기)
```

### 2-5. 멘토 등록 플로우
```
[멘토 목록] → [✋ 멘토 신청] 탭
  → 폼 작성:
    - 자기소개 (필수, 10~500자)
    - 경력 등급 선택 (INTRODUCTION/ELEMENTARY/INTERMEDIATE/PROFICIENT)
    - 주력 장르 선택 (다중 선택, 최소 1개)
    - 전문 분야 선택 (다중 선택, 최소 2개)
    - 최대 멘티 수 (1~5)
    - 즉시 수락 허용 여부 (체크박스)
    - 멘토링 스타일 (선택)
    - 수상·출간 경력 (선택)
  → 신청 → 상태 확인 (PENDING/APPROVED/REJECTED)
  → APPROVED → 멘토 대시보드 활성화
```

### 2-6. 멘토 대시보드 플로우
```
[멘토 대시보드]
  → 통계 요약 (신청 대기, 이번 달 수락/거절)
  → 받은 신청 목록
    → 신청 상세 → 수락 / 거절
  → 현재 멘티 목록
    → 채팅 이동
    → 피드백 작성
    → 멘토링 완료 처리
  → 상세 통계 (총 멘티, 완료 세션, 평균 만족도)
```

### 2-7. 결제 플로우
```
[포인트 충전 / 구독 결제]
  → 금액/플랜 선택
  → 결제 준비 (prepare → merchantUid 발급)
  → PortOne 결제창 (클라이언트 SDK)
  → 결제 완료 → confirm 요청
  → 성공: 포인트 적립 / 구독 활성화
  → 실패: 에러 토스트
```

### 2-8. 정산 플로우 (작가)
```
[정산 페이지]
  → 계좌 등록: 은행·계좌번호·예금주 입력 → 1원 인증 → 확인
  → 수익 통계 확인 (일별/주별/월별)
  → 출금 신청: 금액 입력 → 신청
  → 출금 이력 조회 (상태별 필터)
```

---

## 3. 페이지별 상세 설계

### 메인 페이지 (`/`)
**섹션 구성**
- 히어로 배너 (최신 이벤트 또는 추천 소설)
- AI 개인화 추천 섹션 (로그인: 개인화, 비로그인: 트렌드)
- 실시간 랭킹 (Top 10)
- 주간 랭킹
- 신작 목록 (최신 순)
- 장르별 빠른 탐색

**API 호출**
- `GET /api/ai/recommendation`
- `GET /api/novels/ranking/realtime`
- `GET /api/novels/ranking/weekly`
- `GET /api/novels?type=new&limit=10`

---

### 소설 목록 (`/novel-list`)
**기능**
- 장르 필터 (MainGenre enum 전체)
- 상태 필터 (NovelStatus enum)
- 태그 필터 (MainTag enum 다중 선택)
- 키워드 검색
- 정렬 (최신순, 인기순, 별점순)
- 무한 스크롤 또는 페이징

**API 호출**
- `GET /api/v2/novels?genre=&status=&page=0&size=20`
- `GET /api/search/v2/novels?keyword=`

---

### 소설 상세 (`/novel-detail?id={novelId}`)
**섹션 구성**
- 커버 이미지, 제목, 작가명, 장르, 태그
- 줄거리
- 통계 (조회수, 좋아요, 북마크 수)
- 북마크 버튼, 서재 추가 버튼
- 위키 탭 (캐릭터/세계관/플롯/용어집)
- 회차 목록 (번호, 제목, 무료/유료, 발행일)
- 전권 구매 버튼

**API 호출**
- `GET /api/novels/{novelId}`
- `GET /api/novels/{novelId}/episodes`
- `GET /api/novels/{novelId}/wiki` (로그인 시)

---

### 회차 읽기 (`/episode-read?id={episodeId}`)
**기능**
- 본문 뷰어 (폰트 크기, 배경색 설정)
- 이전/다음 회차 네비게이션
- 좋아요 버튼
- 댓글 섹션 (목록, 작성, 삭제)
- 신고 버튼
- 독서 기록 자동 저장 (로그인 시)

**API 호출**
- `GET /api/v2/episodes/{episodeId}`
- `GET /api/episodes/{episodeId}/comments`
- `POST /api/episodes/{episodeId}/like` (로그인)
- `POST /api/calendars/me/records` (로그인)

---

### 에디터 (`/editor`)
**탭 구성**

**[내 소설 관리] 탭**
- 소설 목록 카드 (수정·삭제·회차 관리 링크)
- 소설 생성 버튼 → 모달 폼

**[회차 작성] 탭**
- 소설 선택 드롭다운
- 회차 번호, 제목, 본문 에디터
- 무료/유료 설정, 가격 입력
- 저장(DRAFT) / 발행(PUBLISHED) 버튼
- AI 리뷰 요청 버튼 (V2 비동기, 폴링)

**[AI 커버 생성] 탭**
- 소설 선택 → 커버 생성 요청 → Job 상태 폴링
- 완료 후 커버 이미지 미리보기

**API 호출**
- `GET /api/author/novels`
- `POST /api/novels`, `PATCH /api/novels/{id}`, `DELETE /api/novels/{id}`
- `GET /api/author/novels/{novelId}/episodes`
- `POST /api/novels/{novelId}/episodes`, `PATCH /api/episodes/{id}`, `POST /api/episodes/{id}/publish`
- `POST /api/ai/author/v2/episodes/{id}/ai-review` → polling `GET /api/ai/author/v2/jobs/{jobId}`
- `POST /api/ai/novels/{novelId}/cover` → polling `GET /api/ai/novels/cover/status/{jobId}`

---

### 마이페이지 (`/mypage`)
**탭 구성**

| 탭 | 내용 |
|----|------|
| `profile` | 기본 정보, 비밀번호 변경, 성인 인증 |
| `library` | 서재 (독서중/즐겨찾기/완독/구매) |
| `notification` | 알림 목록, 전체 읽음 |
| `payment` | 결제 이력, 포인트 충전 |
| `subscription` | 구독 현황, 해지 |
| `calendar` | 독서 캘린더 (READER/AUTHOR) |

---

### 정산 페이지 (`/settlement`)
**섹션 구성**
- 수익 개요 카드 (총 수익, 출금 가능 금액)
- 기간별 수익 차트 (일별/주별/월별 탭)
- 계좌 관리 (등록·인증)
- 출금 신청 폼
- 출금 이력 테이블 (상태 필터)

---

### 멘토 목록 (`/mentor-list`)
**탭 구성**
| 탭 | 내용 |
|----|------|
| `find` | 멘토 카드 그리드, 장르·경력 필터 |
| `history` | 내 멘토링 이력 (상태별 필터) |
| `mentor-apply` | 멘토 등록 신청 폼 |

---

### 멘토 대시보드 (`/mentor-dashboard`)
**섹션 구성**
- 통계 요약 카드 (대기·수락·거절)
- 받은 신청 목록 (수락/거절 버튼 포함)
- 현재 멘티 목록 (채팅·피드백·완료 버튼)
- 내 프로필 수정 폼
- 상세 통계 (총 멘티, 완료 세션, 평균 만족도)

---

### 채팅 (`/chat?mentorshipId={id}`)
**기능**
- WebSocket(STOMP) 연결
- 메시지 이력 로드 (페이징, 위로 스크롤 시 이전 메시지)
- 텍스트 입력, 파일/이미지 첨부
- 실시간 읽음 처리

---

## 4. 관리자 페이지

### 관리자 대시보드 (`/admin`)
**섹션 구성**
- 핵심 지표 카드 (신규 사용자, 신규 소설, 매출, 신규 멘토 신청)
- 실시간 / 히스토리 모드 전환
- 유저 역할별 분포, 소설 상태별 분포 차트

### 사용자 관리 (`/admin/users`) — SUPER_ADMIN 전용
- 대기 중 어드민 계정 목록
- 승인 / 거절 버튼

### 신고 관리 (`/admin/reports`)
- 신고 목록 (상태·대상 유형 필터, 페이징)
- 신고 처리 (해결/기각/종료)

### 멘토 신청 관리 (`/admin/mentors`)
- 멘토 신청 목록 (PENDING 기본)
- 승인 / 거절 (거절 사유 입력)

---

## 5. 상태 관리 포인트

### 전역 상태 (Context / Pinia / Zustand 등)

| 상태 | 설명 | 갱신 시점 |
|------|------|----------|
| `auth.user` | 로그인 사용자 정보 (nickname, role, email) | 로그인/로그아웃/프로필 수정 |
| `auth.accessToken` | JWT 액세스 토큰 | 로그인/토큰 갱신 |
| `auth.refreshToken` | JWT 리프레시 토큰 | 로그인 |
| `point.balance` | 포인트 잔액 | 충전/구매 후 |
| `notification.unreadCount` | 읽지 않은 알림 수 | 알림 읽음 처리 / 폴링 |
| `subscription.status` | 구독 현황 | 구독 완료/해지 후 |

### 페이지 로컬 상태

| 페이지 | 로컬 상태 |
|--------|----------|
| 소설 목록 | 필터(genre, status, tags), 페이지, 정렬 |
| 소설 상세 | bookmarked, libraryType, 회차 목록 페이지 |
| 회차 읽기 | 뷰어 설정(폰트크기, 배경색), 좋아요 상태, 댓글 페이지 |
| 멘토 목록 | 필터(genre, careerLevel), 현재 탭, 멘토 페이지 |
| 에디터 | 선택된 소설ID, 현재 탭, 회차 초안 내용 |
| AI Job | jobId, pollingInterval, jobStatus |
| 채팅 | WebSocket 연결 객체, 메시지 목록, hasMore |
| 정산 | 기간 탭, 차트 데이터, 출금 필터 |

### 비동기 Job 폴링 패턴
```
1. Job 시작 → jobId 저장
2. setInterval (2~3초) 로 상태 확인
   PROCESSING → 계속 폴링
   COMPLETED  → 결과 표시, interval 종료
   FAILED     → 에러 표시, interval 종료
3. 페이지 unmount 시 interval 정리 (clearInterval)
```

해당 기능:
- AI 커버 이미지 생성 (`/api/ai/novels/cover/status/{jobId}`)
- AI 회차 리뷰 (`/api/ai/author/v2/jobs/{jobId}`)

### 토큰 자동 갱신 패턴
```
api 요청 → 401 수신 → refreshToken으로 재시도
재시도 성공 → 새 accessToken 저장 → 원래 요청 반환
재시도 실패 → 토큰 삭제 → /login 리디렉션
```
> `common.js`의 `api.request()` 내부에 이미 구현되어 있음

### WebSocket 연결 패턴 (채팅)
```
STOMP over SockJS
연결: /ws-chat
구독: /topic/chatroom/{roomId}
메시지 발행: /app/chat.sendMessage
메시지 구조: { roomId, content, messageType, fileUrl }
페이지 진입 시 connect, 이탈 시 disconnect
```

---

## 6. 에러 처리 전략

### HTTP 상태 코드별 처리
| 상태 | 처리 방식 |
|------|----------|
| 400 | `data.message` 를 토스트 에러로 표시 |
| 401 | 토큰 갱신 시도 → 실패 시 로그인 페이지 |
| 403 | "권한이 없습니다" 토스트 또는 접근 불가 페이지 |
| 404 | "존재하지 않는 콘텐츠입니다" 표시 |
| 409 | "이미 존재합니다" 등 중복 안내 |
| 500 | "서버 오류가 발생했습니다. 잠시 후 다시 시도해주세요" |

### 검증 에러 (400)
- 백엔드 응답 `data.message` 에 구체적인 검증 실패 메시지 포함
- Toast.error(message) 로 사용자에게 노출

### 네트워크 에러
- fetch 자체 실패 시 "네트워크 오류" 안내

---

## 7. 권한별 접근 제어

### 네비게이션 표시 조건
```
비로그인:        로그인, 회원가입 버튼
로그인(READER):  내 서재, 마이페이지, 멘티 버튼
로그인(AUTHOR):  내 서재, 에디터, 마이페이지, 멘티 버튼
로그인(MENTOR):  내 서재, 에디터, 멘토 대시보드, 마이페이지, 멘티 버튼
로그인(ADMIN):   관리자 버튼 추가
```

### 페이지 접근 가드
| 조건 | 대상 페이지 | 처리 |
|------|------------|------|
| 비로그인 | 마이페이지, 에디터, 채팅 등 | `/login`으로 리디렉션 |
| READER/AUTHOR 아님 | `/calendar` | 접근 불가 안내 |
| AUTHOR 아님 | 에디터, 정산 | 접근 불가 안내 |
| MENTOR 아님 | 멘토 대시보드 | 접근 불가 안내 |
| ADMIN 아님 | `/admin/**` | `/`으로 리디렉션 |

### 멘토 등록 신청 가능 조건
- 로그인 필요 (AUTHOR 역할)
- 이미 PENDING/APPROVED 상태이면 중복 신청 불가 (백엔드에서 409 반환)

### 유료 회차 접근 조건
1. 로그인 여부 확인
2. 해당 회차 구매 여부 확인
3. 구독 중이면 무료 열람 가능 (백엔드 정책에 따름)

### 성인 콘텐츠
- `isAdult: true` 인 소설/회차는 성인 인증 완료 사용자만 열람
- 미인증 시 성인 인증 안내 페이지 표시
