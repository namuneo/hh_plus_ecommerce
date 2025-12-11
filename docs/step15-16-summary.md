# Step 15-16 종합 구현 보고서: Application Event & Transaction Diagnosis

## 프로젝트 개요

이커머스 시스템에서 **트랜잭션 분리**와 **데이터 일관성 보장**을 위한 이벤트 기반 아키텍처 및 분산 트랜잭션 패턴을 구현했습니다.

## Step 15: Application Event

### 목표
- 주문/예약 정보를 원 트랜잭션이 종료된 이후에 전송
- 주문/예약 정보를 전달하는 부가 로직에 대한 관심사를 메인 서비스에서 분리

### 구현 내용

#### 1. 도메인 이벤트 설계

**OrderCompletedEvent** (`src/main/java/sample/hhplus_w2/domain/order/event/OrderCompletedEvent.java`)
```java
@Getter
public class OrderCompletedEvent {
    private final Long orderId;
    private final Long userId;
    private final List<OrderItemSnapshot> orderItems;
    private final LocalDateTime completedAt;

    // JSON 직렬화 지원
    @JsonCreator
    private OrderCompletedEvent(...) { ... }

    // 주문 항목 스냅샷 (엔티티 의존성 제거)
    @Getter
    public static class OrderItemSnapshot {
        private final Long productId;
        private final Integer qty;
    }
}
```

**특징:**
- JSON 직렬화 가능 (`@JsonCreator`, `@JsonProperty`)
- 불변 객체로 설계
- OrderItem 엔티티 대신 OrderItemSnapshot으로 데이터 전달

#### 2. 이벤트 리스너 구현

**OrderEventListener** (`src/main/java/sample/hhplus_w2/application/listener/OrderEventListener.java`)
```java
@Component
@RequiredArgsConstructor
public class OrderEventListener {

    private final ProductRankingService rankingService;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void handleOrderCompleted(OrderCompletedEvent event) {
        // 랭킹 업데이트 (원 트랜잭션과 독립적)
        for (OrderItemSnapshot item : event.getOrderItems()) {
            rankingService.incrementProductOrder(item.getProductId(), item.getQty());
        }
    }
}
```

**핵심 어노테이션:**
- `@TransactionalEventListener(phase = AFTER_COMMIT)`: 원 트랜잭션 커밋 후 실행
- `@Async`: 비동기 실행 (주문 응답 시간에 영향 없음)
- `@Transactional(propagation = REQUIRES_NEW)`: 새로운 트랜잭션 (독립적)

#### 3. 비동기 처리 설정

**AsyncConfig** (`src/main/java/sample/hhplus_w2/config/AsyncConfig.java`)
```java
@Configuration
public class AsyncConfig implements AsyncConfigurer {
    @Override
    public Executor getAsyncExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(5);
        executor.setMaxPoolSize(10);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("event-async-");
        executor.initialize();
        return executor;
    }
}
```

**HhplusW2Application**에 `@EnableAsync` 추가

### Step 15 성과

✅ **관심사 분리**: OrderService에서 랭킹 업데이트 의존성 제거
✅ **트랜잭션 독립성**: 랭킹 업데이트 실패가 주문 완료에 영향 없음
✅ **성능 향상**: 비동기 처리로 주문 응답 시간 단축
✅ **확장성**: 새로운 이벤트 리스너 추가 시 OrderService 수정 불필요

---

## Step 16: Transaction Diagnosis

### 목표
- 도메인별로 트랜잭션이 분리되었을 때 발생 가능한 문제 파악
- 트랜잭션이 분리되더라도 데이터 일관성을 보장할 수 있는 분산 트랜잭션 설계

### 트랜잭션 분리 시 발생 가능한 문제 (6가지)

#### 문제 1: 부분 실패 (Partial Failure)
```
재고 차감 트랜잭션 ✅ → 서버 장애 → 주문 완료 트랜잭션 ❌
결과: 재고는 차감되었지만 주문은 미완료 (데이터 불일치)
```

#### 문제 2: 분산 교착 상태 (Distributed Deadlock)
```
트랜잭션 A: 상품1 락 획득 → 상품2 대기
트랜잭션 B: 상품2 락 획득 → 상품1 대기
결과: 서로 대기하여 교착 상태 발생
```

#### 문제 3: 보상 트랜잭션 누락
```
재고 차감 ✅ → 결제 실패 ❌ → 재고 복구 누락
결과: 재고는 차감되었지만 결제는 실패 (영구적 불일치)
```

#### 문제 4: 이벤트 손실
```
주문 처리 ✅ → 이벤트 발행 (메모리) → 서버 재시작 → 이벤트 손실
결과: 주문은 완료되었지만 랭킹 업데이트 이벤트 손실
```

#### 문제 5: 타임아웃과 중복 처리
```
재고 차감 요청 → 타임아웃 → 재시도 → 재고 2배 차감
결과: 실제 필요 수량의 2배가 차감됨
```

#### 문제 6: 읽기-수정-쓰기 경쟁 조건
```
TX1: 재고 조회(100) → 계산(100-10=90) → 저장(90)
TX2: 재고 조회(100) → 계산(100-20=80) → 저장(80) ✅
TX1: 저장(90) ✅ (잘못된 값!)
결과: 실제로는 70이어야 하지만 90으로 저장됨
```

### 구현한 해결 방안

#### 1. Outbox 패턴 (이벤트 손실 방지)

**OutboxEvent 엔티티** (`src/main/java/sample/hhplus_w2/domain/outbox/OutboxEvent.java`)
```java
@Entity
@Table(name = "outbox_event")
public class OutboxEvent {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String eventType;      // "OrderCompleted"
    private String aggregateId;    // 주문 ID
    private String payload;        // JSON 이벤트 데이터
    private OutboxStatus status;   // PENDING, PUBLISHED, FAILED
    private Integer retryCount;    // 재시도 횟수
    private LocalDateTime publishedAt;
    private LocalDateTime createdAt;
}
```

**OutboxEventPublisher** (`src/main/java/sample/hhplus_w2/application/outbox/OutboxEventPublisher.java`)
```java
@Service
public class OutboxEventPublisher {
    private static final int MAX_RETRIES = 5;

    @Scheduled(fixedDelay = 5000)  // 5초마다 실행
    public void publishPendingEvents() {
        List<OutboxEvent> pendingEvents = outboxEventRepository
            .findPendingEvents(MAX_RETRIES);

        for (OutboxEvent event : pendingEvents) {
            try {
                Object domainEvent = deserializeEvent(event);
                eventPublisher.publishEvent(domainEvent);
                event.markAsPublished();
            } catch (Exception e) {
                event.incrementRetryCount();
                if (event.getRetryCount() >= MAX_RETRIES) {
                    event.moveToDLQ();  // Dead Letter Queue로 이동
                }
            }
            outboxEventRepository.save(event);
        }
    }
}
```

**OrderService에서 Outbox 사용** (`src/main/java/sample/hhplus_w2/service/order/OrderService.java:114-129`)
```java
@Transactional
public Order processPayment(Long orderId) {
    // 1. 주문 검증, 재고 차감, 상태 변경
    // ...

    // 2. Outbox 이벤트 저장 (원자성 보장)
    OrderCompletedEvent event = OrderCompletedEvent.of(...);
    String payload = objectMapper.writeValueAsString(event);
    OutboxEvent outboxEvent = OutboxEvent.create(
        "OrderCompleted",
        String.valueOf(orderId),
        payload
    );
    outboxEventRepository.save(outboxEvent);

    // 트랜잭션 커밋: 주문 + Outbox 이벤트 함께 저장됨
    return order;
}
```

**장점:**
- 이벤트가 DB에 저장되어 손실 방지
- 서버 재시작해도 PENDING 이벤트 재발행
- at-least-once 전송 보장
- DLQ로 실패 이벤트 관리

#### 2. 보상 트랜잭션 (Compensating Transaction)

**OrderCompensationService** (`src/main/java/sample/hhplus_w2/application/compensation/OrderCompensationService.java`)
```java
@Service
public class OrderCompensationService {

    /**
     * 재고 복구 (주문 취소 또는 결제 실패 시)
     */
    @Transactional
    public void restoreStock(Long orderId) {
        List<OrderItem> items = orderItemRepository.findByOrderId(orderId);
        for (OrderItem item : items) {
            Product product = productRepository.findById(item.getProductId())...
            product.increaseStock(item.getQty());  // 보상 작업
            productRepository.save(product);
        }
    }

    /**
     * 주문 취소 처리
     */
    @Transactional
    public void cancelOrder(Long orderId, String reason) {
        Order order = orderRepository.findById(orderId)...
        order.cancel();
        orderRepository.save(order);

        OrderHistory history = OrderHistory.create(
            orderId,
            previousStatus,
            OrderStatus.CANCELLED,
            reason,
            ActorType.SYSTEM
        );
        orderHistoryRepository.save(history);
    }

    /**
     * 주문 취소 및 재고 복구 (원자적 보상)
     */
    @Transactional
    public void cancelOrderWithStockRestore(Long orderId, String reason) {
        restoreStock(orderId);      // 1. 재고 복구
        cancelOrder(orderId, reason);  // 2. 주문 취소
    }
}
```

**사용 시나리오:**
```java
try {
    // 1. 재고 차감
    decreaseStock(orderId);

    // 2. 결제 처리
    processPayment(orderId);

    // 3. 주문 완료
    completeOrder(orderId);

} catch (PaymentFailureException e) {
    // 보상: 재고 복구 + 주문 취소
    compensationService.cancelOrderWithStockRestore(orderId, "결제 실패");
}
```

#### 3. 기존 동시성 제어 메커니즘 유지

**낙관적 락 (@Version)**
```java
@Entity
public class Product {
    @Version
    private Integer version;

    public void decreaseStock(Integer qty) {
        if (this.stockQty < qty) {
            throw new IllegalStateException("재고가 부족합니다.");
        }
        this.stockQty -= qty;
        // version은 JPA가 자동 증가
    }
}
```

**분산락 (Redis SETNX)** - 기존 구현 유지
```java
public void decreaseStockWithDistributedLock(Long productId, Integer quantity) {
    String lockKey = "product:stock:" + productId;
    distributedLock.executeWithLock(lockKey, Duration.ofSeconds(5), () -> {
        decreaseStockInTransaction(productId, quantity);
    });
}
```

### Step 16 성과

✅ **이벤트 손실 방지**: Outbox 패턴으로 at-least-once 보장
✅ **보상 트랜잭션**: 재고 복구, 주문 취소 등 롤백 메커니즘 구현
✅ **데이터 일관성**: 트랜잭션 분리 환경에서도 최종 일관성 보장
✅ **장애 대응**: 재시도, DLQ로 실패 이벤트 관리

---

## 전체 아키텍처 흐름

### 주문 결제 플로우 (Outbox 패턴 적용)

```
[클라이언트 요청]
    ↓
[OrderService.processPayment()]
    ├─ @Transactional 시작
    │
    ├─ [1단계] 주문 검증
    │   └─ Order 상태 확인, 만료 체크
    │
    ├─ [2단계] 재고 차감
    │   ├─ Product.decreaseStock() (@Version 낙관적 락)
    │   └─ OptimisticLockException → 재시도 필요
    │
    ├─ [3단계] 주문 상태 변경
    │   ├─ Order.markAsPaid()
    │   └─ OrderHistory 저장
    │
    ├─ [4단계] Outbox 이벤트 저장
    │   ├─ OrderCompletedEvent 생성
    │   ├─ JSON 직렬화
    │   └─ OutboxEvent 저장 (PENDING)
    │
    └─ @Transactional 커밋 ✅
        └─ Order + Product + OrderHistory + OutboxEvent
           모두 원자적으로 커밋됨
    ↓
[OutboxEventPublisher (5초마다)]
    ├─ PENDING 이벤트 조회
    ├─ JSON 역직렬화 → OrderCompletedEvent
    ├─ ApplicationEventPublisher.publishEvent()
    ├─ 성공 → PUBLISHED
    └─ 실패 → retryCount++, DLQ 이동
    ↓
[OrderEventListener]
    ├─ @TransactionalEventListener(AFTER_COMMIT)
    ├─ @Async (비동기 스레드)
    ├─ @Transactional(REQUIRES_NEW) 시작
    ├─ rankingService.incrementProductOrder()
    └─ 트랜잭션 커밋 (주문과 독립적)
```

### 실패 시나리오 및 보상 플로우

```
[정상 케이스]
주문 검증 → 재고 차감 → 상태 변경 → Outbox 저장 → 커밋 ✅

[재고 부족]
주문 검증 → 재고 차감 ❌ → 전체 롤백

[낙관적 락 충돌]
주문 검증 → 재고 차감 (OptimisticLockException) → 재시도

[결제 실패 (외부 API)]
주문 검증 → 재고 차감 ✅ → 결제 API ❌
    ↓
    보상: compensationService.cancelOrderWithStockRestore()
        ├─ restoreStock() (재고 복구)
        └─ cancelOrder() (주문 취소)

[Outbox 발행 실패]
주문 커밋 ✅ → OutboxEventPublisher → 실패 (1회)
    ↓
    5초 후 재시도 (최대 5회)
    ↓
    5회 초과 → DLQ 이동 (수동 처리)

[이벤트 리스너 실패]
주문 커밋 ✅ → 이벤트 발행 ✅ → 랭킹 업데이트 ❌
    ↓
    로그 기록 (주문은 이미 완료 상태 유지)
```

---

## 데이터 일관성 보장 전략

### 1. 강한 일관성 (Strong Consistency) - ACID

**적용 대상**: 핵심 비즈니스 로직
- 주문 생성 + 주문 항목 저장
- 재고 차감 + 주문 상태 변경

**방법**:
- 단일 `@Transactional` 범위 유지
- @Version 낙관적 락으로 동시성 제어

**예시**:
```java
@Transactional
public Order processPayment(Long orderId) {
    // 재고 차감, 주문 상태 변경, Outbox 저장
    // 모두 하나의 트랜잭션에서 원자적으로 처리
}
```

### 2. 최종 일관성 (Eventual Consistency) - BASE

**적용 대상**: 부가 기능
- 랭킹 업데이트
- 통계 집계
- 알림 전송

**방법**:
- Outbox 패턴 + 비동기 이벤트
- at-least-once 전송 보장
- 멱등성으로 중복 처리 방지 (향후 구현)

**예시**:
```java
@Async
@TransactionalEventListener(phase = AFTER_COMMIT)
@Transactional(propagation = REQUIRES_NEW)
public void handleOrderCompleted(OrderCompletedEvent event) {
    // 랭킹 업데이트 (주문과 독립적)
    // 실패해도 주문은 이미 완료된 상태
}
```

---

## 구현 파일 목록

### Step 15 (Application Event)
| 파일 | 설명 |
|------|------|
| `OrderCompletedEvent.java` | 도메인 이벤트 (JSON 직렬화 지원) |
| `OrderEventListener.java` | 이벤트 리스너 (비동기, 트랜잭션 분리) |
| `AsyncConfig.java` | 비동기 스레드 풀 설정 |
| `OrderEventListenerTest.java` | 통합 테스트 (Awaitility) |

### Step 16 (Transaction Diagnosis)
| 파일 | 설명 |
|------|------|
| `step16-transaction-diagnosis.md` | 문제 분석 및 설계 문서 |
| `OutboxEvent.java` | Outbox 이벤트 엔티티 |
| `OutboxStatus.java` | 이벤트 상태 (PENDING, PUBLISHED, FAILED) |
| `OutboxEventRepository.java` | Outbox Repository |
| `OutboxEventPublisher.java` | 주기적 이벤트 발행 (Scheduled) |
| `OrderCompensationService.java` | 보상 트랜잭션 (재고 복구, 주문 취소) |
| `OrderService.java (수정)` | Outbox 패턴 적용 |

---

## 테스트 전략

### 1. 단위 테스트
- OutboxEvent 엔티티 상태 전이
- OrderCompensationService 보상 로직

### 2. 통합 테스트
- Outbox 패턴: 이벤트 저장 → 발행 → 리스너 처리
- 보상 트랜잭션: 재고 차감 → 실패 → 재고 복구

### 3. 실패 시나리오 테스트 (향후 구현)
- 재고 부족 시 주문 실패
- 결제 실패 시 보상 트랜잭션
- Outbox 발행 실패 시 재시도
- 최대 재시도 초과 시 DLQ 이동

---

## 향후 개선 방안

### 1. 멱등성 보장 (Idempotency)
```java
@Entity
public class IdempotencyKey {
    @Id
    private String key;  // UUID
    private String action;  // "processPayment"
    private String result;  // JSON 결과 (재사용)
    private LocalDateTime expiresAt;
}
```

### 2. Saga Orchestrator
```java
@Service
public class OrderSagaOrchestrator {
    public Order processOrderSaga(Long orderId) {
        try {
            validateOrder(orderId);
            decreaseStock(orderId);
            processPayment(orderId);
            completeOrder(orderId);
        } catch (Exception e) {
            compensate(orderId, e);
        }
    }
}
```

### 3. Circuit Breaker (외부 API)
```java
@CircuitBreaker(name = "paymentService", fallbackMethod = "fallbackPayment")
public PaymentResult callPaymentGateway(Long orderId) {
    // 외부 결제 API 호출
}
```

### 4. 보상 로그 추적
```java
@Entity
public class CompensationLog {
    private String sagaId;
    private String compensationType;  // "RESTORE_STOCK", "CANCEL_ORDER"
    private String targetId;
    private CompensationStatus status;  // SUCCESS, FAILED
    private String errorMessage;
}
```

---

## 결론

### Step 15 핵심 성과
- ✅ 트랜잭션 분리: 주문 완료와 랭킹 업데이트 독립적으로 처리
- ✅ 비동기 처리: 성능 향상 및 응답 시간 단축
- ✅ 관심사 분리: OrderService의 단일 책임 원칙 준수

### Step 16 핵심 성과
- ✅ 문제 진단: 트랜잭션 분리 시 6가지 주요 문제 분석
- ✅ Outbox 패턴: 이벤트 손실 방지, at-least-once 보장
- ✅ 보상 트랜잭션: 재고 복구, 주문 취소 등 롤백 메커니즘
- ✅ 데이터 일관성: 강한 일관성(ACID) + 최종 일관성(BASE) 조합

### 달성한 목표
1. **이벤트 기반 아키텍처**: 도메인 간 느슨한 결합
2. **분산 트랜잭션 패턴**: Outbox + 보상 트랜잭션으로 데이터 일관성 보장
3. **장애 대응**: 재시도, DLQ, 보상 로직으로 시스템 안정성 향상
4. **확장성**: 새로운 이벤트 리스너 추가 시 기존 코드 수정 불필요

### 운영 고려사항
- Outbox 테이블 주기적 정리 (PUBLISHED 이벤트 보관 정책)
- DLQ 이벤트 모니터링 및 수동 재처리
- 보상 트랜잭션 실패 시 알림 및 로그 추적
- 멱등성 키 테이블 TTL 관리

이 구현으로 **트랜잭션 분리 환경에서도 데이터 일관성을 보장**하며, **이벤트 손실 없이 안정적으로 비동기 처리**할 수 있는 시스템을 구축했습니다.