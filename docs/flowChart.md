# FlowChart
| 행위   | 주요 API                                    |
|------| ----------------------------------------- |
| 상품 목록 조회 | GET `/api/products`                       |
| 상품 상세 조회 | GET `/api/products/{id}`                  |
| 장바구니 담기 | POST `/api/cart/items`                    |
| 주문 생성 | POST `/api/orders`                        |
| 쿠폰 적용    | POST `/api/orders/{orderId}/apply-coupon` |
| 결제 처리    | POST `/api/orders/{orderId}/pay`          |
| 주문 상태 조회 | GET `/api/orders/{orderId}`               |

```mermaid
flowchart TD
    Start([시작]) --> B["상품 목록 조회<br/>GET /api/products"]
    B --> C["상품 상세 조회<br/>GET /api/products/{id}"]
    C --> D["장바구니 담기<br/>POST /api/cart/items"]
    D --> E["주문 생성<br/>POST /api/orders"]
    E --> F["쿠폰 적용<br/>POST /api/orders/{orderId}/apply-coupon"]
    F --> G["결제 처리 - 지갑<br/>POST /api/orders/{orderId}/pay"]
    G --> H["주문 상세 조회<br/>GET /api/orders/{orderId}"]
    H --> End([종료])

    style Start fill:#f5f5f5,stroke:#333,stroke-width:2px
    style End fill:#f5f5f5,stroke:#333,stroke-width:2px
    style B fill:#fff,stroke:#666,stroke-width:1px
    style C fill:#fff,stroke:#666,stroke-width:1px
    style D fill:#fff,stroke:#666,stroke-width:1px
    style E fill:#fff,stroke:#666,stroke-width:1px
    style F fill:#fff,stroke:#666,stroke-width:1px
    style G fill:#fff,stroke:#666,stroke-width:1px
    style H fill:#fff,stroke:#666,stroke-width:1px
```