# Step 19: 부하 테스트 계획 및 시나리오 설계

## 목차

1. [테스트 목적 및 배경](#1-테스트-목적-및-배경)
2. [테스트 대상 API 선정](#2-테스트-대상-api-선정)
3. [부하 테스트 시나리오 설계](#3-부하-테스트-시나리오-설계)
4. [테스트 환경 구성](#4-테스트-환경-구성)
5. [성능 목표 지표](#5-성능-목표-지표)
6. [테스트 실행 계획](#6-테스트-실행-계획)

---

## 1. 테스트 목적 및 배경

### 1.1 테스트 목적

이커머스 시스템의 핵심 비즈니스 로직에 대한 **성능 한계점 파악** 및 **병목 지점 탐색**을 통해:

1. **현재 시스템의 처리 용량(Capacity) 파악**
2. **예상 트래픽 대비 안정성 검증**
3. **병목 구간 식별 및 개선 방향 도출**
4. **장애 발생 시나리오 시뮬레이션**

### 1.2 테스트 배경

**비즈니스 요구사항:**
- 선착순 쿠폰 발급: 1만 명 동시 접속 예상
- 상품 주문/결제: 평시 100 req/s, 피크 타임 500 req/s
- 인기 상품 조회: 10,000 req/s (캐싱 적용)

**현재 시스템 구성:**
- Redis 기반 분산락 (쿠폰 발급)
- Kafka 기반 비동기 처리 (주문 완료 이벤트)
- Redis Sorted Set 기반 실시간 랭킹

**검증 필요 사항:**
- 동시성 제어 방식의 성능 한계
- DB 커넥션 풀 적정성
- Redis 응답 시간
- Kafka Producer/Consumer 처리량

---

## 2. 테스트 대상 API 선정

### 2.1 선정 기준

| 기준 | 설명 |
|------|------|
| **비즈니스 중요도** | 매출/고객 만족도에 직접적 영향 |
| **트래픽 집중도** | 동시 접속자 수가 많은 API |
| **동시성 제어 필요성** | Race Condition, 재고 관리 등 |
| **외부 의존성** | Redis, Kafka, DB 등 SPOF 가능성 |

### 2.2 선정된 테스트 대상 API

#### 🎯 Primary Targets (핵심 테스트)

| API | HTTP Method | Endpoint | 중요도 | 예상 병목 |
|-----|------------|----------|-------|----------|
| **쿠폰 발급** | POST | `/api/coupons/{id}/issue` | ⭐⭐⭐⭐⭐ | Redis 분산락, DB 동시성 |
| **주문 결제** | POST | `/api/orders/{id}/payment` | ⭐⭐⭐⭐⭐ | 재고 차감, 낙관적 락 |
| **상품 목록 조회** | GET | `/api/products` | ⭐⭐⭐⭐ | DB 조회 성능 |
| **인기 상품 조회** | GET | `/api/products/popular` | ⭐⭐⭐⭐ | Redis 캐시 성능 |

#### 📊 Secondary Targets (추가 모니터링)

| API | HTTP Method | Endpoint | 목적 |
|-----|------------|----------|------|
| 장바구니 담기 | POST | `/api/carts/items` | 세션 관리 성능 |
| 주문 생성 | POST | `/api/orders` | 트랜잭션 처리 성능 |
| 상품 상세 조회 | GET | `/api/products/{id}` | 단건 조회 성능 |

### 2.3 선정 사유

#### 1) 쿠폰 발급 API

**선정 이유:**
- 선착순 이벤트 시 **1만 명 동시 접속** 예상
- Redis 분산락 기반 동시성 제어
- DB 수량 검증 및 업데이트

**예상 병목:**
- Redis 분산락 획득 지연 (50ms 폴링)
- DB 커넥션 고갈
- 낙관적 락 충돌률 증가

**테스트 목표:**
- 최대 처리 가능한 동시 요청 수 파악
- 분산락 대기 시간 측정
- 실패율(쿠폰 소진, 타임아웃) 분석

#### 2) 주문 결제 API

**선정 이유:**
- 재고 차감, 포인트 차감, 주문 상태 변경 등 **복잡한 트랜잭션**
- 낙관적 락 기반 재고 관리
- Kafka 이벤트 발행 (Outbox 패턴)

**예상 병목:**
- 낙관적 락 충돌로 인한 재시도
- DB 트랜잭션 대기 시간
- Outbox 테이블 삽입 성능

**테스트 목표:**
- 초당 처리 가능한 주문 수 (TPS) 파악
- 재고 부족 시 실패 처리 성능
- 응답 시간 p95, p99 측정

#### 3) 인기 상품 조회 API

**선정 이유:**
- Redis Sorted Set 기반 실시간 랭킹
- **높은 조회 트래픽** (10,000 req/s 예상)
- 캐싱 전략 검증

**예상 병목:**
- Redis 응답 시간
- 네트워크 대역폭
- 애플리케이션 스레드 풀

**테스트 목표:**
- Redis 캐시 성능 한계 파악
- 캐시 미스 시 DB 부하 측정
- 최대 처리 가능한 RPS 파악

---

## 3. 부하 테스트 시나리오 설계

### 3.1 테스트 유형

| 테스트 유형 | 설명 | 목적 |
|-----------|------|------|
| **Load Test** | 예상 트래픽을 가하여 안정성 검증 | 정상 동작 확인 |
| **Stress Test** | 시스템 한계까지 부하 증가 | 최대 처리량 파악 |
| **Spike Test** | 급격한 트래픽 증가 시뮬레이션 | 순간 부하 대응력 검증 |
| **Soak Test** | 장시간 일정 부하 유지 | 메모리 누수, 리소스 고갈 확인 |

### 3.2 시나리오별 설계

#### 시나리오 1: 선착순 쿠폰 발급 (Spike Test)

**목적:** 1만 명이 동시에 100개 한정 쿠폰 발급 시도

**부하 패턴:**
```
사용자 수: 0 → 10,000 (10초 내 급증)
유지 시간: 30초
총 실행 시간: 1분
```

**k6 시나리오:**
```javascript
export let options = {
  scenarios: {
    spike_coupon_issue: {
      executor: 'ramping-vus',
      startVUs: 0,
      stages: [
        { duration: '10s', target: 10000 },  // 0 → 10,000명 (급증)
        { duration: '30s', target: 10000 },  // 10,000명 유지
        { duration: '10s', target: 0 },      // 종료
      ],
    },
  },
  thresholds: {
    'http_req_duration': ['p95<500', 'p99<1000'],  // 응답시간
    'http_req_failed': ['rate<0.1'],                // 실패율 10% 이하
  },
};
```

**예상 결과:**
- 성공: 100건 (쿠폰 발급)
- 실패: 9,900건 (쿠폰 소진)
- 응답 시간: p95 < 500ms, p99 < 1000ms

**검증 지표:**
- 쿠폰 발급 정확성 (100건 정확히 발급)
- 중복 발급 없음
- 분산락 대기 시간
- DB 커넥션 풀 사용률

#### 시나리오 2: 주문 결제 동시 처리 (Stress Test)

**목적:** 재고 100개 상품에 대해 동시 주문 처리 한계 파악

**부하 패턴:**
```
사용자 수: 50 → 500 (단계적 증가)
각 단계: 1분씩 유지
총 실행 시간: 10분
```

**k6 시나리오:**
```javascript
export let options = {
  scenarios: {
    stress_order_payment: {
      executor: 'ramping-vus',
      startVUs: 50,
      stages: [
        { duration: '1m', target: 50 },   // 워밍업
        { duration: '2m', target: 100 },  // 100 VU
        { duration: '2m', target: 200 },  // 200 VU
        { duration: '2m', target: 300 },  // 300 VU
        { duration: '2m', target: 500 },  // 500 VU (한계 테스트)
        { duration: '1m', target: 0 },    // 종료
      ],
    },
  },
  thresholds: {
    'http_req_duration': ['p95<1000', 'p99<2000'],
    'http_req_failed': ['rate<0.05'],  // 실패율 5% 이하
    'http_reqs': ['rate>50'],           // 최소 50 TPS
  },
};
```

**예상 결과:**
- 목표 TPS: 100 req/s
- 응답 시간: p95 < 1000ms
- 재고 정확성: 100개 정확히 차감

**검증 지표:**
- 낙관적 락 충돌률
- 트랜잭션 롤백 횟수
- Outbox 이벤트 발행 성공률
- DB CPU/메모리 사용률

#### 시나리오 3: 인기 상품 조회 (Load Test)

**목적:** 10,000 req/s 트래픽 처리 가능 여부 검증

**부하 패턴:**
```
RPS: 1,000 → 10,000 (단계적 증가)
각 단계: 30초씩 유지
총 실행 시간: 5분
```

**k6 시나리오:**
```javascript
export let options = {
  scenarios: {
    load_popular_products: {
      executor: 'constant-arrival-rate',
      rate: 1000,          // 초기 1,000 RPS
      timeUnit: '1s',
      duration: '5m',
      preAllocatedVUs: 100,
      maxVUs: 1000,
    },
  },
  thresholds: {
    'http_req_duration': ['p95<100', 'p99<200'],  // Redis 캐시 성능
    'http_req_failed': ['rate<0.01'],              // 실패율 1% 이하
  },
};
```

**예상 결과:**
- 목표 RPS: 10,000 req/s
- 응답 시간: p95 < 100ms (캐시 히트)
- 캐시 적중률: > 95%

**검증 지표:**
- Redis 응답 시간
- 애플리케이션 CPU 사용률
- 네트워크 대역폭
- 캐시 미스율

#### 시나리오 4: 복합 사용자 플로우 (Soak Test)

**목적:** 실제 사용자 행동 패턴 시뮬레이션 (장시간 안정성 검증)

**사용자 플로우:**
```
1. 상품 목록 조회 (GET /api/products)
2. 상품 상세 조회 (GET /api/products/{id})
3. 장바구니 담기 (POST /api/carts/items)
4. 주문 생성 (POST /api/orders)
5. 주문 결제 (POST /api/orders/{id}/payment)
```

**부하 패턴:**
```
사용자 수: 100명 유지
Think Time: 1~3초 (랜덤)
총 실행 시간: 2시간
```

**k6 시나리오:**
```javascript
export let options = {
  scenarios: {
    soak_user_journey: {
      executor: 'constant-vus',
      vus: 100,
      duration: '2h',
    },
  },
  thresholds: {
    'http_req_duration': ['p95<1000'],
    'http_req_failed': ['rate<0.01'],
  },
};
```

**예상 결과:**
- 안정적인 메모리 사용량 (증가 없음)
- DB 커넥션 정상 반환
- 응답 시간 일정 유지

**검증 지표:**
- 메모리 누수 여부
- DB 커넥션 누수 여부
- GC 발생 빈도
- 애플리케이션 에러율

---

## 4. 테스트 환경 구성

### 4.1 서버 스펙

**애플리케이션 서버:**
- CPU: 4 Core
- Memory: 8GB
- JVM Heap: -Xms2g -Xmx4g
- Thread Pool: 200 (Tomcat default)

**데이터베이스 (MySQL):**
- CPU: 2 Core
- Memory: 4GB
- Connection Pool: 20 (HikariCP)

**Redis:**
- CPU: 1 Core
- Memory: 2GB
- Max Connections: 1000

**Kafka:**
- CPU: 2 Core
- Memory: 4GB
- Partitions: 3~5

### 4.2 k6 실행 환경

**부하 생성 서버:**
- k6 버전: v0.48.0
- OS: macOS / Linux
- 네트워크: 로컬 환경 (동일 네트워크)

**모니터링 도구:**
- k6 Cloud (실시간 대시보드)
- Prometheus + Grafana (서버 메트릭)
- MySQL Slow Query Log
- Redis Monitor

### 4.3 테스트 데이터 준비

**사전 데이터 셋업:**
```sql
-- 상품 1000개
INSERT INTO product (name, price, stock_qty, status) VALUES ...;

-- 쿠폰 10개 (각 100개 수량)
INSERT INTO coupon (code, total_qty, issued_qty, status) VALUES ...;

-- 사용자 10,000명
INSERT INTO user (name, balance) VALUES ...;
```

**Fixture 데이터 (k6):**
```javascript
// 랜덤 사용자 ID 생성
const userId = Math.floor(Math.random() * 10000) + 1;

// 랜덤 상품 ID 생성
const productId = Math.floor(Math.random() * 1000) + 1;

// 랜덤 수량 (1~5개)
const quantity = Math.floor(Math.random() * 5) + 1;
```

---

## 5. 성능 목표 지표

### 5.1 응답 시간 (Latency)

| API | p50 | p95 | p99 | 목표 |
|-----|-----|-----|-----|------|
| 쿠폰 발급 | 100ms | 500ms | 1000ms | ✅ 1초 이내 |
| 주문 결제 | 200ms | 1000ms | 2000ms | ✅ 2초 이내 |
| 상품 조회 (캐시) | 10ms | 50ms | 100ms | ✅ 100ms 이내 |
| 상품 조회 (DB) | 50ms | 200ms | 500ms | ✅ 500ms 이내 |

### 5.2 처리량 (Throughput)

| API | 목표 TPS | 최대 TPS | 비고 |
|-----|---------|---------|------|
| 쿠폰 발급 | 100 | 500 | 분산락 제한 |
| 주문 결제 | 100 | 300 | DB 트랜잭션 제한 |
| 상품 조회 | 1,000 | 10,000 | Redis 캐시 |

### 5.3 에러율 (Error Rate)

| 시나리오 | 허용 에러율 | 목표 |
|---------|-----------|------|
| 정상 부하 (Load Test) | < 1% | 안정성 |
| 한계 부하 (Stress Test) | < 5% | 회복력 |
| 순간 부하 (Spike Test) | < 10% | 탄력성 |

### 5.4 리소스 사용률

| 리소스 | 정상 | 경고 | 위험 |
|--------|------|------|------|
| CPU | < 70% | 70~85% | > 85% |
| Memory | < 70% | 70~85% | > 85% |
| DB Connections | < 80% | 80~95% | > 95% |
| Redis Memory | < 75% | 75~90% | > 90% |

---

## 6. 테스트 실행 계획

### 6.1 실행 순서

| 순서 | 테스트 | 소요 시간 | 목적 |
|------|--------|----------|------|
| 1 | **Smoke Test** | 5분 | 스크립트 검증 |
| 2 | **Load Test** (인기 상품 조회) | 10분 | 캐시 성능 |
| 3 | **Stress Test** (주문 결제) | 15분 | DB 성능 한계 |
| 4 | **Spike Test** (쿠폰 발급) | 5분 | 동시성 제어 |
| 5 | **Soak Test** (복합 플로우) | 2시간 | 장시간 안정성 |

### 6.2 사전 체크리스트

**테스트 전:**
- [ ] 데이터베이스 초기화 (테스트 데이터 셋업)
- [ ] Redis 캐시 초기화
- [ ] Kafka 토픽 생성 및 초기화
- [ ] 애플리케이션 재시작 (클린 상태)
- [ ] 모니터링 도구 실행 (Prometheus, Grafana)
- [ ] k6 스크립트 검증 (Smoke Test)

**테스트 중:**
- [ ] 실시간 메트릭 모니터링
- [ ] 로그 수집 (애플리케이션, DB, Redis)
- [ ] 에러 발생 시 즉시 기록

**테스트 후:**
- [ ] k6 결과 리포트 저장
- [ ] 서버 메트릭 스냅샷 저장
- [ ] 로그 파일 백업
- [ ] 데이터 정합성 검증 (쿠폰 수량, 재고 수량)

### 6.3 데이터 수집 항목

**k6 메트릭:**
- `http_req_duration`: 응답 시간 (p50, p95, p99)
- `http_reqs`: 초당 요청 수 (RPS)
- `http_req_failed`: 실패율 (%)
- `vus`: 가상 사용자 수
- `iterations`: 총 반복 횟수

**서버 메트릭:**
- CPU 사용률 (%)
- 메모리 사용률 (%)
- GC 발생 횟수 및 시간
- Thread Pool 사용률
- DB Connection Pool 사용률

**애플리케이션 메트릭:**
- API별 응답 시간
- 에러 발생 건수
- Redis 응답 시간
- Kafka 메시지 발행/소비 속도

---

## 7. 예상 병목 지점 및 가설

### 7.1 병목 가설

| 구간 | 예상 병목 | 근거 | 임계치 |
|------|----------|------|--------|
| **쿠폰 발급** | Redis 분산락 | 50ms 폴링 대기 | 500 req/s |
| **주문 결제** | 낙관적 락 충돌 | 동시 재고 차감 | 300 req/s |
| **상품 조회** | DB 커넥션 풀 | 20개 제한 | 200 req/s |
| **전체** | DB CPU | 복잡한 JOIN 쿼리 | CPU 85% |

### 7.2 검증 방법

**분산락 성능:**
```
측정 항목: 락 획득 대기 시간
방법: Redis SETNX 응답 시간 측정
목표: 평균 < 50ms
```

**낙관적 락 충돌:**
```
측정 항목: ObjectOptimisticLockingFailureException 발생률
방법: 애플리케이션 로그 분석
목표: < 10%
```

**DB 커넥션 풀:**
```
측정 항목: HikariCP active/idle connections
방법: JMX 모니터링
목표: active < 80%
```

---

## 8. 성공 기준

### 8.1 기능적 성공 기준

- ✅ 쿠폰 발급: 100개 정확히 발급 (중복 없음)
- ✅ 재고 차감: 음수 재고 발생 없음
- ✅ 주문 처리: 데이터 정합성 유지

### 8.2 비기능적 성공 기준

- ✅ 응답 시간: p95 목표치 이내
- ✅ 처리량: 목표 TPS 달성
- ✅ 에러율: 허용 범위 이내
- ✅ 리소스: CPU/메모리 안정적

### 8.3 개선 필요 기준

- ⚠️ p95 > 목표치 200% 초과
- ⚠️ 에러율 > 10%
- ⚠️ CPU > 90%
- ⚠️ DB 커넥션 고갈

---

## 9. 다음 단계 (Step 20)

테스트 결과를 바탕으로:

1. **병목 구간 분석** (느린 쿼리, 리소스 고갈 지점)
2. **개선 방안 도출** (인덱스 추가, 커넥션 풀 증설, 캐싱 등)
3. **장애 시나리오 작성** (DB 장애, Redis 장애, Kafka 장애)
4. **장애 대응 문서 작성** (감지, 대응, 복구, 회고)

---

**작성일**: 2025-12-25
**작성자**: Step 19 부하 테스트팀