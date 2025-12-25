# Step 18: Kafka를 활용한 비즈니스 프로세스 개선 설계

## 목차

1. [설계 개요](#1-설계-개요)
2. [기존 시스템 한계점 분석](#2-기존-시스템-한계점-분석)
3. [Kafka 기반 선착순 쿠폰 발급 설계](#3-kafka-기반-선착순-쿠폰-발급-설계)
4. [Kafka 기반 콘서트 대기열 설계](#4-kafka-기반-콘서트-대기열-설계)
5. [중복 처리 방지 전략](#5-중복-처리-방지-전략)
6. [성능 개선 지표](#6-성능-개선-지표)
7. [구현 계획](#7-구현-계획)

---

## 1. 설계 개요

### 1.1 목표

기존 Redis 기반 동시성 제어 시스템의 한계를 극복하고, Kafka의 파티션 전략과 순차/병렬 처리를 활용하여:

1. **선착순 쿠폰 발급**: 대량 트래픽 처리 + 정확한 수량 제어
2. **콘서트 대기열**: 공정한 순서 보장 + 확장 가능한 처리

를 구현합니다.

### 1.2 핵심 설계 원칙

| 원칙 | 설명 |
|------|------|
| **이벤트 기반 아키텍처** | 요청을 즉시 Kafka에 발행, 비동기 처리 |
| **파티션 전략 최적화** | 쿠폰: 병렬 처리, 대기열: 순차 처리 |
| **Idempotent Consumer** | 중복 처리 방지 (처리 이력 테이블) |
| **At-least-once 보장** | 수동 커밋으로 메시지 손실 방지 |
| **Outbox 패턴** | 트랜잭션 원자성 보장 |

---

## 2. 기존 시스템 한계점 분석

### 2.1 현재 Redis 기반 쿠폰 발급 시스템

#### 문제점 1: 응답 시간 지연

```java
// 현재 구조 (분산락 방식)
@DistributedLock(key = "coupon:issue:{couponId}")
public CouponUser issueCouponWithDistributedLock(Long couponId, Long userId) {
    // 50ms 폴링 대기 → 평균 100-500ms 응답 시간
    Coupon coupon = couponRepository.findById(couponId).orElseThrow();

    if (coupon.getIssuedQty() >= coupon.getTotalQty()) {
        throw new BusinessException("쿠폰 소진");
    }

    coupon.incrementIssuedQty();  // DB 업데이트
    return couponUserRepository.save(...);
}
```

**문제:**
- 분산락 획득을 위한 폴링 오버헤드 (50ms × N회)
- 동시 요청 시 순차 처리로 인한 처리량 제한 (500 req/s)
- DB에 직접 쓰기 → 높은 DB 부하 (40%)

#### 문제점 2: 데이터 정합성 위험

```java
// 현재 구조 (Redis 비동기 방식)
public String issueCouponAsync(Long couponId, Long userId) {
    // Redis에서 수량 차감 (1-5ms 응답)
    Long remaining = redisTemplate.opsForValue().decrement("coupon:" + couponId);

    if (remaining < 0) {
        redisTemplate.opsForValue().increment("coupon:" + couponId);
        throw new BusinessException("쿠폰 소진");
    }

    // ❌ 문제: DB 동기화는 10초 후 스케줄러가 처리
    return "발급 대기 중";
}

// 10초마다 실행되는 스케줄러
@Scheduled(fixedDelay = 10000)
public void syncCouponIssuance() {
    // Redis → DB 동기화
    // ⚠️ 서버 장애 시 10초간의 데이터 손실 위험
}
```

**문제:**
- 10초 주기 동기화 → 서버 장애 시 데이터 손실
- Redis와 DB 간 일시적 불일치
- 실시간 발급 현황 파악 어려움

#### 문제점 3: 확장성 한계

```
[현재 구조]

Client → API Server (Single Point) → Redis Lock → DB
           ↓
        병목 발생 (500 req/s 한계)
```

**문제:**
- API 서버가 모든 요청을 처리 (수평 확장 제한)
- 분산락으로 인한 순차 처리 강제
- 트래픽 급증 시 서버 과부하

---

### 2.2 콘서트 대기열 시스템 (미구현)

현재는 콘서트 대기열 시스템이 구현되어 있지 않습니다.

**필요한 기능:**
- 대량의 사용자 대기열 관리 (수만 명)
- 공정한 순서 보장 (FIFO)
- 실시간 대기 순번 조회
- 순차적 입장 처리

**Redis 기반 구현 시 예상 문제:**
- Redis Sorted Set 크기 제한 (메모리 부족)
- 순차 처리로 인한 낮은 처리량
- 서버 장애 시 대기열 유실

---

## 3. Kafka 기반 선착순 쿠폰 발급 설계

### 3.1 아키텍처 설계

```
[요청 흐름]

1. 클라이언트 요청
   ↓
2. API 서버 (즉시 응답)
   ├── 쿠폰 발급 요청 이벤트 발행 (Kafka)
   └── Response: "발급 처리 중" (1-5ms)

3. Kafka Topic: coupon-issue-request
   ├── Partition 0 (Consumer 0)
   ├── Partition 1 (Consumer 1)
   └── Partition 2 (Consumer 2)

4. Consumer (병렬 처리)
   ├── 중복 체크 (ProcessedEvent 테이블)
   ├── 수량 체크 (DB)
   ├── 쿠폰 발급 (DB)
   └── 결과 이벤트 발행 (Kafka)

5. Kafka Topic: coupon-issue-result
   ↓
6. Result Consumer
   ├── 사용자에게 알림 (WebSocket/SSE)
   └── 발급 결과 저장
```

### 3.2 시퀀스 다이어그램

```
Client          API Server         Kafka           Consumer         DB
  │                 │                 │                │             │
  │─────(1)────────→│                 │                │             │
  │  POST /issue    │                 │                │             │
  │                 │                 │                │             │
  │                 │───(2) Publish──→│                │             │
  │                 │  IssueRequest   │                │             │
  │                 │                 │                │             │
  │←─────(3)────────│                 │                │             │
  │  202 Accepted   │                 │                │             │
  │  "처리 중"       │                 │                │             │
  │                 │                 │                │             │
  │                 │                 │────(4) Poll───→│             │
  │                 │                 │                │             │
  │                 │                 │                │──(5) Check─→│
  │                 │                 │                │  Duplicate  │
  │                 │                 │                │             │
  │                 │                 │                │←────────────│
  │                 │                 │                │  No Dup     │
  │                 │                 │                │             │
  │                 │                 │                │──(6) Issue─→│
  │                 │                 │                │  Coupon     │
  │                 │                 │                │             │
  │                 │                 │                │←────────────│
  │                 │                 │                │  Success    │
  │                 │                 │                │             │
  │                 │                 │←──(7) Publish──│             │
  │                 │                 │  IssueResult   │             │
  │                 │                 │                │             │
  │                 │                 │──(8) Poll─────→│             │
  │                 │                 │                │             │
  │←──────────(9) Notify via WebSocket/SSE────────────│             │
  │  "발급 성공"     │                 │                │             │
```

### 3.3 Topic 및 Partition 설계

#### Topic 1: `coupon-issue-request`

**목적:** 쿠폰 발급 요청 수신

| 설정 | 값 | 이유 |
|------|---|------|
| **Partitions** | 5개 | 5개 Consumer로 병렬 처리 (5000 req/s) |
| **Replication** | 3개 | 고가용성 (2개 Replica 장애 허용) |
| **Message Key** | `couponId` | 동일 쿠폰은 동일 파티션 (순서 보장) |
| **Retention** | 7일 | 감사 및 재처리 |

**메시지 스키마:**

```json
{
  "requestId": "uuid-123",
  "couponId": 1,
  "userId": 100,
  "timestamp": "2025-12-18T10:00:00Z"
}
```

#### Topic 2: `coupon-issue-result`

**목적:** 쿠폰 발급 결과 전송

| 설정 | 값 | 이유 |
|------|---|------|
| **Partitions** | 3개 | 결과 처리는 경량 작업 |
| **Replication** | 3개 | 고가용성 |
| **Message Key** | `userId` | 사용자별 순서 보장 |
| **Retention** | 30일 | 발급 이력 보관 |

**메시지 스키마:**

```json
{
  "requestId": "uuid-123",
  "couponId": 1,
  "userId": 100,
  "status": "SUCCESS",
  "issuedAt": "2025-12-18T10:00:01Z",
  "errorMessage": null
}
```

### 3.4 병렬 처리 전략

#### Consumer 동시성 설정

```yaml
spring:
  kafka:
    consumer:
      group-id: coupon-issue-consumer-group

    listener:
      concurrency: 5  # 5개 파티션 = 5개 Consumer 스레드
```

#### Consumer 구현

```java
@KafkaListener(
    topics = "coupon-issue-request",
    groupId = "coupon-issue-consumer-group",
    concurrency = "5"
)
public void consumeCouponIssueRequest(
        @Payload CouponIssueRequestEvent event,
        @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
        Acknowledgment ack) {

    log.info("쿠폰 발급 요청 수신: partition={}, couponId={}, userId={}",
            partition, event.getCouponId(), event.getUserId());

    try {
        // 1. 중복 체크
        if (processedEventRepository.existsByRequestId(event.getRequestId())) {
            log.warn("중복 요청 무시: requestId={}", event.getRequestId());
            ack.acknowledge();
            return;
        }

        // 2. 쿠폰 발급 처리
        CouponUser issuedCoupon = couponService.issueCoupon(
                event.getCouponId(), event.getUserId());

        // 3. 처리 이력 저장 (중복 방지)
        processedEventRepository.save(
                ProcessedEvent.of(event.getRequestId(), "SUCCESS"));

        // 4. 결과 이벤트 발행
        kafkaProducerService.publishCouponIssueResult(
                CouponIssueResultEvent.success(event, issuedCoupon));

        // 5. 수동 커밋
        ack.acknowledge();

    } catch (Exception e) {
        log.error("쿠폰 발급 실패: requestId={}, error={}",
                event.getRequestId(), e.getMessage());

        // 실패 시 커밋하지 않음 → 재처리
        throw new RuntimeException("쿠폰 발급 실패", e);
    }
}
```

#### 동시성 제어 방식

**기존 (분산락):**
```
Request 1 → Lock 획득 → 처리 (100ms) → Lock 해제
Request 2 → Lock 대기 (100ms) → 처리 (100ms) → Lock 해제
Request 3 → Lock 대기 (200ms) → 처리 (100ms)

총 처리 시간: 500ms (순차 처리)
```

**Kafka (파티션 병렬):**
```
Request 1 → Partition 0 → Consumer 0 (100ms)
Request 2 → Partition 1 → Consumer 1 (100ms)
Request 3 → Partition 2 → Consumer 2 (100ms)

총 처리 시간: 100ms (병렬 처리)
```

**처리량 비교:**

| 방식 | 동시 처리 | 응답 시간 | 처리량 |
|------|----------|----------|--------|
| 분산락 | 1개 | 100-500ms | 500 req/s |
| Kafka (5 파티션) | 5개 | 1-5ms (API) | 5000+ req/s |

### 3.5 수량 제어 전략

#### 문제: Race Condition

```
Consumer 1: SELECT issued_qty FROM coupon WHERE id=1 → 99
Consumer 2: SELECT issued_qty FROM coupon WHERE id=1 → 99

Consumer 1: UPDATE coupon SET issued_qty=100 WHERE id=1 ✅
Consumer 2: UPDATE coupon SET issued_qty=100 WHERE id=1 ✅ (중복 발급!)
```

#### 해결 방안 1: Optimistic Lock

```java
@Entity
public class Coupon {
    @Id
    private Long id;

    private Integer issuedQty;
    private Integer totalQty;

    @Version
    private Long version;  // 낙관적 락

    public void incrementIssuedQty() {
        if (this.issuedQty >= this.totalQty) {
            throw new BusinessException("쿠폰 소진");
        }
        this.issuedQty++;
    }
}

@Service
public class CouponService {
    @Transactional
    @Retry(maxAttempts = 3)  // 충돌 시 재시도
    public CouponUser issueCoupon(Long couponId, Long userId) {
        Coupon coupon = couponRepository.findById(couponId).orElseThrow();
        coupon.incrementIssuedQty();  // Version 체크
        couponRepository.save(coupon);

        return couponUserRepository.save(...);
    }
}
```

#### 해결 방안 2: Atomic DB Update

```java
@Repository
public interface CouponRepository extends JpaRepository<Coupon, Long> {

    @Modifying
    @Query("UPDATE Coupon c SET c.issuedQty = c.issuedQty + 1 " +
           "WHERE c.id = :couponId AND c.issuedQty < c.totalQty")
    int incrementIssuedQty(@Param("couponId") Long couponId);
}

@Service
public class CouponService {
    @Transactional
    public CouponUser issueCoupon(Long couponId, Long userId) {
        int updated = couponRepository.incrementIssuedQty(couponId);

        if (updated == 0) {
            throw new BusinessException("쿠폰 소진");
        }

        return couponUserRepository.save(...);
    }
}
```

**선택:** **Atomic DB Update** (성능 우수, 충돌 없음)

---

## 4. Kafka 기반 콘서트 대기열 설계

### 4.1 아키텍처 설계

```
[대기열 입장 흐름]

1. 클라이언트 요청
   ↓
2. API 서버
   ├── 대기열 입장 이벤트 발행 (Kafka)
   └── Response: "대기열 등록 완료" (1-5ms)

3. Kafka Topic: concert-queue-entry
   └── Partition 0 (단일 파티션 - 순서 보장!)
       ↓
4. Queue Consumer (순차 처리)
   ├── 대기열 등록 (DB)
   ├── 대기 순번 부여 (Offset)
   └── 입장 가능 여부 확인

5. Kafka Topic: concert-queue-status
   ↓
6. Status Consumer
   └── WebSocket으로 실시간 순번 전송
```

### 4.2 시퀀스 다이어그램

```
Client      API Server       Kafka           Queue Consumer      DB
  │             │              │                   │              │
  │──(1) Join──→│              │                   │              │
  │             │              │                   │              │
  │             │──(2) Publish→│                   │              │
  │             │  QueueEntry  │                   │              │
  │             │              │                   │              │
  │←─(3) 202───│              │                   │              │
  │  "등록됨"    │              │                   │              │
  │             │              │                   │              │
  │             │              │───(4) Sequential──→│              │
  │             │              │      Poll         │              │
  │             │              │                   │              │
  │             │              │                   │──(5) Insert─→│
  │             │              │                   │   Queue      │
  │             │              │                   │              │
  │             │              │                   │←─────────────│
  │             │              │                   │  Queue #1523 │
  │             │              │                   │              │
  │             │              │←──(6) Publish─────│              │
  │             │              │    QueueStatus    │              │
  │             │              │                   │              │
  │←──────────(7) WebSocket Notify────────────────│              │
  │  "현재 1523번째"  │              │                   │              │
```

### 4.3 Topic 설계

#### Topic 1: `concert-queue-entry`

**목적:** 대기열 입장 요청 (순서 보장 필수)

| 설정 | 값 | 이유 |
|------|---|------|
| **Partitions** | **1개** | FIFO 순서 보장 (중요!) |
| **Replication** | 3개 | 고가용성 |
| **Message Key** | `null` | 모든 메시지를 동일 파티션으로 |
| **Retention** | 24시간 | 당일 대기열만 유지 |

**메시지 스키마:**

```json
{
  "requestId": "uuid-456",
  "concertId": 1,
  "userId": 200,
  "timestamp": "2025-12-18T10:00:00Z"
}
```

#### Topic 2: `concert-queue-status`

**목적:** 대기 순번 업데이트

| 설정 | 값 | 이유 |
|------|---|------|
| **Partitions** | 3개 | 순번 업데이트는 병렬 처리 가능 |
| **Replication** | 3개 | 고가용성 |
| **Message Key** | `userId` | 사용자별 순서 보장 |

**메시지 스키마:**

```json
{
  "userId": 200,
  "concertId": 1,
  "queuePosition": 1523,
  "estimatedWaitTime": 300,
  "status": "WAITING"
}
```

### 4.4 순차 처리 전략

#### Consumer 구현

```java
@KafkaListener(
    topics = "concert-queue-entry",
    groupId = "concert-queue-consumer-group",
    concurrency = "1"  // 단일 Consumer (순차 보장)
)
public void consumeQueueEntry(
        @Payload QueueEntryEvent event,
        @Header(KafkaHeaders.OFFSET) long offset,
        Acknowledgment ack) {

    log.info("대기열 입장: offset={}, userId={}", offset, event.getUserId());

    try {
        // 1. Offset을 대기 순번으로 사용
        long queuePosition = offset + 1;

        // 2. 대기열 등록
        ConcertQueue queue = ConcertQueue.create(
                event.getConcertId(),
                event.getUserId(),
                queuePosition);
        concertQueueRepository.save(queue);

        // 3. 입장 가능 여부 확인 (현재 100명까지 입장)
        boolean canEnter = queuePosition <= 100;

        // 4. 상태 이벤트 발행
        kafkaProducerService.publishQueueStatus(
                QueueStatusEvent.of(event, queuePosition, canEnter));

        // 5. 수동 커밋
        ack.acknowledge();

    } catch (Exception e) {
        log.error("대기열 처리 실패: userId={}", event.getUserId(), e);
        throw new RuntimeException("대기열 처리 실패", e);
    }
}
```

#### 순서 보장 원리

**Kafka Offset을 대기 순번으로 활용:**

```
Partition 0 (concert-queue-entry)
┌──────────────────────────────────────┐
│ Offset 0: User 100 → Queue #1        │
│ Offset 1: User 101 → Queue #2        │
│ Offset 2: User 102 → Queue #3        │
│ Offset 3: User 103 → Queue #4        │
│ ...                                  │
│ Offset 1522: User 1622 → Queue #1523 │
└──────────────────────────────────────┘

순서 보장: Offset은 Kafka가 자동으로 순차 증가!
```

**장점:**
- Kafka Offset의 순차성 보장 활용
- 별도 순번 관리 불필요
- 정확한 FIFO 보장

### 4.5 입장 처리 로직

#### 주기적 입장 허용

```java
@Scheduled(fixedDelay = 5000)
public void processQueueEntry() {
    // 현재 입장 가능한 사용자 수 (동시 접속 한도: 1000명)
    long currentUsers = concertSessionRepository.countActiveUsers();
    long availableSlots = 1000 - currentUsers;

    if (availableSlots <= 0) {
        log.info("입장 대기: 동시 접속 한도 도달");
        return;
    }

    // 대기 중인 사용자 중 상위 N명 입장 허용
    List<ConcertQueue> waitingQueue = concertQueueRepository
            .findTopNByStatusOrderByQueuePosition(
                    QueueStatus.WAITING,
                    (int) availableSlots);

    for (ConcertQueue queue : waitingQueue) {
        // 입장 허용
        queue.approve();
        concertQueueRepository.save(queue);

        // 세션 생성
        ConcertSession session = ConcertSession.create(
                queue.getConcertId(),
                queue.getUserId(),
                LocalDateTime.now().plusMinutes(10));  // 10분 유효
        concertSessionRepository.save(session);

        // 입장 허용 이벤트 발행
        kafkaProducerService.publishQueueApproval(
                QueueApprovalEvent.of(queue));
    }

    log.info("입장 처리 완료: {}명 입장", waitingQueue.size());
}
```

### 4.6 실시간 순번 조회

#### WebSocket을 통한 실시간 업데이트

```java
@Service
@RequiredArgsConstructor
public class QueueStatusService {

    private final SimpMessagingTemplate messagingTemplate;

    @KafkaListener(topics = "concert-queue-status")
    public void consumeQueueStatus(@Payload QueueStatusEvent event) {
        // WebSocket으로 사용자에게 실시간 전송
        messagingTemplate.convertAndSendToUser(
                String.valueOf(event.getUserId()),
                "/queue/status",
                event);
    }
}
```

#### 클라이언트 수신

```javascript
// JavaScript (STOMP over WebSocket)
const socket = new SockJS('/ws');
const stompClient = Stomp.over(socket);

stompClient.connect({}, function(frame) {
    stompClient.subscribe('/user/queue/status', function(message) {
        const status = JSON.parse(message.body);
        console.log(`현재 대기 순번: ${status.queuePosition}`);
        console.log(`예상 대기 시간: ${status.estimatedWaitTime}초`);
    });
});
```

---

## 5. 중복 처리 방지 전략

### 5.1 문제 상황

**At-least-once 보장으로 인한 중복 처리:**

```
Consumer 장애 시나리오:

1. Consumer가 메시지 처리 완료
2. 쿠폰 발급 완료 (DB에 저장)
3. 커밋 전 Consumer 크래시 💥
4. Kafka는 커밋되지 않았으므로 재전송
5. 다른 Consumer가 동일 메시지 재처리
6. 중복 쿠폰 발급! ❌
```

### 5.2 해결 방안: Idempotent Consumer 패턴

#### ProcessedEvent 테이블 설계

```sql
CREATE TABLE processed_event (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    request_id VARCHAR(255) NOT NULL,  -- 요청 고유 ID
    event_type VARCHAR(50) NOT NULL,   -- 이벤트 타입
    status VARCHAR(20) NOT NULL,       -- SUCCESS, FAILED
    processed_at TIMESTAMP NOT NULL,
    error_message TEXT,

    UNIQUE KEY uk_request_id (request_id)  -- 중복 방지
);

CREATE INDEX idx_request_id ON processed_event(request_id);
```

#### Entity 구현

```java
@Entity
@Table(name = "processed_event")
public class ProcessedEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String requestId;

    @Column(nullable = false)
    private String eventType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ProcessStatus status;

    @Column(nullable = false)
    private LocalDateTime processedAt;

    private String errorMessage;

    public static ProcessedEvent of(String requestId, String eventType, ProcessStatus status) {
        ProcessedEvent event = new ProcessedEvent();
        event.requestId = requestId;
        event.eventType = eventType;
        event.status = status;
        event.processedAt = LocalDateTime.now();
        return event;
    }
}

public enum ProcessStatus {
    SUCCESS, FAILED
}
```

#### Consumer 구현 (중복 체크)

```java
@KafkaListener(topics = "coupon-issue-request")
@Transactional
public void consumeCouponIssueRequest(
        @Payload CouponIssueRequestEvent event,
        Acknowledgment ack) {

    String requestId = event.getRequestId();

    // 1. 중복 체크 (빠른 조회)
    if (processedEventRepository.existsByRequestId(requestId)) {
        log.warn("중복 요청 감지, 무시: requestId={}", requestId);
        ack.acknowledge();  // 중복이므로 커밋만 하고 종료
        return;
    }

    try {
        // 2. 비즈니스 로직 처리
        CouponUser issuedCoupon = couponService.issueCoupon(
                event.getCouponId(), event.getUserId());

        // 3. 처리 이력 저장 (동일 트랜잭션)
        processedEventRepository.save(
                ProcessedEvent.of(requestId, "COUPON_ISSUE", ProcessStatus.SUCCESS));

        // 4. 커밋
        ack.acknowledge();

        log.info("쿠폰 발급 성공: requestId={}, couponId={}",
                requestId, event.getCouponId());

    } catch (Exception e) {
        log.error("쿠폰 발급 실패: requestId={}, error={}",
                requestId, e.getMessage());

        // 실패 이력 저장
        processedEventRepository.save(
                ProcessedEvent.of(requestId, "COUPON_ISSUE", ProcessStatus.FAILED)
                        .withError(e.getMessage()));

        // 재처리를 위해 커밋하지 않음
        throw new RuntimeException("쿠폰 발급 실패", e);
    }
}
```

#### 트랜잭션 원자성 보장

```
[동일 트랜잭션 내에서 처리]

┌─────────────────────────────────┐
│  @Transactional                 │
│                                 │
│  1. 중복 체크                    │
│  2. 쿠폰 발급 (coupon_user)      │
│  3. 처리 이력 저장 (processed_event) │
│  4. 커밋                         │
│                                 │
│  → 모두 성공 or 모두 롤백        │
└─────────────────────────────────┘
```

### 5.3 대안: Unique Constraint 활용

#### CouponUser 테이블에 Unique 제약조건 추가

```sql
CREATE TABLE coupon_user (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    coupon_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    status VARCHAR(20) NOT NULL,
    issued_at TIMESTAMP NOT NULL,

    UNIQUE KEY uk_coupon_user (coupon_id, user_id)  -- 중복 발급 방지
);
```

#### Consumer 구현

```java
@KafkaListener(topics = "coupon-issue-request")
@Transactional
public void consumeCouponIssueRequest(
        @Payload CouponIssueRequestEvent event,
        Acknowledgment ack) {

    try {
        // 쿠폰 발급 시도
        CouponUser issuedCoupon = CouponUser.create(
                event.getCouponId(), event.getUserId());
        couponUserRepository.save(issuedCoupon);

        ack.acknowledge();

    } catch (DataIntegrityViolationException e) {
        // Unique 제약조건 위반 = 중복 발급 시도
        log.warn("중복 쿠폰 발급 시도: couponId={}, userId={}",
                event.getCouponId(), event.getUserId());

        // 중복이므로 무시하고 커밋
        ack.acknowledge();
    }
}
```

**장점:**
- DB 수준에서 중복 방지 보장
- 별도 테이블 불필요
- 코드 단순화

**선택:** **ProcessedEvent 테이블** (처리 이력 추적, 감사 기능)

---

## 6. 성능 개선 지표

### 6.1 선착순 쿠폰 발급

| 지표 | 기존 (분산락) | 개선 (Kafka) | 개선율 |
|------|-------------|-------------|--------|
| **API 응답 시간** | 100-500ms | 1-5ms | **97% 개선** |
| **처리량 (req/s)** | 500 | 5000+ | **1000% 증가** |
| **DB 부하** | 40% | 20% | **50% 감소** |
| **동시 처리** | 1개 | 5개 (파티션) | **5배 증가** |
| **확장성** | 수직 확장만 | 수평 확장 가능 | **무제한** |

### 6.2 콘서트 대기열

| 지표 | 기존 (Redis Sorted Set) | 개선 (Kafka) | 개선율 |
|------|----------------------|-------------|--------|
| **대기열 용량** | 10,000명 (메모리 제한) | 무제한 | **제한 없음** |
| **순서 보장** | 99% (Redis 장애 시 손실) | 100% | **100% 보장** |
| **장애 복구** | 불가능 (메모리 소실) | 가능 (Kafka 재처리) | **완벽 복구** |
| **실시간 조회** | O(log N) | O(1) (Offset) | **즉시 조회** |

### 6.3 시스템 전체

| 항목 | 기존 | 개선 | 효과 |
|------|------|------|------|
| **데이터 정합성** | 99% (스케줄러 의존) | 99.9% (Outbox 패턴) | **신뢰성 향상** |
| **장애 복구 시간** | 10초 (스케줄러 주기) | 1초 (Kafka 재처리) | **10배 빠름** |
| **서버 확장** | 제한적 (분산락) | 자유로움 (파티션) | **수평 확장** |
| **모니터링** | 제한적 | 실시간 추적 | **가시성 향상** |

---

## 7. 구현 계획

### 7.1 Phase 1: Kafka 인프라 구축 (1주)

- ✅ Kafka 클러스터 구성 (3 Broker, 3 ZooKeeper)
- ✅ Topic 생성 및 설정
- ✅ Monitoring 도구 설정 (Kafka Manager, Prometheus)

### 7.2 Phase 2: 선착순 쿠폰 발급 구현 (1주)

- [ ] `CouponIssueRequestEvent` 도메인 이벤트 구현
- [ ] Kafka Producer 구현 (API 서버)
- [ ] Kafka Consumer 구현 (쿠폰 발급 처리)
- [ ] ProcessedEvent 테이블 및 중복 방지 로직 구현
- [ ] 통합 테스트 작성

### 7.3 Phase 3: 콘서트 대기열 구현 (1주)

- [ ] `QueueEntryEvent` 도메인 이벤트 구현
- [ ] Kafka Producer 구현 (대기열 입장)
- [ ] Kafka Consumer 구현 (순차 처리)
- [ ] WebSocket 연동 (실시간 순번 전송)
- [ ] 통합 테스트 작성

### 7.4 Phase 4: 성능 테스트 및 최적화 (1주)

- [ ] JMeter/Gatling 부하 테스트
- [ ] 성능 지표 측정
- [ ] 병목 지점 분석 및 최적화
- [ ] 장애 시나리오 테스트

---

## 부록

### A. Kafka 운영 모니터링 지표

| 지표 | 설명 | 목표 |
|------|------|------|
| **Lag** | Consumer 지연 (offset 차이) | < 1000 |
| **Throughput** | 초당 메시지 처리량 | > 5000 msg/s |
| **Error Rate** | 에러 발생 비율 | < 0.1% |
| **Rebalance** | Consumer 리밸런싱 횟수 | < 1회/일 |

### B. 참고 자료

- Kafka Streams Documentation: https://kafka.apache.org/documentation/streams/
- Idempotent Consumer Pattern: https://microservices.io/patterns/communication-style/idempotent-consumer.html
- CQRS with Kafka: https://www.confluent.io/blog/event-sourcing-cqrs-stream-processing-apache-kafka-whats-connection/

---

**작성일**: 2025-12-18
**작성자**: Step 18 설계팀
