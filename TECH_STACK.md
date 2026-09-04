# Hot6-NovelCraft 기술 스택 및 인프라 문서

---

## 1. 프로젝트 개요

**NovelCraft**는 작가와 독자를 연결하는 소설 연재 플랫폼입니다.

### 핵심 기능
| 기능 | 설명 |
|------|------|
| 소설 연재 | 작가가 소설·회차를 연재하고 독자가 구독/구매 |
| 결제 시스템 | PortOne 기반 카드·계좌이체 결제 처리 |
| 포인트·구독 | 포인트 충전 및 BASIC / PREMIUM / VIP 구독 플랜 |
| 환전 시스템 | 작가 수익을 은행계좌로 출금 요청 |
| 멘토링 | 경험 작가의 신인 작가 지도 및 피드백 |
| 실시간 채팅 | WebSocket / STOMP 기반 1:1 채팅 |
| AI 커버 생성 | Google Gemini API로 소설 커버 이미지 자동 생성 |
| 알림 | Kafka 기반 비동기 실시간 알림 |
| 이벤트 | 플랫폼 이벤트 참여 (선착순 포함) |
| 검색 | 소설·작가 통합 검색 |
| 국립도서관 연동 | 국립도서관 OpenAPI를 통한 도서 데이터 활용 |
| 모니터링 | Prometheus + Grafana 기반 운영 대시보드 |

### 규모
- **Java 파일**: 449개
- **도메인**: 23개 (user, novel, episode, payment, subscription, point, notification, exchange, mentor, mentoring, admin, event, report, chatroom, coverai, file, calendar, library, search, nationallibrary 등)
- **Entity**: 80개 / **Controller**: 48개 / **Service**: 53개

---

## 2. 기술 스택

### 2-1. Backend Core
| 기술 | 버전 | 용도 |
|------|------|------|
| Java | 17 | 언어 |
| Spring Boot | 3.3.5 | 웹 애플리케이션 프레임워크 |
| Spring Web MVC | (Boot 포함) | REST API |
| Spring Security | (Boot 포함) | 인증·인가 |
| Spring Data JPA | (Boot 포함) | ORM |
| Hibernate | (Boot 포함) | JPA 구현체 |
| Querydsl | 5.0.0 | 타입 안전 동적 쿼리 |
| Gradle | - | 빌드 도구 |

### 2-2. Database
| 기술 | 버전 | 용도 |
|------|------|------|
| MySQL | 8.x | 메인 RDBMS |
| Redis | 7.x | 캐싱 / 세션 / 분산 락 |
| Redisson | 3.27.2 | Redis 분산 락 / 고급 자료구조 |
| Lettuce | (Boot 포함) | Redis 연결 풀 |

### 2-3. Messaging & Streaming
| 기술 | 버전 | 용도 |
|------|------|------|
| Apache Kafka | 3.x (KRaft) | 비동기 이벤트 스트리밍 |
| Spring Kafka | (Boot 포함) | Kafka 프로듀서/컨슈머 |
| WebSocket + STOMP | (Boot 포함) | 실시간 채팅 |

### 2-4. AI / 외부 서비스
| 기술 | 버전 | 용도 |
|------|------|------|
| Google Gemini SDK | 1.0.0 | AI 소설 커버 이미지 생성 |
| OpenAI Java SDK | 2.1.0 | GPT 연동 |
| PortOne SDK | 0.23.0 | 결제 게이트웨이 |
| CoolSMS SDK | 4.3.0 | SMS 발송 |
| 국립도서관 OpenAPI | - | 도서 데이터 |

### 2-5. Cloud (AWS)
| 서비스 | 용도 |
|--------|------|
| AWS S3 | 이미지·파일 스토리지 (버킷: hot6-novelcraft-minwoo, 리전: ap-northeast-2) |
| AWS EC2 | 애플리케이션 서버 배포 |
| AWS RDS | 프로덕션 MySQL |
| AWS ElastiCache | 프로덕션 Redis |
| AWS ALB | 로드 밸런서 |

- **Spring Cloud AWS**: 3.1.1

### 2-6. 인증 / 보안
| 기술 | 버전 | 용도 |
|------|------|------|
| JWT (jjwt) | 0.12.6 | 토큰 기반 인증 |
| OAuth2 Client | (Boot 포함) | Google / Kakao 소셜 로그인 |
| AES 암호화 | - | 민감 정보 암호화 |
| Token Blacklist | Redis | 로그아웃 토큰 무효화 |

### 2-7. 모니터링 / 관측성
| 기술 | 용도 |
|------|------|
| Micrometer Prometheus | 메트릭 수집 (`/actuator/prometheus`) |
| Spring Actuator | 헬스체크 / 메트릭 엔드포인트 |
| Prometheus | 메트릭 저장 및 스크래핑 (5초 간격) |
| Grafana | 시각화 대시보드 |
| Kafka JMX Exporter | Kafka 브로커 메트릭 → Prometheus 변환 |
| Kafka UI | Kafka 클러스터 시각화 |

### 2-8. 성능 테스트
| 기술 | 용도 |
|------|------|
| k6 | 부하 테스트 (이벤트 참여, 출금, 캐시 전략, 카오스 등) |

### 2-9. 기타 라이브러리
| 기술 | 버전 | 용도 |
|------|------|------|
| Lombok | - | 보일러플레이트 코드 제거 |
| Jackson | (Boot 포함) | JSON 직렬화/역직렬화 |
| Jakarta Validation | (Boot 포함) | 요청 유효성 검사 |
| Apache HttpComponents | 5.x | HTTP 클라이언트 |
| CodeRabbit | - | AI PR 코드리뷰 |

---

## 3. 인프라 구조

```
┌─────────────────────────────────────────────────────────────────────────┐
│                           클라이언트 (Browser / App)                     │
└───────────────────────────────────┬─────────────────────────────────────┘
                                    │ HTTPS
                          ┌─────────▼─────────┐
                          │    AWS ALB          │
                          │  (Load Balancer)    │
                          └─────────┬───────────┘
                                    │
                          ┌─────────▼───────────┐
                          │   AWS EC2            │
                          │ Spring Boot :8080    │◄──── Dockerfile
                          │  (NovelCraft App)   │◄──── Jenkins CI/CD
                          └──┬──┬──┬──┬──┬──┬──┘
                             │  │  │  │  │  │
          ┌──────────────────┘  │  │  │  │  └────────────────────┐
          │                     │  │  │  │                        │
          ▼                     │  │  │  ▼                        ▼
  ┌───────────────┐             │  │  │  ┌──────────────┐  ┌───────────────┐
  │  AWS RDS      │             │  │  │  │  AWS S3      │  │  PortOne      │
  │  MySQL :3306  │             │  │  │  │  (이미지/파일) │  │  결제 게이트웨이│
  │  hot6_db      │             │  │  │  └──────────────┘  └───────────────┘
  └───────────────┘             │  │  │
                                │  │  └──────────────────────────┐
          ┌─────────────────────┘  │                             │
          │                        │                             │
          ▼                        ▼                             ▼
  ┌────────────────────────┐  ┌──────────────┐       ┌─────────────────────┐
  │   Redis (Sentinel HA)  │  │ Kafka Cluster│       │  외부 API            │
  │                        │  │  (3 Brokers) │       │  Google Gemini       │
  │  Master :6379          │  │              │       │  OpenAI GPT          │
  │  Slave1 :6380          │  │  kafka-1:9092│       │  CoolSMS             │
  │  Slave2 :6381          │  │  kafka-2:9093│       │  국립도서관 API       │
  │                        │  │  kafka-3:9094│       │  Google/Kakao OAuth2 │
  │  Sentinel1 :26379      │  │              │       └─────────────────────┘
  │  Sentinel2 :26380      │  │  KRaft 모드  │
  │  Sentinel3 :26381      │  │  RF=3, ISR=2 │
  └────────────────────────┘  └──────┬───────┘
                                     │ JMX Metrics
                    ┌────────────────┬┴──────────────────┐
                    ▼                ▼                    ▼
           ┌──────────────┐ ┌──────────────┐  ┌──────────────┐
           │ JMX Exporter │ │ JMX Exporter │  │ JMX Exporter │
           │  kafka-1:5556│ │  kafka-2:5557│  │  kafka-3:5558│
           └──────┬───────┘ └──────┬───────┘  └──────┬───────┘
                  └────────────────┼──────────────────┘
                                   │
                    ┌──────────────▼──────────────┐
                    │        Prometheus :9095       │
                    │  scrape interval: 5s          │
                    │  ┌──────────────────────────┐ │
                    │  │ Jobs:                    │ │
                    │  │  - novelcraft (:8080)    │ │
                    │  │  - kafka-1-jmx (:5556)   │ │
                    │  │  - kafka-2-jmx (:5557)   │ │
                    │  │  - kafka-3-jmx (:5558)   │ │
                    │  └──────────────────────────┘ │
                    └──────────────┬────────────────┘
                                   │
                    ┌──────────────▼──────────────┐
                    │        Grafana :3000          │
                    │   대시보드 / 알림 / 시각화     │
                    └─────────────────────────────┘

  ┌───────────────────────────────┐
  │         Jenkins               │
  │  CI/CD 파이프라인             │
  │  - 빌드 → 테스트 → 배포       │
  │  - AWS EC2 배포 자동화        │
  └───────────────────────────────┘

  ┌───────────────────────────────┐
  │      Kafka UI :8088           │
  │  Kafka 클러스터 모니터링 UI   │
  └───────────────────────────────┘
```

### 네트워크 구성 (Docker)
| 네트워크 | 대상 컨테이너 |
|----------|--------------|
| `kafka-net` | kafka-1, kafka-2, kafka-3, jmx-exporter ×3, kafka-ui, prometheus |
| `redis-net` | redis-master, redis-slave-1, redis-slave-2, sentinel ×3 |

### 포트 맵
| 서비스 | 포트 | 설명 |
|--------|------|------|
| Spring Boot App | 8080 | REST API / WebSocket |
| MySQL | 3306 | 메인 DB |
| Redis Master | 6379 | 캐싱 / 세션 |
| Redis Slave 1 | 6380 | 레플리카 |
| Redis Slave 2 | 6381 | 레플리카 |
| Redis Sentinel 1 | 26379 | 페일오버 감지 |
| Redis Sentinel 2 | 26380 | 페일오버 감지 |
| Redis Sentinel 3 | 26381 | 페일오버 감지 |
| Kafka Broker 1 | 9092 | 외부 클라이언트 |
| Kafka Broker 2 | 9093 | 외부 클라이언트 |
| Kafka Broker 3 | 9094 | 외부 클라이언트 |
| Kafka JMX Exporter 1 | 5556 | Prometheus 스크래핑 |
| Kafka JMX Exporter 2 | 5557 | Prometheus 스크래핑 |
| Kafka JMX Exporter 3 | 5558 | Prometheus 스크래핑 |
| Kafka UI | 8088 | 클러스터 대시보드 |
| Prometheus | 9095 | 메트릭 수집 |
| Grafana | 3000 | 시각화 대시보드 |

---

## 4. Kafka 토픽 구성

| 토픽 | 용도 |
|------|------|
| `novel-notification-events` | 팔로우·댓글·좋아요 등 알림 이벤트 |
| `cover-generation-events` | AI 커버 이미지 비동기 생성 요청 |

- **Replication Factor**: 3
- **Min ISR**: 2
- **Consumer Group**: `notification-service`

---

## 5. Redis 구성 상세

```
           ┌─────────────────┐
           │   Redis Master  │  :6379  (AOF 활성화)
           └───────┬─────────┘
         ┌─────────┴──────────┐
         ▼                    ▼
  ┌────────────┐      ┌────────────┐
  │Redis Slave1│      │Redis Slave2│
  │   :6380    │      │   :6381    │
  └────────────┘      └────────────┘

  ┌────────────┐ ┌────────────┐ ┌────────────┐
  │ Sentinel 1 │ │ Sentinel 2 │ │ Sentinel 3 │
  │  :26379    │ │  :26380    │ │  :26381    │
  └────────────┘ └────────────┘ └────────────┘
  (quorum=2, master 장애 시 자동 페일오버)
```

**연결 풀 설정 (Lettuce)**
- 최대 동시 연결: 10
- 최대 유휴 연결: 5
- 최소 유지 연결: 2
- 대기 타임아웃: 2000ms

---

## 6. CI/CD 파이프라인

```
개발자 Push
    │
    ▼
  Jenkins
    ├─ 1. 소스 체크아웃
    ├─ 2. Gradle Build
    ├─ 3. 단위 테스트
    ├─ 4. Docker 이미지 빌드
    └─ 5. AWS EC2 배포
```

---

## 7. 보안 아키텍처

| 항목 | 구현 |
|------|------|
| 인증 방식 | JWT (jjwt 0.12.6) + OAuth2 (Google / Kakao) |
| 토큰 저장 | Redis 블랙리스트로 로그아웃 토큰 무효화 |
| 데이터 암호화 | AES-128 (key: 16자, iv: 16자) |
| 성인 인증 | 회원가입 시 본인 인증 |
| WebSocket 보안 | StompChannelInterceptor로 JWT 검증 |
| 소프트 딜리트 | 논리적 삭제 (isDeleted 플래그) |

---

## 8. 고가용성 설계 요약

| 컴포넌트 | HA 전략 |
|---------|---------|
| Redis | Sentinel 3노드 + Slave 2노드 자동 페일오버 |
| Kafka | 3-Broker KRaft 클러스터, RF=3, Min ISR=2 |
| DB | AWS RDS Multi-AZ (프로덕션) |
| 분산 락 | Redisson으로 동시성 제어 |
| 캐싱 | Redis 캐시 + 로컬 캐시 전략 |
