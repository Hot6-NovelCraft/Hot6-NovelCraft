# NovelCraft 대규모 트래픽 테스트 가이드

## 테스트 목적 (포트폴리오 관점)

> "시스템 한계를 수치로 정의하고, 병목을 코드 레벨에서 개선한 백엔드 엔지니어"

---

## SLO 정의 (역산 기준)

| 항목 | 목표 | 근거 |
|------|------|------|
| 소설 목록/상세 P95 | < 500ms | 독자 UX 이탈 임계점 |
| 회차 본문 P95 | < 1000ms | 본문 크기 감안 |
| 에러율 | < 1% | 일반 API 기준 |
| 가용성 | 99.9% | 월 43분 다운 허용 |
| Spike 에러율 | < 5% | 순간 폭발 허용 기준 |

---

## 실행 순서

### 사전 준비
```bash
# 테스트 계정 생성 확인 (reader1@test.com ~ reader620@test.com)
# 소설/회차 데이터 존재 확인 (NOVEL_IDS, EP_IDS)
```

### Phase 1: Baseline (정상 수치 확보)
```bash
k6 run load-test-baseline.js \
  -e BASE_URL=http://novelcraft-dev-alb-336387969.ap-northeast-2.elb.amazonaws.com \
  -e NOVEL_IDS=1,2,3,4,5 \
  -e EP_IDS=1,2,3,4,5
```
- VU: 20 / Duration: 3분
- 목적: SLO 비교 기준선 확보

### Phase 2: Stress Test (Breaking Point 탐색)
```bash
k6 run load-test-stress.js \
  -e BASE_URL=http://novelcraft-dev-alb-336387969.ap-northeast-2.elb.amazonaws.com \
  -e NOVEL_IDS=1,2,3,4,5 \
  -e EP_IDS=1,2,3,4,5
```
- VU: 20→50→100→200→300→500 단계적 증가
- 총 소요: 약 27분
- 목적: SLO 기준(P95<500ms)이 깨지는 VU 구간 = Breaking Point

### Phase 3: Spike Test (신작 출시 시나리오)
```bash
k6 run load-test-spike.js \
  -e BASE_URL=http://novelcraft-dev-alb-336387969.ap-northeast-2.elb.amazonaws.com \
  -e NOVEL_ID=1 \
  -e NEW_EP_ID=10 \
  -e NOVEL_IDS=1,2,3,4,5
```
- 평상시 30VU → 500VU 30초 내 급증
- 총 소요: 약 13분
- 목적: 캐시 효과 검증 (동일 novelId 집중 조회)

---

## CloudWatch 모니터링 포인트

Stress Test 실행 중 AWS CloudWatch에서 확인:

| 지표 | 임계값 | Breaking Point 신호 |
|------|--------|---------------------|
| EC2 CPU | > 80% | App 서버 병목 |
| RDS CPU | > 80% | DB 병목 |
| RDS DB Connections | > 80% of max | 커넥션 고갈 |
| Redis CPU | > 70% | 캐시 서버 병목 |
| ALB 5xx Error Rate | > 1% | 서비스 장애 시작 |
| ALB Target Response Time | > 500ms | SLO 위반 |

---

## 예상 병목 및 개선 방향

| 병목 위치 | 증상 | 개선 방법 |
|-----------|------|-----------|
| 소설 목록 API | RDS CPU 급증 | 복합 인덱스 + Redis 캐싱 |
| 회차 본문 API | 응답시간 폭발 | 본문 캐싱 (내용 변경 없음) |
| 랭킹 API | 반복 집계 쿼리 | Redis Sorted Set 캐시 |
| 회차 목록 API | N+1 쿼리 | v2 API (fetch join) 사용 |

---

## 포트폴리오 스토리 구성

```
1. SLO 설정 (왜 이 수치인지 근거 제시)
          ↓
2. Baseline 측정 (정상 수치)
          ↓
3. Stress Test → Breaking Point 발견
   예: "200 VU에서 소설 목록 API P95 1800ms → SLO 위반"
          ↓
4. 원인 분석
   예: "EXPLAIN 분석 → genre+status 복합 풀스캔 확인"
          ↓
5. 개선 적용
   예: "복합 인덱스 추가 + Redis TTL 캐싱 적용"
          ↓
6. 재측정 → 개선 전후 비교
   예: "P95 1800ms → 180ms (90% 개선), Breaking Point 200→500 VU"
          ↓
7. Spike Test → 신작 출시 시나리오 검증
```