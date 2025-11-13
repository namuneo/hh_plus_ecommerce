package sample.hhplus_w2.domain.order;

import jakarta.persistence.*;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * OrderItem 도메인 엔티티
 */
@Entity
@Table(name = "order_item")
@Getter
public class OrderItem {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_id", nullable = false)
    private Long orderId;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(nullable = false)
    private Integer qty;

    @Column(name = "unit_price", nullable = false, precision = 10, scale = 2)
    private BigDecimal unitPrice;  // 주문 당시 단가

    @Column(precision = 10, scale = 2)
    private BigDecimal discount;   // 항목별 할인

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    protected OrderItem() {
    }

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * 주문 항목 생성
     */
    public static OrderItem create(Long orderId, Long productId, Integer qty, BigDecimal unitPrice) {
        return create(orderId, productId, qty, unitPrice, BigDecimal.ZERO);
    }

    /**
     * 주문 항목 생성 (할인 포함)
     */
    public static OrderItem create(Long orderId, Long productId, Integer qty, BigDecimal unitPrice, BigDecimal discount) {
        if (qty <= 0) {
            throw new IllegalArgumentException("수량은 1 이상이어야 합니다.");
        }
        if (unitPrice == null || unitPrice.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("단가는 0 이상이어야 합니다.");
        }
        if (discount == null || discount.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("할인 금액은 0 이상이어야 합니다.");
        }

        OrderItem item = new OrderItem();
        item.orderId = orderId;
        item.productId = productId;
        item.qty = qty;
        item.unitPrice = unitPrice;
        item.discount = discount;
        item.createdAt = LocalDateTime.now();
        item.updatedAt = LocalDateTime.now();
        return item;
    }

    /**
     * ID 설정 (Repository에서 사용)
     */
    public void assignId(Long id) {
        if (this.id != null) {
            throw new IllegalStateException("ID는 이미 할당되었습니다.");
        }
        this.id = id;
    }

    /**
     * 항목별 총 금액 (할인 전)
     */
    public BigDecimal getSubtotal() {
        return unitPrice.multiply(BigDecimal.valueOf(qty));
    }

    /**
     * 항목별 최종 금액 (할인 후)
     */
    public BigDecimal getFinalAmount() {
        return getSubtotal().subtract(discount);
    }
}