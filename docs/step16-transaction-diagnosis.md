# Step 16: Transaction Diagnosis - 트랜잭션 분리 문제 분석 및 분산 트랜잭션 설계

## 목표
- 도메인별로 트랜잭션이 분리되었을 때 발생 가능한 문제 파악
- 트랜잭션이 분리되더라도 데이터 일관성을 보장할 수 있는 분산 트랜잭션 설계

## 1. 현재 시스템의 트랜잭션 구조 분석

### 1.1 주문 결제 플로우의 트랜잭션 경계

현재 `OrderService.processPayment()`는 하나의 트랜잭션에서 여러 도메인을 처리합니다:

```java
@Transactional
public Order processPayment(Long orderId) {
    // [단계 1] Order 조회 및 검증
    Order order = orderRepository.findById(orderId)...

    // [단계 2] OrderItem 조회
    List<OrderItem> orderItems = orderItemRepository.findByOrderId(orderId);

    // [단계 3] Product 재고 차감 (여러 상품)
    for (OrderItem item : orderItems) {
        Product product = productRepository.findById(item.getProductId())...
        product.decreaseStock(item.getQty());  // @Version 낙관적 락
        productRepository.save(product);
    }

    // [단계 4] Order 상태 변경
    order.markAsPaid();
    orderRepository.save(order);

    // [단계 5] OrderHistory 기록
    OrderHistory history = OrderHistory.create(...);
    orderHistoryRepository.save(history);

    // [단계 6] 이벤트 발행 (트랜잭션 커밋 후)
    eventPublisher.publishEvent(OrderCompletedEvent.of(...));

    return order;
}
```

**처리 도메인**: Order, OrderItem, Product, OrderHistory (4개 도메인)

## 2. 트랜잭션 분리 시 발생 가능한 문제점

### 문제 1: 부분 실패로 인한 데이터 불일치 (Partial Failure)

#### 시나리오: 재고 차감과 주문 상태 변경을 별도 트랜잭션으로 분리

```java
// [나쁜 예시] 트랜잭션 분리
@Transactional
public void decreaseStockForOrder(Long orderId) {
    List<OrderItem> items = orderItemRepository.findByOrderId(orderId);
    for (OrderItem item : items) {
        Product product = productRepository.findById(item.getProductId())...
        product.decreaseStock(item.getQty());
        productRepository.save(product);
    }
}  // 트랜잭션 커밋

@Transactional
public Order completeOrder(Long orderId) {
    Order order = orderRepository.findById(orderId)...
    order.markAsPaid();
    orderRepository.save(order);
    return order;
}  // 트랜잭션 커밋

// 호출 코드
public Order processPayment(Long orderId) {
    decreaseStockForOrder(orderId);  // ✅ 성공: 재고 차감 완료
    // ❌ 여기서 서버 장애 발생!
    completeOrder(orderId);  // 실행 안 됨
}
```

**문제점**:
- 재고는 차감되었지만 주문 상태는 PENDING 상태로 남음
- 고객은 결제를 완료하지 못했지만 재고는 줄어듦
- 데이터 일관성 깨짐 (Inconsistency)

**영향**:
- 재고 부족 오류 발생 (실제로는 재고가 있음)
- 주문 취소 시 재고 복구 로직 필요
- 운영 비용 증가

---

### 문제 2: 분산 교착 상태 (Distributed Deadlock)

#### 시나리오: 여러 서비스가 서로 다른 순서로 리소스 접근

```
[주문 A: 상품 1 → 상품 2]
트랜잭션 1:
- 상품 1 재고 차감 (락 획득)
- 상품 2 재고 차감 대기...

[주문 B: 상품 2 → 상품 1]
트랜잭션 2:
- 상품 2 재고 차감 (락 획득)
- 상품 1 재고 차감 대기...

→ 서로 대기하여 교착 상태 발생
```

**문제점**:
- 트랜잭션이 분리되면 락 타임아웃까지 대기
- 성능 저하 및 사용자 경험 악화

**해결 방안**:
- 리소스 접근 순서 표준화 (예: 상품 ID 오름차순)
- 락 타임아웃 설정
- 재시도 로직 구현

---

### 문제 3: 보상 트랜잭션 누락 (Missing Compensating Transaction)

#### 시나리오: 결제 실패 시 재고 복구 실패

```java
@Transactional
public void processPaymentSeparated(Long orderId) {
    // 1. 재고 차감 (별도 서비스)
    inventoryService.decreaseStock(orderId);  // ✅ 성공

    // 2. 결제 처리 (외부 API)
    paymentGateway.charge(orderId);  // ❌ 실패!

    // 3. 보상 트랜잭션 (재고 복구)
    // ❌ 복구 로직이 없거나 실패할 수 있음!
}
```

**문제점**:
- 재고는 차감되었지만 결제는 실패
- 보상 트랜잭션(재고 복구)이 실패하면 영구적 불일치

**필요한 보상 로직**:
- 재고 증가 (rollback)
- 주문 상태를 FAILED로 변경
- 고객에게 알림

---

### 문제 4: 이벤트 손실 (Event Loss)

#### 시나리오: 이벤트 발행 후 서버 장애

```java
@Transactional
public Order processPayment(Long orderId) {
    // 주문 처리
    Order order = ...;
    order.markAsPaid();
    orderRepository.save(order);

    // 이벤트 발행
    eventPublisher.publishEvent(OrderCompletedEvent.of(...));

    // ❌ 이벤트가 메모리에만 존재하고 DB에 저장되지 않음
    // 서버 재시작 시 이벤트 손실

    return order;
}
```

**문제점**:
- 주문은 완료되었지만 랭킹 업데이트 이벤트가 손실됨
- 재실행 불가 (영속화되지 않음)

**해결 방안**:
- Outbox 패턴: 이벤트를 DB 테이블에 먼저 저장
- 별도 워커가 이벤트 테이블을 폴링하여 발행

---

### 문제 5: 타임아웃과 재시도로 인한 중복 처리 (Duplicate Processing)

#### 시나리오: 재고 차감 서비스 타임아웃

```
클라이언트 요청
    ↓
[주문 서비스] processPayment()
    ↓
[재고 서비스] decreaseStock()
    ↓ (네트워크 지연, 30초 타임아웃)
    ❌ 타임아웃!

[주문 서비스] 재시도
    ↓
[재고 서비스] decreaseStock()
    ✅ 성공 (하지만 이미 첫 번째 요청도 성공했을 수 있음)

결과: 재고가 2배로 차감됨!
```

**문제점**:
- 타임아웃 발생 시 실제 성공 여부를 알 수 없음
- 재시도 시 중복 처리 발생

**해결 방안**:
- 멱등성 키(Idempotency Key) 사용
- 재고 차감 기록을 별도 테이블에 저장하여 중복 체크

---

### 문제 6: 읽기-수정-쓰기 경쟁 조건 (Read-Modify-Write Race)

#### 시나리오: 재고 조회와 차감 사이에 다른 트랜잭션이 개입

```
트랜잭션 1:
- 재고 조회: 100개
- 차감 계산: 100 - 10 = 90
- ⏸️ 대기...

트랜잭션 2:
- 재고 조회: 100개
- 차감 계산: 100 - 20 = 80
- ✅ 저장: 80개

트랜잭션 1:
- ✅ 저장: 90개 (잘못된 값!)

결과: 실제로는 70개여야 하지만 90개로 저장됨
```

**현재 해결책**:
- @Version 낙관적 락 사용
- OptimisticLockException 발생 시 재시도

---

## 3. 분산 트랜잭션 설계 원칙

### 3.1 ACID vs. BASE

#### ACID (전통적 단일 DB 트랜잭션)
- **Atomicity**: 모두 성공 또는 모두 실패
- **Consistency**: 일관성 있는 상태 유지
- **Isolation**: 트랜잭션 간 격리
- **Durability**: 영구 저장

#### BASE (분산 시스템)
- **Basically Available**: 기본적으로 가용
- **Soft state**: 일시적 불일치 허용
- **Eventually consistent**: 최종 일관성 보장

**현재 시스템**: ACID (단일 트랜잭션) + BASE (이벤트 기반)

---

### 3.2 적용 가능한 패턴

#### 패턴 1: Saga 패턴 (Choreography vs. Orchestration)

##### Choreography (이벤트 기반)
```
주문 생성 → 주문 생성 이벤트 발행
    ↓
재고 서비스 → 재고 차감 이벤트 발행
    ↓
결제 서비스 → 결제 완료 이벤트 발행
    ↓
알림 서비스 → 알림 전송

실패 시:
결제 실패 이벤트 → 재고 복구 이벤트 → 주문 취소 이벤트
```

**장점**:
- 서비스 간 느슨한 결합
- 확장성 우수

**단점**:
- 전체 흐름 파악 어려움
- 디버깅 복잡

##### Orchestration (중앙 조정자)
```
[주문 Saga Orchestrator]
    ├─ 1. 주문 생성 → 재고 서비스.차감()
    │   ├─ 성공 → 2단계 진행
    │   └─ 실패 → Saga 종료
    ├─ 2. 결제 처리 → 결제 서비스.charge()
    │   ├─ 성공 → 3단계 진행
    │   └─ 실패 → 보상: 재고 복구
    └─ 3. 알림 전송 → 알림 서비스.send()
        ├─ 성공 → Saga 완료
        └─ 실패 → 로그만 기록 (중요도 낮음)
```

**장점**:
- 전체 흐름 명확
- 디버깅 쉬움

**단점**:
- 중앙 조정자가 단일 장애점
- 서비스 간 결합도 증가

---

#### 패턴 2: Outbox 패턴 (이벤트 영속화)

현재 문제:
```java
@Transactional
public Order processPayment(Long orderId) {
    // DB 작업
    order.markAsPaid();
    orderRepository.save(order);

    // 이벤트 발행 (메모리)
    eventPublisher.publishEvent(OrderCompletedEvent.of(...));

    // 서버 장애 시 이벤트 손실!
}
```

Outbox 패턴 적용:
```java
@Transactional
public Order processPayment(Long orderId) {
    // 1. 주문 처리
    order.markAsPaid();
    orderRepository.save(order);

    // 2. 이벤트를 DB에 저장
    OutboxEvent event = OutboxEvent.create(
        "OrderCompleted",
        orderId,
        JsonUtil.toJson(OrderCompletedEvent.of(...))
    );
    outboxRepository.save(event);

    // 트랜잭션 커밋: 주문과 이벤트가 함께 저장됨
    return order;
}

// 별도 워커
@Scheduled(fixedDelay = 1000)
public void publishPendingEvents() {
    List<OutboxEvent> events = outboxRepository.findPendingEvents();
    for (OutboxEvent event : events) {
        try {
            eventPublisher.publishEvent(event.getPayload());
            event.markAsPublished();
            outboxRepository.save(event);
        } catch (Exception e) {
            event.incrementRetryCount();
            outboxRepository.save(event);
        }
    }
}
```

**장점**:
- 이벤트 손실 방지
- 재시도 가능
- At-least-once 전송 보장

**단점**:
- 복잡도 증가
- 중복 처리 가능 (멱등성 필요)

---

#### 패턴 3: 보상 트랜잭션 (Compensating Transaction)

```java
@Service
public class OrderSagaService {

    public Order processPaymentWithCompensation(Long orderId) {
        try {
            // 1단계: 재고 차감
            decreaseStock(orderId);

            try {
                // 2단계: 결제 처리
                processPayment(orderId);

                try {
                    // 3단계: 주문 완료
                    completeOrder(orderId);
                    return getOrder(orderId);

                } catch (Exception e) {
                    // 보상: 결제 취소
                    cancelPayment(orderId);
                    throw e;
                }

            } catch (Exception e) {
                // 보상: 재고 복구
                restoreStock(orderId);
                throw e;
            }

        } catch (Exception e) {
            // 보상: 주문 취소
            cancelOrder(orderId);
            throw new OrderProcessingException("주문 처리 실패", e);
        }
    }

    @Transactional
    private void decreaseStock(Long orderId) { ... }

    @Transactional
    private void restoreStock(Long orderId) {
        // 재고 증가 로직
    }
}
```

**장점**:
- 명확한 롤백 절차
- 디버깅 쉬움

**단점**:
- 보상 트랜잭션도 실패할 수 있음
- 코드 복잡도 증가

---

#### 패턴 4: Two-Phase Commit (2PC) - 사용하지 않음

분산 트랜잭션의 전통적 방법이지만 현대 MSA에서는 권장되지 않습니다:

**문제점**:
- 코디네이터가 단일 장애점
- 긴 락 시간으로 성능 저하
- 확장성 제한

---

## 4. 현재 시스템에 적용할 설계

### 4.1 주문 결제 플로우: Saga 패턴 (Orchestration)

```
[OrderPaymentSaga]
    ↓
[1단계] 주문 검증
    ├─ Order 상태 확인 (PENDING)
    └─ 만료 여부 체크
    ↓
[2단계] 재고 확보
    ├─ Product 재고 차감 (낙관적 락)
    ├─ 성공 → 다음 단계
    └─ 실패 → Saga 종료, 주문 취소
    ↓
[3단계] 결제 처리 (가정)
    ├─ 사용자 잔액 확인
    ├─ 잔액 차감
    ├─ 성공 → 다음 단계
    └─ 실패 → 보상: 재고 복구, 주문 취소
    ↓
[4단계] 주문 완료
    ├─ Order 상태 변경 (PAID)
    ├─ OrderHistory 기록
    └─ Outbox 이벤트 저장
    ↓
[5단계] 후처리 (비동기)
    └─ 랭킹 업데이트 (이벤트 리스너)
```

---

### 4.2 Outbox 패턴 적용

#### OutboxEvent 엔티티 설계

```java
@Entity
@Table(name = "outbox_event")
public class OutboxEvent {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String eventType;  // "OrderCompleted", "CouponIssued", etc.

    @Column(nullable = false)
    private String aggregateId;  // 주문 ID, 쿠폰 ID 등

    @Column(columnDefinition = "TEXT", nullable = false)
    private String payload;  // JSON 형태의 이벤트 데이터

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OutboxStatus status;  // PENDING, PUBLISHED, FAILED

    @Column(nullable = false)
    private Integer retryCount = 0;

    private LocalDateTime publishedAt;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    // getters, setters, factory methods
}

enum OutboxStatus {
    PENDING,    // 발행 대기
    PUBLISHED,  // 발행 완료
    FAILED      // 발행 실패 (최대 재시도 초과)
}
```

---

### 4.3 보상 트랜잭션 설계

#### OrderCompensation 서비스

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
            Product product = productRepository.findById(item.getProductId())
                .orElseThrow();
            product.increaseStock(item.getQty());
            productRepository.save(product);
        }
        log.info("재고 복구 완료: orderId={}", orderId);
    }

    /**
     * 주문 취소 처리
     */
    @Transactional
    public void cancelOrder(Long orderId, String reason) {
        Order order = orderRepository.findById(orderId).orElseThrow();
        order.cancel();
        orderRepository.save(order);

        OrderHistory history = OrderHistory.create(
            orderId,
            order.getStatus(),
            OrderStatus.CANCELLED,
            reason,
            ActorType.SYSTEM
        );
        orderHistoryRepository.save(history);

        log.info("주문 취소 완료: orderId={}, reason={}", orderId, reason);
    }
}
```

---

## 5. 데이터 일관성 보장 전략

### 5.1 강한 일관성 (Strong Consistency)

**적용 대상**: 핵심 비즈니스 로직
- 주문 생성 + 주문 항목 저장 → 단일 트랜잭션
- 재고 차감 + 주문 상태 변경 → 단일 트랜잭션

**방법**:
- ACID 트랜잭션 유지
- 낙관적 락으로 동시성 제어

---

### 5.2 최종 일관성 (Eventual Consistency)

**적용 대상**: 부가 기능
- 랭킹 업데이트
- 통계 집계
- 알림 전송

**방법**:
- 이벤트 기반 비동기 처리
- Outbox 패턴으로 at-least-once 보장
- 멱등성으로 중복 처리 방지

---

### 5.3 멱등성 보장 (Idempotency)

#### 멱등성 키 테이블 설계

```java
@Entity
@Table(name = "idempotency_key")
public class IdempotencyKey {
    @Id
    private String key;  // UUID 또는 "{userId}:{action}:{timestamp}"

    @Column(nullable = false)
    private String action;  // "processPayment", "issueCoupon", etc.

    @Column
    private String result;  // JSON 형태의 결과 (재사용)

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime expiresAt;
}
```

#### 사용 예시

```java
@Transactional
public Order processPaymentIdempotent(Long orderId, String idempotencyKey) {
    // 1. 멱등성 키 확인
    Optional<IdempotencyKey> existing = idempotencyKeyRepository
        .findById(idempotencyKey);

    if (existing.isPresent()) {
        // 이미 처리된 요청
        return JsonUtil.fromJson(existing.get().getResult(), Order.class);
    }

    // 2. 실제 처리
    Order order = processPayment(orderId);

    // 3. 멱등성 키 저장
    IdempotencyKey key = IdempotencyKey.create(
        idempotencyKey,
        "processPayment",
        JsonUtil.toJson(order),
        LocalDateTime.now().plusHours(24)
    );
    idempotencyKeyRepository.save(key);

    return order;
}
```

---

## 6. 실패 복구 전략

### 6.1 재시도 (Retry)

```java
@Retryable(
    value = {OptimisticLockException.class},
    maxAttempts = 3,
    backoff = @Backoff(delay = 100, multiplier = 2)
)
public Order processPaymentWithRetry(Long orderId) {
    return processPayment(orderId);
}
```

---

### 6.2 Circuit Breaker

```java
@CircuitBreaker(name = "paymentService", fallbackMethod = "fallbackPayment")
public PaymentResult callPaymentGateway(Long orderId) {
    // 외부 결제 API 호출
}

public PaymentResult fallbackPayment(Long orderId, Exception e) {
    // Fallback: 주문을 PENDING_PAYMENT 상태로 유지
    log.error("결제 API 장애: orderId={}", orderId, e);
    return PaymentResult.pending();
}
```

---

### 6.3 Dead Letter Queue (DLQ)

```java
@Service
public class OutboxEventPublisher {

    private static final int MAX_RETRIES = 5;

    @Scheduled(fixedDelay = 5000)
    public void publishPendingEvents() {
        List<OutboxEvent> events = outboxRepository
            .findByStatusAndRetryCountLessThan(OutboxStatus.PENDING, MAX_RETRIES);

        for (OutboxEvent event : events) {
            try {
                eventPublisher.publishEvent(deserialize(event.getPayload()));
                event.markAsPublished();
                outboxRepository.save(event);
            } catch (Exception e) {
                event.incrementRetryCount();

                if (event.getRetryCount() >= MAX_RETRIES) {
                    // DLQ로 이동
                    event.moveToDLQ();
                    log.error("이벤트 발행 실패, DLQ 이동: eventId={}", event.getId(), e);
                }

                outboxRepository.save(event);
            }
        }
    }
}
```

---

## 7. 모니터링 및 알림

### 7.1 트랜잭션 실패 추적

```java
@Aspect
@Component
public class TransactionMonitoringAspect {

    @AfterThrowing(
        pointcut = "@annotation(org.springframework.transaction.annotation.Transactional)",
        throwing = "ex"
    )
    public void logTransactionFailure(JoinPoint joinPoint, Exception ex) {
        String method = joinPoint.getSignature().toShortString();
        log.error("트랜잭션 실패: method={}, exception={}", method, ex.getClass().getSimpleName());

        // 모니터링 시스템에 메트릭 전송
        metricsService.incrementCounter("transaction.failure",
            Tags.of("method", method, "exception", ex.getClass().getSimpleName()));
    }
}
```

---

### 7.2 보상 트랜잭션 추적

```java
@Entity
@Table(name = "compensation_log")
public class CompensationLog {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String sagaId;  // Saga 인스턴스 식별자

    @Column(nullable = false)
    private String compensationType;  // "RESTORE_STOCK", "CANCEL_ORDER", etc.

    @Column(nullable = false)
    private String targetId;  // 대상 엔티티 ID (주문 ID, 상품 ID 등)

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CompensationStatus status;  // SUCCESS, FAILED

    @Column(columnDefinition = "TEXT")
    private String errorMessage;

    @Column(nullable = false)
    private LocalDateTime createdAt;
}
```

---

## 8. 결론

### 8.1 트랜잭션 분리 시 주요 문제점

1. **부분 실패**: 재고는 차감되었지만 주문은 미완료
2. **분산 교착 상태**: 서로 다른 순서로 리소스 접근
3. **보상 트랜잭션 누락**: 실패 시 원상 복구 안 됨
4. **이벤트 손실**: 서버 장애 시 이벤트 영속화 필요
5. **중복 처리**: 재시도 시 멱등성 필요
6. **경쟁 조건**: 낙관적 락으로 방어

### 8.2 데이터 일관성 보장 전략

1. **핵심 로직**: 단일 트랜잭션 유지 (ACID)
2. **부가 기능**: 이벤트 기반 최종 일관성 (BASE)
3. **Outbox 패턴**: 이벤트 영속화로 at-least-once 보장
4. **Saga 패턴**: 보상 트랜잭션으로 롤백 구현
5. **멱등성**: 중복 처리 방지
6. **재시도 + Circuit Breaker**: 일시적 장애 대응

### 8.3 다음 단계

- Outbox 패턴 구현
- Saga Orchestrator 구현
- 보상 트랜잭션 구현
- 멱등성 키 관리
- 실패 시나리오 통합 테스트
