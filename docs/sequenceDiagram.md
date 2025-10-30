# 시쿼스 다이어그램

## 🛍️ 상품 조회 (목록 / 상세 / 인기 Top5)

### 상품 목록 조회
```mermaid
sequenceDiagram
    autonumber
    participant PC as ProductController
    participant PS as ProductService
    participant PR as ProductRepository
    participant SR as SkuRepository
    participant AR as AnalyticsRepository

    rect rgb(245,245,245)
    Note over PC: GET /api/products?query...
    PC->>PS: listProducts(filter, sort, page)
    PS->>PR: findProducts(filter, sort, page)
    PR-->>PS: 상품 데이터 목록
    PS-->>PC: 상품 목록 조회 결과
    end
```

### 상품 상세 조회

```mermaid
sequenceDiagram
    autonumber
    participant PC as ProductController
    participant PS as ProductService
    participant PR as ProductRepository
    participant SR as SkuRepository
    participant AR as AnalyticsRepository

    rect rgb(245,245,245)
        Note over PC: GET /api/products/{id}
        PC->>PS: getProductDetail(productId)
        PS->>PR: findById(productId)
        PR-->>PS: 상품 기본 정보
        PS->>SR: findSkusByProduct(productId)
        SR-->>PS: 상품 옵션(SKU) 목록
        PS-->>PC: 상품 상세 조회 결과
    end
```

### 3일간 인기 상품 TOP 5 조회

```mermaid
sequenceDiagram
    autonumber
    participant PC as ProductController
    participant PS as ProductService
    participant PR as ProductRepository
    participant SR as SkuRepository
    participant AR as AnalyticsRepository

    rect rgb(245,245,245)
        Note over PC: GET /api/products/popular?range=3d&top=5
        PC->>PS: getPopularProducts(3d, 5)
        PS->>AR: findTopByRevenue(range=3d, limit=5)
        AR-->>PS: 인기 상품 데이터
        PS-->>PC: 인기 상품 Top5 조회 결과
    end
```

## 🛒 장바구니 (담기 / 수정 / 삭제)

### 장바구니 담기

```mermaid
sequenceDiagram
    autonumber
    participant CC as CartController
    participant CS as CartService
    participant CR as CartRepository
    participant CIR as CartItemRepository
    participant SR as SkuRepository

    rect rgb(245,245,245)
        Note over CC: POST /api/cart/items {skuId, qty}
        CC->>CS: addItem(userId|guestToken, skuId, qty)
        CS->>SR: getSku(skuId)
        SR-->>CS: 상품 옵션 정보
        CS->>CIR: findByCartAndSku(cartId, skuId)
        alt 이미 존재
            CS->>CIR: 수량 증가 처리
        else 신규
            CS->>CIR: 장바구니 항목 저장
        end
        CS-->>CC: 장바구니 추가 완료
    end

```

### 장바구니 수정

```mermaid
sequenceDiagram
    autonumber
    participant CC as CartController
    participant CS as CartService
    participant CR as CartRepository
    participant CIR as CartItemRepository
    participant SR as SkuRepository

    rect rgb(245,245,245)
        Note over CC: PATCH /api/cart/items/{itemId}
        CC->>CS: changeQty(itemId, qty)
        CS->>SR: getSkuForStockCheck(skuId)
        CS->>CIR: 수량 변경
        CS-->>CC: 장바구니 수정 완료
    end

```

### 장바구니 삭제

```mermaid
sequenceDiagram
    autonumber
    participant CC as CartController
    participant CS as CartService
    participant CR as CartRepository
    participant CIR as CartItemRepository
    participant SR as SkuRepository

    rect rgb(245,245,245)
        Note over CC: DELETE /api/cart/items/{itemId}
        CC->>CS: removeItem(itemId)
        CS->>CIR: 장바구니 항목 삭제
        CS-->>CC: 장바구니 삭제 완료
    end

```
## 📦 주문 생성 (장바구니 → 주문 / 주문항목 / 이력)

```mermaid
sequenceDiagram
    autonumber
    participant OC as OrderController
    participant OS as OrderService
    participant CR as CartRepository
    participant CIR as CartItemRepository
    participant SR as SkuRepository
    participant OR as OrderRepository
    participant OIR as OrderItemRepository
    participant OHR as OrderHistoryRepository

    Note over OC,OS: POST /api/orders
    OC->>OS: createOrder(userId)
    OS->>CR: findActiveCart(userId|guestToken)
    CR-->>OS: 장바구니 정보
    OS->>CIR: findItems(cartId)
    CIR-->>OS: 장바구니 상품 목록

    par 재고 및 가격 검증
        loop 각 상품
            OS->>SR: getSkuWithPriceAndStock(skuId)
            SR-->>OS: 상품 옵션 정보 (가격/재고)
        end
    and 총합 계산
        OS->>OS: 주문 금액 합산
    end

    OS->>OR: 주문 데이터 저장
    OR-->>OS: 주문 ID 반환
    loop 각 항목 저장
        OS->>OIR: 주문 상품 저장
    end
    OS->>OHR: 주문 생성 이력 기록
    OS-->>OC: 주문 생성 완료
```

## 🎫 쿠폰 적용 (유효성 검증 → 할인 반영)

```mermaid
sequenceDiagram
    autonumber
    participant OC as OrderController
    participant OS as OrderService
    participant COR as CouponRepository
    participant CUR as CouponUserRepository
    participant OR as OrderRepository
    participant OHR as OrderHistoryRepository

    Note over OC: POST /api/orders/{orderId}/apply-coupon
    OC->>OS: applyCoupon(userId, orderId, code)
    OS->>COR: findByCode(code)
    COR-->>OS: 쿠폰 정책 정보
    OS->>CUR: findByCouponAndUser(couponId, userId)
    CUR-->>OS: 사용자 쿠폰 상태

    alt 쿠폰 유효
        OS->>OR: 주문 금액에 할인 반영
        OR-->>OS: 할인 적용 완료
        OS->>OHR: 쿠폰 적용 이력 기록
        OS-->>OC: 쿠폰 적용 완료 (할인 금액 포함)
    else 쿠폰 무효
        OS-->>OC: 쿠폰 적용 실패 (사유 반환)
    end
```

## 💸 결제 (잔액 확인 → 재고 차감 → 상태 변경)

```mermaid
sequenceDiagram
    autonumber
    participant OC as OrderController
    participant OS as OrderService
    participant OR as OrderRepository
    participant OIR as OrderItemRepository
    participant SR as SkuRepository
    participant OHR as OrderHistoryRepository
    participant WR as WalletRepository
    participant WLR as WalletLedgerRepository

    Note over OC: POST /api/orders/{orderId}/pay (Idempotency-Key)
    OC->>OS: pay(orderId, userId, idemKey)

    rect rgb(255,250,230)
        Note over OS: @Transactional (원자적 처리)
        OS->>OR: findByIdForUpdate(orderId)
        OR-->>OS: 주문 정보 (PENDING 상태)

        OS->>WR: checkBalance(userId)
        WR-->>OS: 현재 잔액 조회

        alt 잔액 부족
            OS-->>OC: 결제 실패 (잔액 부족)
        else 결제 가능
            OS->>WLR: 결제 금액 차감 내역 기록
            OS->>OIR: findByOrder(orderId)
            OIR-->>OS: 주문 상품 목록

            loop 각 항목 재고 차감
                OS->>SR: updateStockOptimistic(skuId, -qty, version)
                SR-->>OS: 재고 차감 성공
            end

            OS->>OR: 주문 상태를 결제완료로 변경
            OR-->>OS: 결제 완료 저장
            OS->>OHR: 주문 상태 변경 이력 기록
            OS-->>OC: 결제 완료 (주문 상태=결제완료)
        end
    end
```


