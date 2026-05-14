# NovelCraft 프론트엔드 사용 가이드

## 📁 파일 구성

```
frontend/
├── common.js          # 공통 유틸 (Auth, api, Toast, Modal, 네비게이션, 공통 CSS)
├── index.html         # 메인 페이지 (AI추천, 랭킹, 신작)
├── login.html         # 로그인 (계정 복구 포함)
├── signup.html        # 회원가입 3단계 (독자/작가 분기)
├── novel-list.html    # 소설 목록 (검색/필터/태그)
├── novel-detail.html  # 소설 상세 + 회차 목록 + 구매
├── episode-read.html  # 회차 읽기 (리더 + 댓글 + 좋아요)
├── editor.html        # 작가 에디터 (소설/회차/설정집/AI리뷰/AI표지)
├── mypage.html        # 마이페이지 (프로필/서재/알림/결제/구독/AI채팅/수익)
├── mentor-list.html   # 멘토 찾기 + 멘토링 신청 + 내 이력
├── mentor-dashboard.html # 멘토 대시보드 (멘티관리/신청접수/피드백)
└── chat.html          # 1:1 채팅 (WebSocket)
```

---

## 🚀 사용 방법 (Spring Boot와 연결)

### 방법 1: Spring Boot static 폴더에 넣기 (가장 간단)

```
src/main/resources/static/
├── common.js
├── index.html
├── login.html
└── ... (나머지 파일 전부)
```

그러면 서버 실행 후 `http://localhost:8080/index.html` 바로 접속 가능!

---

### 방법 2: 개발용 — 로컬에서 파일 직접 열기 + 프록시

**① VS Code Live Server 사용**

1. VS Code에서 `frontend/` 폴더 열기
2. Extensions → **Live Server** 설치
3. `index.html` 우클릭 → **Open with Live Server**
4. 브라우저에서 `http://localhost:5500/index.html` 접속

단, API 요청이 `/api/...`로 가므로 CORS 또는 프록시 설정 필요 (아래 참고)

---

### 방법 3: Spring Boot SecurityConfig CORS 설정

Spring Boot `application.yml` 또는 SecurityConfig에서 허용:

```yaml
# application.yml
spring:
  web:
    cors:
      allowed-origins: "http://localhost:5500"
      allowed-methods: "*"
      allowed-headers: "*"
```

또는 SecurityConfig에서:
```java
@Bean
public CorsConfigurationSource corsConfigurationSource() {
    CorsConfiguration config = new CorsConfiguration();
    config.addAllowedOrigin("http://localhost:5500");
    config.addAllowedMethod("*");
    config.addAllowedHeader("*");
    config.setAllowCredentials(true);
    UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/**", config);
    return source;
}
```

---

### 방법 4: ★ 권장 — static 폴더 + API 포트 일치

**Spring Boot 서버가 8080이라면:**

1. `frontend/` 폴더 내 파일들을 전부 `src/main/resources/static/`에 복사
2. `common.js`에서 `API_BASE` 확인:

```js
// common.js 상단
const API_BASE = '/api';  // 이미 /api 로 설정되어 있음 ✅
```

3. 서버 실행 후 바로 접속:
   - `http://localhost:8080/` 또는
   - `http://localhost:8080/index.html`

---

## ⚙️ common.js API_BASE 설정

서버 포트가 다르다면 `common.js` 상단 수정:

```js
// 같은 서버 (static 폴더) → 기본값 유지
const API_BASE = '/api';

// 별도 서버에서 개발할 경우
const API_BASE = 'http://localhost:8080/api';
```

---

## 🔗 페이지 흐름

```
index.html
  ├─ 로그인 필요 → login.html
  │     └─ 회원가입 → signup.html
  ├─ 소설 목록 → novel-list.html
  │     └─ 소설 상세 → novel-detail.html
  │           └─ 회차 읽기 → episode-read.html
  ├─ 마이페이지 → mypage.html
  ├─ 작가 에디터 → editor.html (AUTHOR/MENTOR 전용)
  ├─ 멘토 찾기 → mentor-list.html
  │     └─ 채팅 → chat.html
  └─ 멘토 대시보드 → mentor-dashboard.html (MENTOR 전용)
```

---

## 🛠️ WebSocket 설정 (채팅)

`chat.html`에서 WebSocket URL은 자동으로:
```
ws://현재_호스트/ws/chat?token=JWT토큰
```

Spring Boot WebSocket 설정 확인:
```java
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {
    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws/chat").setAllowedOriginPatterns("*").withSockJS();
    }
}
```

---

## ✅ 체크리스트

- [ ] `src/main/resources/static/`에 파일 복사
- [ ] Spring Boot 서버 실행 (`./gradlew bootRun` 또는 IDE)
- [ ] `http://localhost:8080/index.html` 접속
- [ ] 회원가입 or 로그인
- [ ] 소설 목록 → 소설 상세 → 회차 읽기 테스트
- [ ] 작가 계정으로 에디터 테스트
- [ ] 멘토 신청 → 채팅 테스트
