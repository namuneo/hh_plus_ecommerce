# Step 20: 장애 대응 문서 (Incident Response Document)

> **문서 버전:** 1.0
> **작성일:** 2025-12-25
> **작성자:** 김성준
> **검토자:** -
> **승인자:** -

---

## 📋 목차

1. [장애 대응 개요](#1-장애-대응-개요)
2. [장애 레벨 정의](#2-장애-레벨-정의)
3. [장애 시나리오별 대응](#3-장애-시나리오별-대응)
4. [SLO/SLA 및 성능 지표](#4-slosla-및-성능-지표)
5. [모니터링 및 Alert 설정](#5-모니터링-및-alert-설정)
6. [장애 대응 프로세스](#6-장애-대응-프로세스)
7. [Runbook (실행 절차서)](#7-runbook-실행-절차서)
8. [사후 분석 (Post-Mortem)](#8-사후-분석-post-mortem)

---

## 1. 장애 대응 개요

### 1.1 목적

본 문서는 e-commerce 시스템에서 발생 가능한 장애 상황에 대한 감지, 대응, 복구 절차를 정의합니다.

**핵심 목표:**
- **MTTD (Mean Time To Detect)**: < 5분
- **MTTR (Mean Time To Repair)**: < 30분
- **MTBF (Mean Time Between Failures)**: > 30일

### 1.2 대응 전략

| 기간 | 전략 | 목표 |
|------|------|------|
| **Short-term** (즉시) | 긴급 복구, 서비스 정상화 | MTTR < 30분 |
| **Mid-term** (1-2주) | 근본 원인 해결, 재발 방지 | 동일 장애 0건 |
| **Long-term** (1-3개월) | 아키텍처 개선, 자동화 | MTBF > 90일 |

### 1.3 책임자 및 연락처

| 역할 | 이름 | 연락처 | 책임 범위 |
|------|------|--------|----------|
| **On-Call Engineer** | TBD | +82-10-XXXX-XXXX | 1차 장애 대응 |
| **Backend Lead** | TBD | +82-10-XXXX-XXXX | 기술적 의사결정 |
| **DevOps Lead** | TBD | +82-10-XXXX-XXXX | 인프라 복구 |
| **Product Owner** | TBD | +82-10-XXXX-XXXX | 비즈니스 영향 판단 |

---

## 2. 장애 레벨 정의

### 2.1 장애 레벨 기준

| 레벨 | 정의 | 비즈니스 임팩트 | 대응 시간 | Alert |
|------|------|----------------|----------|-------|
| **P0 (Critical)** | 전체 서비스 중단 | 매출 손실, 고객 이탈 | 즉시 (5분) | Slack + SMS |
| **P1 (High)** | 핵심 기능 장애 | 일부 매출 손실 | 30분 이내 | Slack + Email |
| **P2 (Medium)** | 성능 저하 | UX 저하 | 2시간 이내 | Slack |
| **P3 (Low)** | 비핵심 기능 장애 | 최소 영향 | 1일 이내 | Slack (저우선순위) |

### 2.2 핵심 기능별 레벨 매핑

| 기능 | 정상 상태 | P2 (성능 저하) | P1 (기능 장애) | P0 (서비스 중단) |
|------|----------|---------------|---------------|-----------------|
| **쿠폰 발급** | p95 < 500ms | p95 > 1s | 에러율 > 10% | 에러율 > 50% |
| **주문 결제** | p95 < 1s | p95 > 2s | 에러율 > 5% | 에러율 > 20% |
| **상품 조회** | p95 < 100ms | p95 > 300ms | 에러율 > 5% | 에러율 > 50% |
| **대기열** | 순번 정확 | 지연 > 10s | 순번 오류 | 진입 불가 |

---

## 3. 장애 시나리오별 대응

### 3.1 장애 #1: 쿠폰 발급 API 응답 지연

#### 📌 장애 정의

**증상:**
- 쿠폰 발급 API p95 응답 시간 > 2초
- Timeout 에러 발생률 > 30%
- 사용자 불만 증가 (쿠폰 받기 실패)

**발생 조건:**
- 인기 쿠폰 오픈 시 대량 트래픽 (10,000+ req/s)
- 선착순 이벤트

**비즈니스 임팩트:**
- **레벨:** P1 (High)
- **영향:** 고객 불만, 브랜드 이미지 손상
- **추정 손실:** 쿠폰 이벤트 실패 → 잠재 고객 이탈

#### 🔍 근본 원인 분석 (Root Cause Analysis)

**Step 19 부하 테스트 결과 기반:**

```
Spike Test 결과:
- http_req_duration: avg=850ms, p(95)=1800ms, p(99)=2800ms
- timeout_errors: 35,000 / 100,000 (35%)

근본 원인:
1. Redis 분산락 폴링 오버헤드 (50ms 간격)
   - 10,000 VUs × 20회 폴링/req = 200,000 lock req/s
   - Redis CPU 80% 초과

2. 순차 처리 병목
   - Lock 획득 → 수량 검증 → 쿠폰 발급 → Lock 해제
   - 처리량: ~500 req/s (이론적 최대)
   - 10,000 req 처리 시간: 20초+

3. DB Connection Pool 고갈
   - HikariCP max: 20
   - 동시 요청 10,000 → 19,980개 대기 → Timeout
```

#### 🚨 감지 방법

**Alert 조건:**
```yaml
# Datadog / Prometheus Alert
- name: coupon_api_slow
  query: p95(http_req_duration{endpoint="/api/coupons/{id}/issue"}) > 2000ms
  duration: 3m
  severity: P1
  notify: slack-channel-oncall

- name: coupon_api_timeout
  query: rate(http_req_failed{endpoint="/api/coupons/{id}/issue"}) > 0.3
  duration: 2m
  severity: P1
  notify: slack-channel-oncall + email
```

**로그 패턴:**
```
[ERROR] RedisLockTimeoutException: Failed to acquire lock after 3000ms
[WARN] HikariCP: Connection pool exhausted, waiting for available connection
[ERROR] CouponService: Coupon issuance failed for user=12345, coupon=1
```

#### ⚡ 즉시 대응 (Short-term, <30분)

**목표:** 서비스 정상화, MTTR < 30분

**1. 긴급 조치 (0-5분)**

```bash
# Step 1: 트래픽 제한 (Rate Limiting)
# Nginx/API Gateway에서 쿠폰 API 요청률 제한
# /etc/nginx/nginx.conf
limit_req_zone $binary_remote_addr zone=coupon:10m rate=100r/s;

location /api/coupons {
    limit_req zone=coupon burst=200 nodelay;
}

# Nginx reload
sudo nginx -s reload

# Step 2: Connection Pool 긴급 증가
# application.yml 수정 (또는 환경변수)
kubectl set env deployment/ecommerce-api \
  SPRING_DATASOURCE_HIKARI_MAXIMUM_POOL_SIZE=50

# Pod 재시작
kubectl rollout restart deployment/ecommerce-api

# Step 3: 모니터링 확인
# 응답 시간 개선 여부 확인
curl http://monitoring.internal/api/metrics/coupon-api-p95
```

**2. 사용자 안내 (5-10분)**

```
# 공지사항 게시 (웹사이트, 앱 푸시)
제목: [긴급] 쿠폰 발급 지연 안내
내용: 현재 대량 트래픽으로 인해 쿠폰 발급이 지연되고 있습니다.
      잠시 후 다시 시도해 주시기 바랍니다.
      이벤트 기간은 연장됩니다. (종료 시각: 23:59 → 익일 12:00)
```

**3. 응급 복구 검증 (10-30분)**

```bash
# k6로 부하 테스트 재실행 (Spike Test)
k6 run --duration 1m --vus 1000 k6/scenarios/spike-test-coupon.js

# 목표:
# - p95 < 1000ms (임시 목표, 정상은 500ms)
# - timeout_rate < 10%

# 결과 확인
# ✅ p95: 950ms (개선됨)
# ✅ timeout_rate: 8% (개선됨)
```

#### 🔧 중기 대응 (Mid-term, 1-2주)

**목표:** 근본 원인 해결, 재발 방지

**1. Kafka 기반 비동기 처리 전환 (Step 18 구현 활용)**

```java
// CouponController.java
@PostMapping("/coupons/{couponId}/issue")
public ResponseEntity<CouponIssueResponse> issueCouponAsync(
        @PathVariable Long couponId,
        @RequestBody CouponIssueRequest request) {

    // 기존: 동기 처리 (850ms avg)
    // CouponUser couponUser = couponService.issueCoupon(couponId, userId);

    // 개선: Kafka 비동기 처리 (5ms avg)
    CouponIssueRequestEvent event = CouponIssueRequestEvent.create(
        couponId,
        request.getUserId()
    );

    kafkaProducerService.publishCouponIssueRequest(event);

    // 즉시 202 Accepted 응답
    return ResponseEntity.accepted()
        .body(CouponIssueResponse.of(
            event.getRequestId(),
            "PENDING",
            "쿠폰 발급 요청이 접수되었습니다. 잠시 후 결과를 확인해 주세요."
        ));
}
```

**2. Atomic DB Update 적용**

```java
// CouponJpaRepository.java (Step 18 구현 완료)
@Modifying
@Query("UPDATE Coupon c SET c.issuedQty = c.issuedQty + 1 " +
       "WHERE c.id = :couponId AND c.issuedQty < c.totalQty AND c.status = 'PUBLISHED'")
int incrementIssuedQty(@Param("couponId") Long couponId);

// CouponService.java
public CouponUser issueCoupon(Long couponId, Long userId) {
    // Atomic Update로 Race Condition 방지
    int updated = couponRepository.incrementIssuedQty(couponId);

    if (updated == 0) {
        throw new CouponSoldOutException("쿠폰이 모두 소진되었습니다.");
    }

    // 쿠폰 발급 이력 저장
    return couponUserRepository.save(CouponUser.of(couponId, userId));
}
```

**3. 개선 효과 벤치마크**

```
Before (Redis Lock):
- p95: 1800ms
- Throughput: 500 req/s
- Timeout: 35%

After (Kafka Async + Atomic Update):
- API Response p95: 10ms (99.4% 개선)
- Processing p95: 200ms (비동기)
- Throughput: 5000+ req/s (10배 증가)
- Timeout: 0.1%
```

#### 🏗️ 장기 대응 (Long-term, 1-3개월)

**목표:** 아키텍처 개선, Auto Scaling

**1. MSA 전환 (Coupon Service 분리)**

```
Before:
  Monolith API
  ├── Product
  ├── Order
  ├── Coupon  ← 트래픽 급증 시 전체 영향
  └── User

After:
  API Gateway
  ├── Product Service
  ├── Order Service
  ├── Coupon Service  ← 독립적 확장
  └── User Service

효과:
- Coupon Service만 HPA (Horizontal Pod Autoscaler)
- 다른 서비스 영향 없음
- 장애 격리 (Fault Isolation)
```

**2. Auto Scaling 설정 (K8s HPA)**

```yaml
# coupon-service-hpa.yaml
apiVersion: autoscaling/v2
kind: HorizontalPodAutoscaler
metadata:
  name: coupon-service-hpa
spec:
  scaleTargetRef:
    apiVersion: apps/v1
    kind: Deployment
    name: coupon-service
  minReplicas: 3
  maxReplicas: 50
  metrics:
  - type: Resource
    resource:
      name: cpu
      target:
        type: Utilization
        averageUtilization: 70
  - type: Pods
    pods:
      metric:
        name: http_request_rate
      target:
        type: AverageValue
        averageValue: "1000"  # 1000 req/s per pod
  behavior:
    scaleUp:
      stabilizationWindowSeconds: 0  # 즉시 확장
      policies:
      - type: Percent
        value: 100  # 2배씩 증가
        periodSeconds: 15
    scaleDown:
      stabilizationWindowSeconds: 300  # 5분 후 축소
```

**3. Circuit Breaker 패턴 (Resilience4j)**

```java
@Service
public class CouponService {

    @CircuitBreaker(name = "couponService", fallbackMethod = "fallbackIssueCoupon")
    @RateLimiter(name = "couponService")
    public CouponUser issueCoupon(Long couponId, Long userId) {
        // 쿠폰 발급 로직
    }

    // Fallback: 대기열 등록
    private CouponUser fallbackIssueCoupon(Long couponId, Long userId, Exception e) {
        // Circuit Open 시 대기열에 등록
        waitlistService.enqueue(couponId, userId);

        throw new ServiceUnavailableException(
            "현재 요청이 많아 대기열에 등록되었습니다. 순서가 되면 자동으로 발급됩니다."
        );
    }
}
```

#### 📊 재발 방지 체크리스트

- [x] Kafka 비동기 처리 전환
- [x] Atomic DB Update 적용
- [x] Connection Pool 증가 (20 → 50)
- [x] Rate Limiting 설정
- [ ] MSA 전환 (Coupon Service 분리)
- [ ] Auto Scaling (HPA) 설정
- [ ] Circuit Breaker 적용
- [ ] Load Test 자동화 (CI/CD)

---

### 3.2 장애 #2: DB Connection Pool 고갈

#### 📌 장애 정의

**증상:**
- `HikariPool: Connection is not available` 에러 로그 급증
- API 응답 시간 > 5초
- HTTP 503 Service Unavailable 에러

**발생 조건:**
- Stress Test 300 VUs 이상
- 주문 결제 요청 급증
- Slow Query 누적

**비즈니스 임팩트:**
- **레벨:** P0 (Critical)
- **영향:** 전체 서비스 중단 (주문, 조회, 결제 모두 불가)
- **추정 손실:** 분당 10,000원 이상 매출 손실

#### 🔍 근본 원인 분석

**Step 19 Stress Test 결과:**

```
Phase 3 (300 VUs):
  connection_pool_active: 19/20 (95%)
  connection_pool_wait_time: avg=80ms, p(95)=200ms

Phase 4 (500 VUs):
  connection_pool_active: 20/20 (100%) ← POOL EXHAUSTED
  connection_pool_wait_time: avg=500ms, p(95)=2000ms
  connection_timeout_errors: 800건

근본 원인:
1. HikariCP maximum-pool-size: 20 (부족)
2. Slow Query (>1s): 1,200건
   - JOIN 3개 (order, order_item, product)
   - INDEX 미사용
3. Connection Leak 의심
   - finally 블록 누락 코드 존재
```

#### 🚨 감지 방법

**Alert 조건:**
```yaml
- name: db_connection_pool_high
  query: hikaricp_connections_active / hikaricp_connections_max > 0.8
  duration: 2m
  severity: P2
  notify: slack-channel-oncall

- name: db_connection_pool_exhausted
  query: hikaricp_connections_pending > 10
  duration: 1m
  severity: P0
  notify: slack-channel-oncall + sms

- name: db_connection_timeout
  query: rate(hikaricp_connections_timeout_total) > 5
  duration: 1m
  severity: P0
  notify: slack-channel-oncall + sms
```

**로그 패턴:**
```
[ERROR] HikariPool-1: Connection is not available, request timed out after 30000ms
[WARN] HikariPool-1: Thread starvation or clock leap detected (housekeeper delta=2s100ms)
[ERROR] o.s.web.servlet.DispatcherServlet: Handler dispatch failed
java.sql.SQLTransientConnectionException: HikariPool-1 - Connection is not available
```

#### ⚡ 즉시 대응 (Short-term, <10분)

**1. Connection Pool 긴급 증가 (0-5분)**

```bash
# K8s 환경
kubectl set env deployment/ecommerce-api \
  SPRING_DATASOURCE_HIKARI_MAXIMUM_POOL_SIZE=50 \
  SPRING_DATASOURCE_HIKARI_MINIMUM_IDLE=10

# Pod 재시작 (Rolling Update)
kubectl rollout restart deployment/ecommerce-api
kubectl rollout status deployment/ecommerce-api

# 또는 ConfigMap 수정 후 재시작
kubectl edit configmap ecommerce-api-config
# maximum-pool-size: 50
# minimum-idle: 10

kubectl rollout restart deployment/ecommerce-api
```

**2. Slow Query 킬 (5-10분)**

```sql
-- MySQL에서 실행 중인 Slow Query 확인
SELECT
    ID,
    USER,
    HOST,
    DB,
    COMMAND,
    TIME,
    STATE,
    INFO
FROM information_schema.PROCESSLIST
WHERE TIME > 5  -- 5초 이상
  AND COMMAND != 'Sleep'
ORDER BY TIME DESC;

-- 장시간 실행 중인 쿼리 강제 종료
KILL QUERY 12345;  -- ID는 위에서 확인
KILL QUERY 12346;
KILL QUERY 12347;

-- 또는 스크립트로 일괄 Kill
SELECT CONCAT('KILL QUERY ', ID, ';')
FROM information_schema.PROCESSLIST
WHERE TIME > 10
  AND COMMAND != 'Sleep'
  AND USER = 'hhplus';
```

**3. 트래픽 제한 (동시)**

```bash
# Nginx Rate Limiting (주문 API)
limit_req_zone $binary_remote_addr zone=order:10m rate=50r/s;

location /api/orders {
    limit_req zone=order burst=100;
}

sudo nginx -s reload
```

#### 🔧 중기 대응 (Mid-term, 1주)

**1. Query 최적화**

```sql
-- Before: Slow Query (200-500ms)
SELECT
    o.id,
    o.user_id,
    o.total_amount,
    oi.product_id,
    oi.quantity,
    p.name
FROM `order` o
JOIN order_item oi ON o.id = oi.order_id
JOIN product p ON oi.product_id = p.id
WHERE o.user_id = ?
  AND o.status = 'PAID'
ORDER BY o.created_at DESC
LIMIT 20;

-- After: INDEX 추가 + Query 분리
-- 1) INDEX 추가
CREATE INDEX idx_order_user_status_created
ON `order`(user_id, status, created_at DESC);

CREATE INDEX idx_order_item_order_id
ON order_item(order_id);

-- 2) Query 분리 (JOIN 제거)
-- 주문 목록 조회
SELECT id, user_id, total_amount, created_at
FROM `order`
WHERE user_id = ? AND status = 'PAID'
ORDER BY created_at DESC
LIMIT 20;

-- 주문 상세 조회 (필요 시)
SELECT oi.product_id, oi.quantity, p.name
FROM order_item oi
JOIN product p ON oi.product_id = p.id
WHERE oi.order_id IN (?, ?, ...);

-- 개선 효과: 200ms → 20ms (90% 개선)
```

**2. Connection Leak 수정**

```java
// Bad: Connection Leak 위험
public void processOrder(Long orderId) {
    Connection conn = dataSource.getConnection();
    try {
        PreparedStatement stmt = conn.prepareStatement("...");
        // ... 비즈니스 로직

        if (someCondition) {
            return;  // ← conn.close() 누락!
        }

    } catch (SQLException e) {
        log.error("Error", e);
        return;  // ← conn.close() 누락!
    }
    conn.close();  // ← 도달 안 할 수도 있음
}

// Good: Try-with-resources
public void processOrder(Long orderId) {
    try (Connection conn = dataSource.getConnection();
         PreparedStatement stmt = conn.prepareStatement("...")) {

        // ... 비즈니스 로직

    } catch (SQLException e) {
        log.error("Error", e);
        throw new OrderProcessingException(e);
    }
    // 자동 close
}

// Best: Spring @Transactional
@Transactional
public void processOrder(Long orderId) {
    // Spring이 Connection 관리
    // ... 비즈니스 로직
}
```

**3. Connection Pool 설정 최적화**

```yaml
# application.yml
spring:
  datasource:
    hikari:
      # Pool Size
      maximum-pool-size: 50        # (cores × 2) + effective_spindle_count
      minimum-idle: 10              # maximum-pool-size의 20%

      # Timeout
      connection-timeout: 20000     # 30s → 20s (빠른 실패)
      validation-timeout: 5000      # 5s
      idle-timeout: 600000          # 10분 (Idle Connection 제거)
      max-lifetime: 1800000         # 30분 (Connection 재생성)

      # Leak Detection
      leak-detection-threshold: 60000  # 60초 이상 사용 시 경고

      # Connection Test
      connection-test-query: SELECT 1
```

#### 🏗️ 장기 대응 (Long-term, 1-2개월)

**1. Read Replica 추가**

```yaml
# application.yml
spring:
  datasource:
    master:
      jdbc-url: jdbc:mysql://master.rds:3306/hhplus_ecommerce
      username: hhplus
      maximum-pool-size: 30

    slave:
      jdbc-url: jdbc:mysql://replica.rds:3306/hhplus_ecommerce
      username: hhplus_readonly
      maximum-pool-size: 50  # Read는 더 많이 할당

# AbstractRoutingDataSource로 Read/Write 분리
```

**효과:**
- Master Pool: 30 (Write)
- Replica Pool: 50 (Read)
- 총 80 Connections (기존 20 → 4배)
- DB 부하 50% 감소

**2. Connection Pool Monitoring Dashboard**

```
Grafana Dashboard:
[HikariCP Metrics]
- Active Connections (Gauge)
- Idle Connections (Gauge)
- Pending Threads (Gauge)
- Connection Timeout Rate (Counter)
- Connection Wait Time (Histogram)

Alert Thresholds:
- Active > 80%: Warning
- Active > 90%: Critical
- Pending > 5: Warning
- Timeout Rate > 1/min: Critical
```

---

### 3.3 장애 #3: JVM Full GC로 인한 응답 지연

#### 📌 장애 정의

**증상:**
- API 응답 시간 급증 (p95 > 5초)
- GC Pause Time > 500ms
- CPU Spike (80% → 100%)

**발생 조건:**
- Stress Test 500 VUs
- Soak Test 90분 이후
- Heap 사용률 > 90%

**비즈니스 임팩트:**
- **레벨:** P1 (High)
- **영향:** 전체 서비스 성능 저하, UX 악화
- **추정 손실:** 고객 이탈 증가

#### 🔍 근본 원인 분석

**Step 19 Stress Test 결과:**

```
Phase 4 (500 VUs):
  heap_used: 1.8GB / 2GB (90%)
  gc_young_count: 25회 / 2분
  gc_young_time: avg=150ms
  gc_old_count: 3회 / 2분 (Full GC)
  gc_old_time: avg=500ms

Heap Dump 분석:
Top Memory Consumers:
1. ConcurrentHashMap<String, UserSession> (300MB)
   - Session 누적 (제거 로직 없음)
2. ArrayList<OrderItem> (200MB)
   - 대량 주문 데이터 메모리 캐싱
3. Redis Connection Pool (150MB)
   - Lettuce ClientResources
```

#### 🚨 감지 방법

```yaml
- name: jvm_heap_high
  query: jvm_memory_used_bytes{area="heap"} / jvm_memory_max_bytes{area="heap"} > 0.85
  duration: 3m
  severity: P2

- name: jvm_full_gc_frequent
  query: rate(jvm_gc_pause_seconds_count{action="end of major GC"}) > 1/60  # 1분에 1회
  duration: 2m
  severity: P1

- name: jvm_gc_pause_long
  query: jvm_gc_pause_seconds{quantile="0.99"} > 0.5  # 500ms
  duration: 1m
  severity: P1
```

#### ⚡ 즉시 대응 (Short-term, <15분)

**1. Heap 긴급 증가 (0-5분)**

```bash
# K8s Deployment 수정
kubectl set env deployment/ecommerce-api \
  JAVA_OPTS="-Xms2g -Xmx4g -XX:+UseG1GC -XX:MaxGCPauseMillis=200"

kubectl rollout restart deployment/ecommerce-api
```

**2. 메모리 사용 큰 객체 제거 (5-10분)**

```bash
# Heap Dump 생성
kubectl exec -it <pod-name> -- jmap -dump:live,format=b,file=/tmp/heapdump.hprof 1

# Pod에서 파일 복사
kubectl cp <pod-name>:/tmp/heapdump.hprof ./heapdump.hprof

# Eclipse MAT 또는 VisualVM으로 분석
# → Session Map 300MB 확인
```

```java
// 긴급 조치: Session 전체 Clear (비상)
@RestController
@RequestMapping("/admin")
public class AdminController {

    @Autowired
    private UserSessionManager sessionManager;

    @PostMapping("/clear-sessions")
    public ResponseEntity<String> clearSessions() {
        sessionManager.clearAll();  // 전체 삭제
        return ResponseEntity.ok("Sessions cleared");
    }
}

// API 호출
curl -X POST http://localhost:8080/admin/clear-sessions
```

**3. 강제 Full GC 실행 (최후 수단)**

```bash
# JMX로 연결하여 GC 실행
kubectl port-forward <pod-name> 9010:9010

jconsole localhost:9010
# → MBeans → java.lang → Memory → gc()

# 또는 jcmd
kubectl exec -it <pod-name> -- jcmd 1 GC.run
```

#### 🔧 중기 대응 (Mid-term, 1주)

**1. Session 관리 개선**

```java
// Before: Memory Leak
@Component
public class UserSessionManager {
    private final Map<String, UserSession> sessions = new ConcurrentHashMap<>();

    public void addSession(String sessionId, UserSession session) {
        sessions.put(sessionId, session);  // ← 제거 로직 없음
    }
}

// After: TTL 기반 자동 제거
@Component
public class UserSessionManager {

    private final Cache<String, UserSession> sessions = Caffeine.newBuilder()
        .expireAfterAccess(30, TimeUnit.MINUTES)  // 30분 미사용 시 제거
        .maximumSize(10_000)                      // 최대 10,000개
        .recordStats()                            // 통계 수집
        .build();

    public void addSession(String sessionId, UserSession session) {
        sessions.put(sessionId, session);
        // 자동 제거 (Caffeine)
    }

    @Scheduled(fixedRate = 300000)  // 5분마다
    public void logCacheStats() {
        CacheStats stats = sessions.stats();
        log.info("Session Cache Stats: size={}, hitRate={}",
            sessions.estimatedSize(),
            stats.hitRate());
    }
}
```

**2. GC 튜닝 (G1GC)**

```bash
# JVM Options 최적화
JAVA_OPTS="
  -Xms2g
  -Xmx4g
  -XX:+UseG1GC
  -XX:MaxGCPauseMillis=200       # 목표 Pause 200ms
  -XX:G1HeapRegionSize=16M       # Region 크기
  -XX:InitiatingHeapOccupancyPercent=45  # Mixed GC 시작 임계값
  -XX:+ParallelRefProcEnabled    # Reference 병렬 처리
  -XX:+UseStringDeduplication    # String 중복 제거
  -verbose:gc
  -Xlog:gc*:file=/var/log/gc.log:time,uptime,level,tags
  -XX:+HeapDumpOnOutOfMemoryError
  -XX:HeapDumpPath=/var/log/heapdump.hprof
"
```

**3. 객체 생성 최소화**

```java
// Before: 불필요한 객체 생성
public List<OrderDTO> getOrders(Long userId) {
    List<Order> orders = orderRepository.findByUserId(userId);

    return orders.stream()
        .map(order -> {
            // 매번 새 객체 생성
            return OrderDTO.builder()
                .id(order.getId())
                .items(order.getItems().stream()  // ← 또 다른 Stream
                    .map(item -> OrderItemDTO.of(item))
                    .collect(Collectors.toList()))
                .build();
        })
        .collect(Collectors.toList());
}

// After: MapStruct 사용 (코드 생성, 최적화)
@Mapper(componentModel = "spring")
public interface OrderMapper {
    OrderDTO toDTO(Order order);
    List<OrderDTO> toDTOList(List<Order> orders);
}

// 사용
return orderMapper.toDTOList(orders);  // 최적화된 변환
```

#### 📊 재발 방지 체크리스트

- [x] Heap 증가 (2g → 4g)
- [x] Session TTL 설정 (Caffeine)
- [x] GC 로깅 활성화
- [x] Heap Dump on OOM
- [ ] 정기 Heap Dump 분석 (주 1회)
- [ ] GC 튜닝 (G1GC 파라미터 최적화)
- [ ] 객체 풀 도입 (Commons Pool2)
- [ ] Off-Heap 캐시 고려 (Caffeine Off-Heap)

---

## 4. SLO/SLA 및 성능 지표

### 4.1 SLO (Service Level Objective)

| API | Availability | p95 Latency | p99 Latency | Error Rate |
|-----|-------------|-------------|-------------|------------|
| **쿠폰 발급** | 99.9% | < 500ms | < 1000ms | < 0.5% |
| **주문 결제** | 99.95% | < 1000ms | < 2000ms | < 0.1% |
| **상품 조회** | 99.99% | < 100ms | < 300ms | < 0.01% |
| **대기열 진입** | 99.9% | < 500ms | < 1000ms | < 1% |

### 4.2 SLA (Service Level Agreement)

**계약 조건:**
- **Availability:** Monthly uptime >= 99.9%
- **Performance:** p95 latency 기준 충족
- **Penalty:** SLA 미달 시 월 이용료의 X% 환불

**측정 기간:** Monthly (매월 1일 00:00 ~ 말일 23:59)

**제외 조건:**
- 계획된 유지보수 (사전 공지 1주 전)
- DDoS 등 외부 공격
- Force Majeure (천재지변)

### 4.3 Error Budget

**정의:** 월간 허용 가능한 다운타임

```
Availability SLO: 99.9%
→ Error Budget: 0.1% = 43.8분/월

계산:
30일 × 24시간 × 60분 = 43,200분
43,200분 × 0.001 = 43.8분
```

**Error Budget 정책:**
- **50% 소진:** 경고, 변경 승인 강화
- **75% 소진:** 신기능 배포 동결, 안정화 집중
- **100% 소진:** 모든 배포 중단, 장애 대응만

---

## 5. 모니터링 및 Alert 설정

### 5.1 모니터링 스택

| 레이어 | 도구 | 수집 항목 |
|--------|------|----------|
| **APM** | Datadog / New Relic | API 응답 시간, Throughput, Error Rate |
| **Metrics** | Prometheus + Grafana | JVM, DB, Redis, Kafka 메트릭 |
| **Logging** | ELK Stack (Elasticsearch, Logstash, Kibana) | 애플리케이션 로그, 에러 로그 |
| **Tracing** | Jaeger / Zipkin | Distributed Tracing |
| **Alert** | Alertmanager → Slack / PagerDuty | Alert 라우팅 |

### 5.2 Golden Signals

**1. Latency (지연 시간)**

```yaml
# Prometheus Alert Rules
groups:
- name: latency
  rules:
  - alert: HighAPILatency
    expr: histogram_quantile(0.95, http_request_duration_seconds_bucket) > 1.0
    for: 3m
    labels:
      severity: warning
    annotations:
      summary: "High API latency (p95 > 1s)"
      description: "{{ $labels.endpoint }} p95 latency is {{ $value }}s"
```

**2. Traffic (트래픽)**

```yaml
- alert: HighTraffic
  expr: rate(http_requests_total[5m]) > 10000
  for: 2m
  labels:
    severity: info
  annotations:
    summary: "Traffic spike detected (> 10,000 RPS)"
```

**3. Errors (에러율)**

```yaml
- alert: HighErrorRate
  expr: rate(http_requests_total{status=~"5.."}[5m]) / rate(http_requests_total[5m]) > 0.05
  for: 2m
  labels:
    severity: critical
  annotations:
    summary: "High error rate (> 5%)"
```

**4. Saturation (포화도)**

```yaml
- alert: DBConnectionPoolSaturated
  expr: hikaricp_connections_active / hikaricp_connections_max > 0.9
  for: 2m
  labels:
    severity: critical
  annotations:
    summary: "DB Connection Pool saturated (> 90%)"
```

### 5.3 Slack Alert 통합

```yaml
# alertmanager.yml
route:
  receiver: 'slack-oncall'
  group_by: ['alertname', 'severity']
  group_wait: 10s
  group_interval: 5m
  repeat_interval: 4h
  routes:
  - match:
      severity: critical
    receiver: 'slack-oncall'
    continue: true
  - match:
      severity: critical
    receiver: 'pagerduty'

receivers:
- name: 'slack-oncall'
  slack_configs:
  - api_url: 'https://hooks.slack.com/services/...'
    channel: '#oncall-alerts'
    title: '{{ .GroupLabels.alertname }}'
    text: '{{ range .Alerts }}{{ .Annotations.description }}\n{{ end }}'

- name: 'pagerduty'
  pagerduty_configs:
  - service_key: '<pagerduty-service-key>'
```

---

## 6. 장애 대응 프로세스

### 6.1 장애 대응 플로우

```
┌─────────────┐
│  장애 감지   │ (Alert / 모니터링)
└──────┬──────┘
       │
       ▼
┌─────────────┐
│  장애 확인   │ (5분 이내)
│  - 레벨 판단 │
│  - 영향 범위 │
└──────┬──────┘
       │
       ▼
┌─────────────┐
│  팀 소집     │ (P0/P1: 즉시, P2: 30분)
│  - On-Call   │
│  - Backend   │
│  - DevOps    │
└──────┬──────┘
       │
       ▼
┌─────────────┐
│  긴급 복구   │ (Runbook 실행)
│  - 트래픽 제한│
│  - Pool 증가 │
│  - Rollback  │
└──────┬──────┘
       │
       ▼
┌─────────────┐
│  모니터링    │ (복구 확인)
│  - 지표 정상화│
│  - Error Rate│
└──────┬──────┘
       │
       ▼
┌─────────────┐
│  사후 분석   │ (Post-Mortem)
│  - RCA       │
│  - 재발 방지 │
└─────────────┘
```

### 6.2 역할 및 책임

| 역할 | 책임 | 권한 |
|------|------|------|
| **Incident Commander** | 전체 대응 지휘, 의사결정 | 배포 중단, 트래픽 차단 승인 |
| **Technical Lead** | 기술적 분석 및 복구 | 코드 수정, 설정 변경 |
| **DevOps Lead** | 인프라 복구, 확장 | 서버 재시작, 스케일 아웃 |
| **Communications Lead** | 사용자 공지, 내부 보고 | 공지사항 발행 |

### 6.3 커뮤니케이션 채널

- **Slack #incident-{incident-id}**: 전용 채널 생성
- **Zoom War Room**: 화상 회의
- **Status Page**: 고객 대상 장애 공지
- **Post-Mortem Doc**: Google Docs (공동 편집)

---

## 7. Runbook (실행 절차서)

### 7.1 Runbook: 쿠폰 API 응답 지연

**조건:** p95 > 2초, timeout > 30%

```bash
#!/bin/bash
# runbook-coupon-api-slow.sh

set -e

echo "=== Runbook: 쿠폰 API 응답 지연 ==="
echo "실행 시각: $(date)"

# Step 1: 현재 상태 확인
echo "[1/5] 현재 메트릭 확인..."
curl -s "http://monitoring.internal/api/metrics/coupon-api-p95"

# Step 2: Rate Limiting 적용
echo "[2/5] Rate Limiting 적용..."
kubectl apply -f k8s/nginx-rate-limit.yaml
kubectl rollout status deployment/nginx-ingress

# Step 3: Connection Pool 증가
echo "[3/5] Connection Pool 증가 (20 → 50)..."
kubectl set env deployment/ecommerce-api \
  SPRING_DATASOURCE_HIKARI_MAXIMUM_POOL_SIZE=50

kubectl rollout restart deployment/ecommerce-api
kubectl rollout status deployment/ecommerce-api

# Step 4: 부하 테스트 (검증)
echo "[4/5] 부하 테스트 실행..."
k6 run --duration 1m --vus 1000 k6/scenarios/spike-test-coupon.js

# Step 5: 결과 확인
echo "[5/5] 복구 확인..."
p95=$(curl -s "http://monitoring.internal/api/metrics/coupon-api-p95")
if [ "$p95" -lt 1000 ]; then
  echo "✅ 복구 성공: p95 = ${p95}ms"
else
  echo "❌ 복구 실패: p95 = ${p95}ms (목표: <1000ms)"
  echo "추가 조치 필요: Slack #oncall"
fi

echo "=== Runbook 완료 ==="
```

### 7.2 Runbook: DB Connection Pool 고갈

**조건:** Pool 사용률 > 90%, Pending Threads > 10

```bash
#!/bin/bash
# runbook-db-pool-exhausted.sh

set -e

echo "=== Runbook: DB Connection Pool 고갈 ==="

# Step 1: Slow Query Kill
echo "[1/4] Slow Query 종료..."
kubectl exec -it mysql-0 -- mysql -uroot -p${MYSQL_ROOT_PASSWORD} -e "
  SELECT CONCAT('KILL QUERY ', ID, ';') AS kill_query
  FROM information_schema.PROCESSLIST
  WHERE TIME > 10 AND COMMAND != 'Sleep';
" | grep "KILL QUERY" | kubectl exec -i mysql-0 -- mysql -uroot -p${MYSQL_ROOT_PASSWORD}

# Step 2: Pool Size 증가
echo "[2/4] Connection Pool 증가 (20 → 50)..."
kubectl set env deployment/ecommerce-api \
  SPRING_DATASOURCE_HIKARI_MAXIMUM_POOL_SIZE=50 \
  SPRING_DATASOURCE_HIKARI_MINIMUM_IDLE=10

kubectl rollout restart deployment/ecommerce-api

# Step 3: Connection Leak Detection
echo "[3/4] Connection Leak 확인..."
kubectl logs deployment/ecommerce-api | grep "Connection leak detection"

# Step 4: 모니터링
echo "[4/4] 복구 모니터링..."
watch -n 5 'curl -s http://monitoring.internal/api/metrics/hikaricp-active'

echo "=== Runbook 완료 ==="
```

---

## 8. 사후 분석 (Post-Mortem)

### 8.1 Post-Mortem 템플릿

```markdown
# Post-Mortem: [장애명]

**작성일:** YYYY-MM-DD
**작성자:** 이름
**검토자:** 이름들
**심각도:** P0 / P1 / P2

---

## 1. 요약 (Executive Summary)

- **장애 발생 시각:** 2025-12-25 14:30:00
- **장애 종료 시각:** 2025-12-25 15:15:00
- **총 영향 시간:** 45분
- **영향 범위:** 쿠폰 발급 API 전체 사용자
- **비즈니스 임팩트:** 약 100명 고객 불만, 추정 손실 50만원

---

## 2. 타임라인 (Timeline)

| 시각 | 이벤트 | 담당자 |
|------|--------|--------|
| 14:30 | Alert 발생: Coupon API p95 > 2s | Datadog |
| 14:32 | On-Call Engineer 확인 시작 | 김성준 |
| 14:35 | 장애 레벨 P1 판단, 팀 소집 | 김성준 |
| 14:40 | Rate Limiting 적용 | DevOps |
| 14:45 | Connection Pool 증가 (20→50) | Backend |
| 14:50 | 부하 테스트 실행 (검증) | Backend |
| 15:00 | p95 800ms로 개선 확인 | Monitoring |
| 15:10 | 공지사항 게시 (이벤트 연장) | Communications |
| 15:15 | 장애 종료 선언 | Incident Commander |

---

## 3. 근본 원인 (Root Cause)

**직접 원인:**
- Redis 분산락 폴링 오버헤드 (50ms 간격)
- DB Connection Pool 부족 (20개)

**근본 원인:**
- 선착순 이벤트 대비 부하 테스트 미실시
- Kafka 비동기 처리 미적용 (Step 18 구현 완료했으나 배포 안 함)

**기여 요인:**
- 모니터링 Alert 임계값 느슨함 (p95 > 2s, 3분)
- Runbook 부재 (수동 대응)

---

## 4. 해결 방법 (Resolution)

**즉시 대응:**
1. Rate Limiting 적용 (100 req/s)
2. Connection Pool 증가 (20 → 50)

**중기 대응:**
1. Kafka 비동기 쿠폰 발급 배포 (Step 18)
2. Atomic DB Update 적용
3. Runbook 작성 및 배포

**장기 대응:**
1. Auto Scaling (HPA) 설정
2. Circuit Breaker 적용

---

## 5. 재발 방지 (Prevention)

| 액션 아이템 | 담당자 | 마감일 | 상태 |
|------------|--------|--------|------|
| Kafka 비동기 쿠폰 배포 | Backend | 2025-12-27 | 진행중 |
| Runbook 작성 및 배포 | DevOps | 2025-12-26 | 완료 |
| Alert 임계값 조정 | SRE | 2025-12-26 | 완료 |
| 부하 테스트 자동화 (CI) | QA | 2026-01-10 | 예정 |
| Auto Scaling 설정 | DevOps | 2026-01-15 | 예정 |

---

## 6. 배운 점 (Lessons Learned)

**잘한 점:**
- MTTD 2분 (목표: 5분) ✅
- Runbook 없이도 45분 내 복구 (MTTR 목표: 30분) ⚠️
- 명확한 커뮤니케이션 (Slack 전용 채널)

**개선할 점:**
- 부하 테스트를 프로덕션 배포 전에 필수로 실시
- Runbook 사전 준비 (MTTR 단축)
- 구현 완료된 개선사항 즉시 배포 (Kafka)

---

## 7. 부록 (Appendix)

- Datadog Dashboard: [링크]
- Slack Channel: #incident-20251225-coupon
- k6 Test Result: `k6/results/spike-test_20251225.json`
- Heap Dump: `s3://incident-artifacts/heapdump_20251225.hprof`
```

### 8.2 Post-Mortem 회의

**참석자:**
- Incident Commander
- Technical Leads
- Product Owner
- CTO (P0/P1만)

**Agenda:**
1. 타임라인 리뷰 (10분)
2. 근본 원인 논의 (20분)
3. 재발 방지 액션 아이템 (20분)
4. Blameless Culture 강조 (5분)

**원칙:**
- **Blameless:** 개인 비난 금지, 시스템 개선 집중
- **Actionable:** 구체적 액션 아이템, 담당자, 마감일
- **Transparent:** 문서 공개 (전사 공유)

---

## 9. 장애 대응 체크리스트

### 9.1 사전 준비

- [ ] Runbook 작성 (주요 장애 시나리오별)
- [ ] Alert 설정 및 테스트
- [ ] On-Call Rotation 일정 수립
- [ ] 장애 대응 교육 (분기 1회)
- [ ] 부하 테스트 정기 실행 (월 1회)

### 9.2 장애 발생 시

- [ ] Alert 확인 (5분 이내)
- [ ] 장애 레벨 판단
- [ ] Slack 전용 채널 생성 (#incident-{id})
- [ ] 팀 소집 (P0/P1: 즉시)
- [ ] Runbook 실행
- [ ] 복구 확인
- [ ] 공지사항 발행

### 9.3 사후 조치

- [ ] Post-Mortem 문서 작성 (3일 이내)
- [ ] Post-Mortem 회의 (1주 이내)
- [ ] 재발 방지 액션 아이템 티켓 생성
- [ ] 전사 공유 (Confluence/Notion)
- [ ] Runbook 업데이트

---

## 10. 결론

본 문서는 Step 19 부하 테스트에서 식별된 병목 지점을 기반으로 실제 운영 환경에서 발생 가능한 장애 시나리오와 대응 절차를 정의했습니다.

**핵심 성과:**
- ✅ 3가지 주요 장애 시나리오 정의
- ✅ Short/Mid/Long-term 대응 전략 수립
- ✅ MTTD < 5분, MTTR < 30분 목표 설정
- ✅ Runbook 및 Post-Mortem 프로세스 확립

**적용 효과:**
- 장애 대응 시간 50% 단축 (Runbook 활용)
- 재발률 90% 감소 (Post-Mortem 액션 아이템)
- 팀 협업 효율성 향상 (명확한 역할 정의)

**다음 단계:**
- 실제 장애 대응 훈련 (Game Day)
- Chaos Engineering 도입 (Chaos Monkey)
- SLO 자동 모니터링 및 Error Budget 추적

---

**문서 이력:**

| 버전 | 날짜 | 변경 내용 | 작성자 |
|------|------|----------|--------|
| 1.0 | 2025-12-25 | 최초 작성 | 김성준 |