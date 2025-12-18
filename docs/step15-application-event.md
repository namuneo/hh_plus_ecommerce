# Step 15: Application Event 구현 보고서

## 목표
- 주문/예약 정보를 원 트랜잭션이 종료된 이후에 전송
- 주문/예약 정보를 전달하는 부가 로직에 대한 관심사를 메인 서비스에서 분리

## 구현 개요

Spring의 Application Event와 `@TransactionalEventListener`를 활용하여 주문 완료 후 부가 로직(랭킹 업데이트)을 비동기로 분리하였습니다.

## 핵심 구현 사항

### 1. 도메인 이벤트 정의

**파일**: `src/main/java/sample/hhplus_w2/domain/order/event/OrderCompletedEvent.java`

```java
@Getter
public class OrderCompletedEvent {
    private final Long orderId;
    private final Long userId;
    private final List<OrderItem> orderItems;
    private final LocalDateTime completedAt;

    public static OrderCompletedEvent of(Long orderId, Long userId, List<OrderItem> orderItems) {
        return new OrderCompletedEvent(orderId, userId, orderItems, LocalDateTime.now());
    }
}
```

**특징**:
- 불변 객체로 설계하여 이벤트 데이터 안정성 보장
- 주문 완료에 필요한 최소한의 정보만 포함
- 정적 팩토리 메서드로 생성 편의성 제공

### 2. 이벤트 리스너 구현

**파일**: `src/main/java/sample/hhplus_w2/application/listener/OrderEventListener.java`

```java
@Component
@RequiredArgsConstructor
public class OrderEventListener {

    private final ProductRankingService rankingService;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void handleOrderCompleted(OrderCompletedEvent event) {
        try {
            for (OrderItem item : event.getOrderItems()) {
                rankingService.incrementProductOrder(item.getProductId(), item.getQty());
            }
            log.info("랭킹 업데이트 완료: orderId={}", event.getOrderId());
        } catch (Exception e) {
            log.error("랭킹 업데이트 실패: orderId={}", event.getOrderId(), e);
        }
    }
}
```

**핵심 어노테이션**:
- `@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)`: 원 트랜잭션 커밋 후 실행
- `@Async`: 비동기로 실행하여 주문 처리 성능에 영향 없음
- `@Transactional(propagation = Propagation.REQUIRES_NEW)`: 새로운 트랜잭션에서 독립적으로 실행

**장점**:
1. **트랜잭션 분리**: 주문 트랜잭션과 랭킹 업데이트 트랜잭션이 완전히 독립적
2. **실패 격리**: 랭킹 업데이트 실패가 주문 완료에 영향을 주지 않음
3. **성능 향상**: 비동기 처리로 주문 응답 시간 단축

### 3. OrderService 수정

**파일**: `src/main/java/sample/hhplus_w2/service/order/OrderService.java:106-109`

**변경 전**:
```java
// 랭킹 업데이트: 주문 완료 시 상품별 주문 수량 증가
for (OrderItem item : orderItems) {
    rankingService.incrementProductOrder(item.getProductId(), item.getQty());
}
```

**변경 후**:
```java
// 주문 완료 이벤트 발행 (트랜잭션 커밋 후 비동기로 처리됨)
OrderCompletedEvent event = OrderCompletedEvent.of(order.getId(), order.getUserId(), orderItems);
eventPublisher.publishEvent(event);
```

**개선 효과**:
- `ProductRankingService` 의존성 제거 → 관심사 분리
- 동기적 랭킹 업데이트 → 비동기 이벤트 발행으로 전환
- 주문 서비스의 책임 단순화

### 4. 비동기 처리 설정

**파일**: `src/main/java/sample/hhplus_w2/config/AsyncConfig.java`

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

**설정 값**:
- Core Pool Size: 5 (기본 스레드 수)
- Max Pool Size: 10 (최대 스레드 수)
- Queue Capacity: 100 (대기 큐 크기)

**Application 클래스**: `@EnableAsync` 추가

## 트랜잭션 분리 흐름

```
[주문 트랜잭션]
1. 주문 검증
2. 재고 차감
3. 주문 상태 변경 (PAID)
4. 주문 이력 저장
5. 이벤트 발행 (메모리에만 저장)
6. 트랜잭션 커밋 ✅

↓ (트랜잭션 커밋 완료 후)

[이벤트 리스너 - 새로운 트랜잭션]
7. 비동기 스레드에서 이벤트 처리
8. 랭킹 업데이트 (Redis)
9. 성공/실패 로그 기록
```

## 테스트 검증

### 1. 기본 동작 테스트
**파일**: `src/test/java/sample/hhplus_w2/service/order/OrderServiceTest.java`

- 주문 생성/결제 기본 흐름 검증
- 재고 차감 정확성 검증
- 예외 상황 처리 검증

### 2. 이벤트 통합 테스트
**파일**: `src/test/java/sample/hhplus_w2/application/listener/OrderEventListenerTest.java`

```java
@Test
void orderCompletedEvent_UpdatesRanking_Asynchronously() {
    // given: 상품 및 주문 준비

    // when: 결제 처리
    orderService.processPayment(order.getId());

    // then: 주문은 즉시 완료, 랭킹은 비동기로 업데이트
    assertThat(completedOrder.getStatus()).isEqualTo(OrderStatus.PAID);

    await()
        .atMost(Duration.ofSeconds(5))
        .untilAsserted(() -> {
            Integer updatedCount = rankingService.getProductOrderCount(productId);
            assertThat(updatedCount).isGreaterThan(initialCount);
        });
}
```

**검증 항목**:
- 주문 트랜잭션 즉시 완료
- 재고 즉시 차감
- 랭킹 비동기 업데이트 (Awaitility 사용)

## 개선 효과

### 1. 관심사 분리
- **Before**: OrderService가 주문 처리 + 랭킹 업데이트 두 가지 책임
- **After**: OrderService는 주문 처리만, 랭킹은 이벤트 리스너가 담당

### 2. 트랜잭션 독립성
- **Before**: 랭킹 업데이트 실패 시 주문 트랜잭션 롤백 가능
- **After**: 랭킹 업데이트 실패해도 주문은 정상 완료

### 3. 성능 개선
- **Before**: 동기적 랭킹 업데이트로 주문 응답 지연
- **After**: 비동기 처리로 주문 응답 시간 단축

### 4. 확장성
- 새로운 이벤트 리스너 추가 시 OrderService 수정 불필요
- 예: 주문 완료 알림, 포인트 적립, 외부 시스템 연동 등

## 주의사항

### 1. 이벤트 처리 실패
- 랭킹 업데이트 실패 시 재처리 로직 필요
- 현재는 로그만 기록, 향후 재시도 또는 DLQ 구현 고려

### 2. 순서 보장
- 비동기 처리로 인해 이벤트 순서 보장 안 됨
- 필요 시 순차 처리 또는 메시지 큐 도입 검토

### 3. 테스트 환경
- Redis 연결 필요한 통합 테스트는 Testcontainers 사용 권장
- 현재는 기본 OrderServiceTest만 검증

## 향후 개선 방안

1. **이벤트 저장소**: Outbox 패턴으로 이벤트 영속화
2. **재시도 메커니즘**: Spring Retry 또는 메시지 큐 활용
3. **모니터링**: 이벤트 처리 성공/실패율 추적
4. **멱등성**: 중복 이벤트 처리 방지 로직 추가

## 결론

Spring Application Event와 `@TransactionalEventListener`를 활용하여:
- ✅ 주문 정보를 원 트랜잭션 종료 후 전송
- ✅ 부가 로직(랭킹 업데이트)을 메인 서비스에서 분리
- ✅ 트랜잭션 독립성 및 시스템 안정성 향상

Step 15의 목표를 성공적으로 달성하였습니다.