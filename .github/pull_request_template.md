## :pushpin: PR 제목
[STEP 15-16] 김성준 - e-commerce

---

## 📋 구현 내용

### STEP 15: Application Event
- [x] 주문/예약 정보를 원 트랜잭션이 종료된 이후에 전송
  - `@TransactionalEventListener(phase = AFTER_COMMIT)` 적용
  - 주문 트랜잭션 커밋 후 이벤트 처리
- [x] 주문/예약 정보를 전달하는 부가 로직에 대한 관심사를 메인 서비스에서 분리
  - OrderService에서 ProductRankingService 의존성 제거
  - OrderEventListener로 랭킹 업데이트 로직 분리
  - `@Async` + `Propagation.REQUIRES_NEW`로 독립적인 트랜잭션 실행

### STEP 16: Transaction Diagnosis
- [x] 도메인별로 트랜잭션이 분리되었을 때 발생 가능한 문제 파악
  - 부분 실패 (Partial Failure)
  - 분산 교착 상태 (Distributed Deadlock)
  - 보상 트랜잭션 누락
  - 이벤트 손실
  - 타임아웃과 중복 처리
  - 읽기-수정-쓰기 경쟁 조건
- [x] 트랜잭션이 분리되더라도 데이터 일관성을 보장할 수 있는 분산 트랜잭션 설계
  - **Outbox 패턴**: 이벤트 영속화로 손실 방지 (at-least-once 보장)
  - **보상 트랜잭션**: 재고 복구, 주문 취소 롤백 메커니즘
  - **재시도 + DLQ**: 최대 5회 재시도, 실패 시 Dead Letter Queue 이동

---

## 🏗️ 핵심 구현

### 1. 도메인 이벤트 및 리스너

**OrderCompletedEvent** (`domain/order/event/OrderCompletedEvent.java`)
```java
@Getter
public class OrderCompletedEvent {
    private final Long orderId;
    private final Long userId;
    private final List<OrderItemSnapshot> orderItems;  // JSON 직렬화 가능
    private final LocalDateTime completedAt;
}
```

**OrderEventListener** (`application/listener/OrderEventListener.java`)
```java
@Async
@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
@Transactional(propagation = Propagation.REQUIRES_NEW)
public void handleOrderCompleted(OrderCompletedEvent event) {
    // 랭킹 업데이트 (원 트랜잭션과 독립적)
}
```

### 2. Outbox 패턴

**OutboxEvent** (`domain/outbox/OutboxEvent.java`)
```java
@Entity
@Table(name = "outbox_event")
public class OutboxEvent {
    private String eventType;      // "OrderCompleted"
    private String aggregateId;    // 주문 ID
    private String payload;        // JSON 이벤트 데이터
    private OutboxStatus status;   // PENDING, PUBLISHED, FAILED
    private Integer retryCount;    // 재시도 횟수
}
```

**OutboxEventPublisher** (`application/outbox/OutboxEventPublisher.java`)
```java
@Scheduled(fixedDelay = 5000)  // 5초마다 실행
public void publishPendingEvents() {
    // PENDING 이벤트 조회 → 발행 → PUBLISHED 또는 재시도
}
```

**OrderService 수정** (`service/order/OrderService.java:114-129`)
```java
// Outbox 패턴: 이벤트를 DB에 저장 (트랜잭션 원자성 보장)
OrderCompletedEvent event = OrderCompletedEvent.of(...);
String payload = objectMapper.writeValueAsString(event);
OutboxEvent outboxEvent = OutboxEvent.create("OrderCompleted", orderId, payload);
outboxEventRepository.save(outboxEvent);
// 트랜잭션 커밋: 주문 + Outbox 이벤트 함께 저장
```

### 3. 보상 트랜잭션

**OrderCompensationService** (`application/compensation/OrderCompensationService.java`)
```java
@Transactional
public void cancelOrderWithStockRestore(Long orderId, String reason) {
    restoreStock(orderId);        // 1. 재고 복구 (보상)
    cancelOrder(orderId, reason);  // 2. 주문 취소
}
```

---

## 🔄 전체 아키텍처 흐름

```
[주문 결제 요청]
    ↓
[OrderService.processPayment()]
    ├─ @Transactional 시작
    ├─ 주문 검증
    ├─ 재고 차감 (@Version 낙관적 락)
    ├─ 주문 상태 변경 (PAID)
    ├─ OrderHistory 기록
    ├─ OutboxEvent 저장 (PENDING)
    └─ 트랜잭션 커밋 ✅
        └─ Order + Product + OrderHistory + OutboxEvent
           모두 원자적으로 커밋
    ↓
[OutboxEventPublisher (5초마다)]
    ├─ PENDING 이벤트 조회
    ├─ OrderCompletedEvent 역직렬화
    ├─ ApplicationEventPublisher 발행
    ├─ 성공 → PUBLISHED
    └─ 실패 → retryCount++, 5회 초과 시 DLQ
    ↓
[OrderEventListener]
    ├─ @Async (별도 스레드)
    ├─ @TransactionalEventListener(AFTER_COMMIT)
    ├─ @Transactional(REQUIRES_NEW) 시작
    ├─ rankingService.incrementProductOrder()
    └─ 커밋 (주문 트랜잭션과 독립적)
```

---

## 📊 데이터 일관성 보장 전략

### 강한 일관성 (ACID)
- **적용**: 주문 생성, 재고 차감, 상태 변경
- **방법**: 단일 `@Transactional`, 낙관적 락

### 최종 일관성 (BASE)
- **적용**: 랭킹 업데이트, 통계 집계
- **방법**: Outbox 패턴 + 비동기 이벤트

---

## 📁 주요 파일 변경 사항

### 신규 파일 (11개)
```
domain/order/event/OrderCompletedEvent.java           # 도메인 이벤트 (JSON 직렬화)
application/listener/OrderEventListener.java          # 이벤트 리스너
config/AsyncConfig.java                               # 비동기 설정

domain/outbox/OutboxEvent.java                        # Outbox 엔티티
domain/outbox/OutboxStatus.java                       # 이벤트 상태
repository/outbox/OutboxEventRepository.java          # Repository
repository/outbox/impl/OutboxEventRepositoryImpl.java
infrastructure/outbox/OutboxEventJpaRepository.java
application/outbox/OutboxEventPublisher.java          # 주기적 발행

application/compensation/OrderCompensationService.java # 보상 트랜잭션

test/application/listener/OrderEventListenerTest.java # 통합 테스트
```

### 수정 파일 (2개)
```
HhplusW2Application.java          # @EnableAsync 추가
service/order/OrderService.java   # Outbox 패턴 적용
```

### 문서 (3개)
```
docs/step15-application-event.md       # Step 15 상세 문서
docs/step16-transaction-diagnosis.md   # Step 16 문제 분석 및 설계
docs/step15-16-summary.md              # 종합 구현 보고서
```

---

## 🧪 테스트 전략

### 구현된 테스트
- `OrderEventListenerTest`: 비동기 이벤트 처리 검증 (Awaitility 사용)
- `OrderServiceTest`: 주문 처리 기본 흐름 검증

### 검증 항목
- ✅ 주문 트랜잭션 즉시 완료
- ✅ 재고 즉시 차감
- ✅ Outbox 이벤트 DB 저장
- ✅ 랭킹 비동기 업데이트 (5초 이내)

---

## 🎯 달성 효과

### Step 15
- ✅ **관심사 분리**: OrderService 단일 책임 원칙 준수
- ✅ **트랜잭션 독립성**: 랭킹 실패가 주문에 영향 없음
- ✅ **성능 향상**: 비동기 처리로 응답 시간 단축
- ✅ **확장성**: 새 이벤트 리스너 추가 시 기존 코드 수정 불필요

### Step 16
- ✅ **이벤트 손실 방지**: Outbox 패턴으로 영속화
- ✅ **at-least-once 보장**: 재시도 + DLQ
- ✅ **보상 메커니즘**: 재고 복구, 주문 취소 롤백
- ✅ **데이터 일관성**: ACID + BASE 조합

---

## 🔍 주요 기술 스택

- **Spring Events**: `ApplicationEventPublisher`, `@TransactionalEventListener`
- **비동기 처리**: `@Async`, `ThreadPoolTaskExecutor`
- **트랜잭션 관리**: `@Transactional(propagation = REQUIRES_NEW)`
- **스케줄링**: `@Scheduled(fixedDelay = 5000)`
- **직렬화**: Jackson `ObjectMapper`, `@JsonCreator`
- **테스트**: Awaitility (비동기 검증)

---

## 📌 향후 개선 방안

1. **멱등성 키 관리**: 중복 처리 방지
2. **Saga Orchestrator**: 복잡한 분산 트랜잭션 조정
3. **Circuit Breaker**: 외부 API 장애 대응
4. **보상 로그 추적**: `CompensationLog` 테이블
5. **Outbox 테이블 정리**: PUBLISHED 이벤트 보관 정책

---

## 💭 회고

### 잘한 점
- Outbox 패턴으로 이벤트 손실을 완벽히 방지하여 데이터 일관성 보장
- @TransactionalEventListener와 @Async를 조합하여 트랜잭션 분리와 성능 향상 동시 달성
- 보상 트랜잭션 구현으로 실패 시나리오에 대한 롤백 메커니즘 확보

### 어려운 점
- OrderCompletedEvent의 JSON 직렬화 처리 시 OrderItem 엔티티 의존성 제거를 위한 OrderItemSnapshot 설계
- Outbox 패턴의 재시도 로직과 DLQ 이동 시점 결정 (최대 5회로 설정)
- 비동기 이벤트 처리 테스트에서 Awaitility를 활용한 타이밍 검증

### 다음 시도
- 멱등성 키 관리를 통한 중복 처리 완벽 방지
- Saga Orchestrator 패턴으로 더 복잡한 분산 트랜잭션 관리
- 모니터링 시스템 연동으로 Outbox 발행 실패율 추적

---

## 📚 참고 문서

- [Step 15 상세 문서](../docs/step15-application-event.md)
- [Step 16 문제 분석 및 설계](../docs/step16-transaction-diagnosis.md)
- [종합 구현 보고서](../docs/step15-16-summary.md)