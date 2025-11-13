# ERD

```mermaid
erDiagram
%% NOTE: Mermaid 예약어 충돌을 피하기 위해 order 테이블은 ORDER_ 로 표기합니다. (실제명: `order`)

    USER {
        BIGINT id PK
        VARCHAR email
        VARCHAR password_hash
        VARCHAR name
        VARCHAR phone
        VARCHAR address_line1
        VARCHAR address_line2
        VARCHAR postal_code
        BOOLEAN is_active
        DATETIME created_at
        DATETIME updated_at
    }

    CATEGORY {
        BIGINT id PK
        VARCHAR name
        BOOLEAN is_active
        DATETIME created_at
        DATETIME updated_at
    }

    PRODUCT {
        BIGINT id PK
        BIGINT category_id FK
        VARCHAR name
        VARCHAR brand
        TEXT description
        DECIMAL price
        INT stock_qty
        INT version
        BOOLEAN is_active
        DATETIME created_at
        DATETIME updated_at
    }

    CART {
        BIGINT id PK
        BIGINT user_id FK
        VARCHAR guest_token
        ENUM status
        DATETIME created_at
        DATETIME updated_at
    }

    CART_ITEM {
        BIGINT id PK
        BIGINT cart_id FK
        BIGINT product_id FK
        INT qty
        DECIMAL unit_price_snapshot
        DATETIME created_at
        DATETIME updated_at
    }

    ORDER_ {
        BIGINT id PK
        BIGINT user_id FK
        ENUM status
        DECIMAL total
        DECIMAL discount_total
        DECIMAL shipping_fee
        DATETIME expires_at
        DATETIME created_at
        DATETIME updated_at
    }

    ORDER_ITEM {
        BIGINT id PK
        BIGINT order_id FK
        BIGINT product_id FK
        INT qty
        DECIMAL unit_price
        DECIMAL discount
        DATETIME created_at
        DATETIME updated_at
    }

    ORDER_HISTORY {
        BIGINT id PK
        BIGINT order_id FK
        ENUM from_status
        ENUM to_status
        VARCHAR reason
        ENUM actor_type
        DATETIME created_at
    }

    COUPON {
        BIGINT id PK
        VARCHAR code
        ENUM type
        DECIMAL amount
        INT total_issuable
        INT issued
        INT per_user_limit
        DATETIME valid_from
        DATETIME valid_to
        DECIMAL min_order_amount
        BOOLEAN stackable
        ENUM status
        DATETIME created_at
        DATETIME updated_at
    }

    COUPON_USER {
        BIGINT id PK
        BIGINT coupon_id FK
        BIGINT user_id FK
        BIGINT order_id FK
        ENUM status
        DATETIME issued_at
        DATETIME used_at
    }

    PRODUCT_SALES_STATS {
        BIGINT id PK
        BIGINT product_id FK
        VARCHAR product_name
        INT sales_count
        DECIMAL sales_amount
        INT days_range
        DATETIME aggregated_at
    }

%% Relationships
    CATEGORY ||--o{ PRODUCT : "분류한다"
    USER     ||--o{ CART : "장바구니 보유"
    CART     ||--o{ CART_ITEM : "항목 포함"
    PRODUCT  ||--o{ CART_ITEM : "담김"

    USER     ||--o{ ORDER_ : "주문"
    ORDER_   ||--o{ ORDER_ITEM : "상품 포함"
    PRODUCT  ||--o{ ORDER_ITEM : "주문 대상"
    ORDER_   ||--o{ ORDER_HISTORY : "상태 이력"

    COUPON   ||--o{ COUPON_USER : "발급"
    USER     ||--o{ COUPON_USER : "보유"
    ORDER_   ||--o{ COUPON_USER : "사용 주문(선택)"

    PRODUCT  ||--o{ PRODUCT_SALES_STATS : "판매 통계"

```

---

## 📌 인덱스 전략

### 1. 성능 최적화 인덱스

#### USER 테이블
```sql
CREATE INDEX idx_user_email ON USER(email);  -- 로그인 조회
```

#### PRODUCT 테이블
```sql
CREATE INDEX idx_product_category ON PRODUCT(category_id, created_at DESC);  -- 카테고리별 조회
CREATE INDEX idx_product_active ON PRODUCT(is_active, created_at DESC);      -- 활성 상품 조회
```

#### ORDER 테이블
```sql
CREATE INDEX idx_order_user_created ON ORDER_(user_id, created_at DESC);  -- 사용자별 주문 조회
CREATE INDEX idx_order_status ON ORDER_(status, created_at DESC);         -- 상태별 조회
CREATE INDEX idx_order_expires ON ORDER_(expires_at);                     -- 만료 주문 조회
```

#### CART 테이블
```sql
CREATE INDEX idx_cart_user ON CART(user_id, status);  -- 사용자별 장바구니 조회
CREATE INDEX idx_cart_guest ON CART(guest_token);     -- 비회원 장바구니 조회
```

#### COUPON_USER 테이블
```sql
CREATE UNIQUE INDEX uk_coupon_user ON COUPON_USER(coupon_id, user_id);  -- 중복 발급 방지
CREATE INDEX idx_coupon_user_status ON COUPON_USER(user_id, status);    -- 사용자별 쿠폰 조회
```

#### PRODUCT_SALES_STATS 테이블
```sql
CREATE INDEX idx_stats_days_sales ON PRODUCT_SALES_STATS(days_range, sales_count DESC);    -- 판매량 순위
CREATE INDEX idx_stats_days_revenue ON PRODUCT_SALES_STATS(days_range, sales_amount DESC); -- 매출액 순위
CREATE UNIQUE INDEX uk_stats_product_days ON PRODUCT_SALES_STATS(product_id, days_range);  -- 중복 집계 방지
```

---

## 🔐 제약조건

### UNIQUE 제약조건
- `COUPON.code` - 쿠폰 코드 중복 방지
- `COUPON_USER(coupon_id, user_id)` - 동일 쿠폰 중복 발급 방지
- `PRODUCT_SALES_STATS(product_id, days_range)` - 동일 기간 중복 집계 방지

### CHECK 제약조건
- `PRODUCT.stock_qty >= 0` - 음수 재고 방지
- `COUPON.issued <= COUPON.total_issuable` - 발급 수량 초과 방지
- `ORDER_.total >= 0` - 주문 금액 음수 방지