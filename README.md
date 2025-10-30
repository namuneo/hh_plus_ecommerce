
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
