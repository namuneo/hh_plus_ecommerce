
# 🎉 E-Commerce


<details>
<summary>🔍 주요 키워드 공부</summary>
<div markdown="1">



### ✅ SKU (Stock Keeping Unit)
- **정의:** ‘재고관리 단위’. 상품의 세부 옵션(예: 색상, 사이즈)에 따라 각각 별도의 SKU로 관리.
- **예시:** “티셔츠” 상품의 SKU는 `티셔츠-블랙-M`, `티셔츠-화이트-L` 처럼 구분된다.
- **특징:** 재고, 가격, 할인은 SKU 단위로 관리되며, Product와 1:N 관계를 갖는다.

```sql
CREATE TABLE sku (
  id BIGINT PRIMARY KEY,
  product_id BIGINT,
  attributes JSON,
  price DECIMAL(10,2),
  stock_qty INT,
  version INT
);
```

---

### ✅ 원자적 (Atomic)
- **정의:** “모두 수행되거나, 아무것도 수행되지 않는다”는 트랜잭션의 기본 성질.
- **예시:** 주문-결제-재고 차감이 하나라도 실패하면 전체가 롤백되어 일관성이 유지된다.

```java
@Transactional
public void completeOrder(Order order) {
    paymentService.charge(order);
    stockService.decrease(order);
    order.setStatus(OrderStatus.PAID);
    orderRepository.save(order);
}
```

---

### ✅ 낙관적 락 (Optimistic Lock)
- **정의:** 충돌이 적다고 가정하고, 수정 전후 버전(version)을 비교하는 방식의 동시성 제어.
- **특징:** DB 락을 오래 잡지 않아 성능에 유리함.
- **예시:**

```sql
UPDATE sku
SET stock_qty = stock_qty - 1, version = version + 1
WHERE id = 100 AND version = 3;
```

---

### ✅ Idempotency-Key (멱등키)
- **정의:** 같은 요청이 여러 번 들어와도 결과가 한 번만 반영되도록 하는 키.
- **사용처:** 결제, 쿠폰 발급 API 등 중복 요청 방지.
- **예시:**

```http
POST /payments
Idempotency-Key: abc123xyz
```

---

### ✅ Outbox 테이블 (Outbox Pattern)
- **정의:** 트랜잭션 내 발생 이벤트를 안전하게 외부로 전달하기 위한 패턴.
- **과정:** 주문 DB에 Outbox 레코드 적재 → 별도 워커가 외부로 전송.
- **예시:**

| id | event_type | payload | status |
|----|-------------|----------|--------|
| 1 | ORDER_PAID | {"orderId": 101} | PENDING |
| 2 | ORDER_PAID | {"orderId": 102} | SENT |

---

### ✅ DLQ (Dead Letter Queue)
- **정의:** 전송 실패나 재시도 초과 메시지를 보관하는 ‘실패 큐’.
- **용도:** 운영자가 실패 원인 분석 및 재전송 가능.
- **예시:**

| id | event_id | reason | retry_count |
|----|-----------|---------|-------------|
| 1 | 102 | Timeout | 3 |

---

## 📘 요약

| 용어 | 의미 | 주요 활용 영역 |
|------|------|----------------|
| **SKU** | 옵션별 재고 단위 식별자 | 상품/재고 관리 |
| **원자적** | 트랜잭션 불가분성 (모두 성공 or 전부 실패) | 주문/결제 |
| **낙관적 락** | version 비교를 통한 충돌 방지 | 재고 동시성 |
| **Idempotency-Key** | 중복 요청 단일 처리 | 결제/쿠폰 발급 |
| **Outbox** | 이벤트 DB 기록 후 외부 전송 | 외부 데이터 연동 |
| **DLQ** | 실패 메시지 보관 및 재처리 | 재시도/운영 복구 |

---

</div>
</details>

---

## 🔒 동시성 제어 전략

이 프로젝트에서 적용한 동시성 제어 방식과 기술적 분석입니다.

### 1. 쿠폰 선착순 발급 (synchronized)

**방식:** Java의 `synchronized` 키워드를 사용한 메서드 레벨 동기화

```java
public synchronized CouponUser issueCoupon(Long couponId, Long userId) {
    Coupon coupon = getCoupon(couponId);

    // 중복 발급 체크
    if (couponUserRepository.findByCouponIdAndUserId(couponId, userId).isPresent()) {
        throw new IllegalStateException("이미 발급받은 쿠폰입니다.");
    }

    // 발급 가능 여부 확인
    if (!coupon.canIssue()) {
        throw new IllegalStateException("발급 불가능한 쿠폰입니다.");
    }

    // 원자적 발급 처리
    boolean issued = coupon.issue();
    if (!issued) {
        throw new IllegalStateException("쿠폰이 모두 소진되었습니다.");
    }

    couponRepository.save(coupon);
    CouponUser couponUser = CouponUser.issue(couponId, userId);
    return couponUserRepository.save(couponUser);
}
```

**선택 이유:**
- ✅ 단일 인스턴스 환경에서 간단하고 확실한 동시성 제어
- ✅ 코드가 직관적이고 이해하기 쉬움
- ✅ 선착순이라는 요구사항에 적합 (먼저 도착한 요청이 먼저 처리)

**트레이드오프:**
- ⚠️ **성능:** 동시 요청 시 순차 처리로 인한 처리량 감소
- ⚠️ **확장성:** 다중 인스턴스 환경에서는 분산 락(Redis, DB Lock 등) 필요
- ✅ **정확성:** Race Condition 없이 100% 정확한 수량 제어 보장

**동시성 테스트 결과:**
```
✅ 100명이 10개 쿠폰 발급 시도 → 정확히 10명만 성공
✅ 동일 사용자 중복 발급 시도 → 1번만 성공
✅ Race Condition 발생 없음
```

---

### 2. 재고 차감 (낙관적 락 - Optimistic Lock)

**방식:** Version 필드를 활용한 낙관적 락

```java
public boolean decreaseStock(Integer quantity, Integer expectedVersion) {
    // 버전 불일치 체크 (동시성 충돌 감지)
    if (!this.version.equals(expectedVersion)) {
        return false; // 다른 트랜잭션에서 이미 수정됨
    }

    // 재고 부족 체크
    if (this.stockQty < quantity) {
        throw new IllegalStateException("재고가 부족합니다.");
    }

    // 재고 차감 및 버전 증가
    this.stockQty -= quantity;
    this.version++;
    return true;
}
```

**선택 이유:**
- ✅ **읽기 성능:** DB 락을 잡지 않아 읽기 작업에 대한 성능 영향 최소
- ✅ **충돌 감지:** 버전 불일치로 동시 수정 즉시 감지
- ✅ **재시도 가능:** 실패 시 애플리케이션 레벨에서 재시도 가능

**트레이드오프:**
- ⚠️ **재시도 필요:** 충돌 발생 시 클라이언트에서 재시도 로직 구현 필요
- ✅ **충돌 빈도:** 이커머스 환경에서 동일 상품에 대한 동시 주문 빈도는 상대적으로 낮음
- ✅ **확장성:** 다중 인스턴스 환경에서도 정확하게 동작

**재시도 로직 예시:**
```java
boolean success = false;
int maxRetries = 10;
for (int retry = 0; retry < maxRetries && !success; retry++) {
    Product product = productService.getProduct(productId);
    success = productService.decreaseStock(productId, quantity, product.getVersion());
    if (!success) {
        Thread.sleep(10); // 짧은 대기 후 재시도
    }
}
```

**동시성 테스트 결과:**
```
✅ 100개 재고, 50명이 각 2개씩 주문 → 음수 재고 발생 없음
✅ 재고 부족 시 일부만 성공 → 정확한 재고 관리
✅ Race Condition 방지 확인
```

---

### 3. 인메모리 환경 (ConcurrentHashMap)

**방식:** 스레드 안전한 자료구조 사용

```java
@Repository
public class ProductRepositoryImpl implements ProductRepository {
    private final Map<Long, Product> store = new ConcurrentHashMap<>();
    private final AtomicLong idGenerator = new AtomicLong(1);

    @Override
    public Product save(Product product) {
        if (product.getId() == null) {
            product.assignId(idGenerator.getAndIncrement());
        }
        store.put(product.getId(), product);
        return product;
    }
}
```

**선택 이유:**
- ✅ `ConcurrentHashMap`: 동시 읽기/쓰기에 안전
- ✅ `AtomicLong`: 원자적 ID 생성 보장
- ✅ DB 없이 빠른 개발 및 테스트 가능

**한계:**
- ⚠️ 애플리케이션 재시작 시 데이터 손실
- ⚠️ 다중 인스턴스 환경에서 데이터 불일치 가능
- ⚠️ 실제 프로덕션에서는 DB 또는 Redis 필요

---

### 4. 주문/결제 트랜잭션

**전략:** 도메인 로직 + Repository 저장의 조합

```java
public Order processPayment(Long orderId) {
    // 1. 주문 조회 및 검증
    Order order = getOrder(orderId);

    // 2. 만료 체크
    if (order.isExpired()) {
        order.expire();
        // ... 이력 저장
        throw new IllegalStateException("주문이 만료되었습니다.");
    }

    // 3. 재고 차감 (낙관적 락)
    for (OrderItem item : orderItems) {
        Product product = productRepository.findById(item.getProductId()).orElseThrow();
        boolean success = product.decreaseStock(item.getQty(), product.getVersion());
        if (!success) {
            throw new IllegalStateException("재고 차감 실패");
        }
        productRepository.save(product);
    }

    // 4. 주문 상태 변경
    order.markAsPaid();
    orderRepository.save(order);

    return order;
}
```

**동시성 고려사항:**
- ✅ 재고 차감은 낙관적 락으로 보호
- ✅ 실패 시 예외 발생으로 부분 성공 방지
- ⚠️ 인메모리 환경이므로 실제 DB 트랜잭션(@Transactional) 미적용

---

## 📊 동시성 테스트 전략

### 테스트 도구
- `ExecutorService`: 고정 크기 스레드 풀 생성
- `CountDownLatch`: 모든 스레드의 완료 대기
- `AtomicInteger`: 스레드 안전한 카운터

### 테스트 시나리오

**1. 쿠폰 발급 동시성 테스트 (4개)**
- ✅ 100명이 10개 쿠폰 발급 시도 → 정확히 10명 성공
- ✅ 중복 발급 방지 검증
- ✅ Race Condition 없음 확인
- ✅ 수량 초과 발급 없음

**2. 재고 차감 동시성 테스트 (5개)**
- ✅ 낙관적 락으로 정확한 재고 차감
- ✅ 재고 부족 시 일부만 성공
- ✅ 음수 재고 발생 없음
- ✅ 혼합 작업(증가/차감) 안정성

**3. 주문 결제 동시성 테스트 (4개)**
- ✅ 동시 결제 시 재고 정확성
- ✅ 재고 부족 시 일부만 성공
- ✅ Race Condition 방지

### 테스트 예시 코드

```java
@Test
void couponConcurrencyTest() throws InterruptedException {
    // given
    Coupon coupon = createCoupon(totalIssuable=10);

    int threadCount = 100;
    ExecutorService executor = Executors.newFixedThreadPool(threadCount);
    CountDownLatch latch = new CountDownLatch(threadCount);
    AtomicInteger successCount = new AtomicInteger(0);

    // when
    for (int i = 0; i < threadCount; i++) {
        executor.submit(() -> {
            try {
                couponService.issueCoupon(couponId, userId);
                successCount.incrementAndGet();
            } catch (Exception e) {
                // 실패는 정상
            } finally {
                latch.countDown();
            }
        });
    }

    latch.await();
    executor.shutdown();

    // then
    assertThat(successCount.get()).isEqualTo(10); // 정확히 10명만 성공
}
```

---

## 🎯 성능 고려사항

### 인기 상품 집계 캐싱 전략

**문제:** 매 요청마다 집계하면 성능 저하

**해결책:** 주기적 갱신 + 메모리 캐싱
```java
public List<ProductSalesStats> getTopProductsByPeriod(Integer days, int limit) {
    // 통계 갱신 (5분마다 백그라운드 작업으로 실행 가능)
    aggregateSalesStats(days);

    // 캐시된 통계 조회
    return statsRepository.findByDaysRangeOrderBySalesCountDesc(days, limit);
}
```

**개선 가능 사항:**
- ⏰ 스케줄러를 통한 주기적 갱신 (5분마다)
- 📦 애플리케이션 레벨 캐시 (Caffeine, Ehcache)
- 🚀 분리된 통계 조회 전용 저장소

---

## 📝 결론

| 영역 | 동시성 제어 방식 | 장점 | 한계 |
|------|----------------|------|------|
| **쿠폰 발급** | synchronized | 간단하고 정확함 | 단일 인스턴스 한정 |
| **재고 차감** | 낙관적 락 (version) | 높은 읽기 성능 | 재시도 로직 필요 |
| **저장소** | ConcurrentHashMap | 스레드 안전 | 인메모리 한계 |

**실제 프로덕션 환경 고려사항:**
- 다중 인스턴스: Redis 분산 락, DB 비관적 락 검토
- 트랜잭션: @Transactional + 적절한 격리 수준 설정
- 모니터링: 동시성 충돌률, 재시도 빈도 추적

---
