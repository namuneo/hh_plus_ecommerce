package sample.hhplus_w2.domain.order;

import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * OrderItem 도메인 엔티티
 */
@Getter
public class OrderItem {
    private Long id;
    private Long orderId;
    private Long productId;
    private Integer qty;
    private BigDecimal unitPrice;  // 주문 당시 단가
    private BigDecimal discount;   // 항목별 할인
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    private OrderItem() {
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