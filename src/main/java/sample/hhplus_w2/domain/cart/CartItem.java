package sample.hhplus_w2.domain.cart;

import jakarta.persistence.*;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * CartItem 도메인 엔티티
 */
@Entity
@Table(name = "cart_item", indexes = {
    @Index(name = "idx_cart_item_cart", columnList = "cart_id"),
    @Index(name = "idx_cart_item_cart_product", columnList = "cart_id, product_id")
})
@Getter
public class CartItem {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "cart_id", nullable = false)
    private Long cartId;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(nullable = false)
    private Integer qty;

    @Column(name = "unit_price_snapshot", nullable = false, precision = 10, scale = 2)
    private BigDecimal unitPriceSnapshot; // 장바구니 담을 당시 가격 스냅샷

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    protected CartItem() {
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
     * 장바구니 항목 생성
     */
    public static CartItem create(Long cartId, Long productId, Integer qty, BigDecimal unitPrice) {
        if (qty <= 0) {
            throw new IllegalArgumentException("수량은 1 이상이어야 합니다.");
        }
        if (unitPrice == null || unitPrice.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("가격은 0 이상이어야 합니다.");
        }

        CartItem item = new CartItem();
        item.cartId = cartId;
        item.productId = productId;
        item.qty = qty;
        item.unitPriceSnapshot = unitPrice;
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
     * 수량 변경
     */
    public void changeQuantity(Integer newQty) {
        if (newQty <= 0) {
            throw new IllegalArgumentException("수량은 1 이상이어야 합니다.");
        }
        this.qty = newQty;
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * 수량 증가
     */
    public void increaseQuantity(Integer amount) {
        if (amount <= 0) {
            throw new IllegalArgumentException("증가량은 1 이상이어야 합니다.");
        }
        this.qty += amount;
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * 가격 스냅샷 갱신 (결제 시점에 가격 재확인용)
     */
    public void updatePriceSnapshot(BigDecimal newPrice) {
        this.unitPriceSnapshot = newPrice;
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * 총 금액 계산
     */
    public BigDecimal getTotalPrice() {
        return unitPriceSnapshot.multiply(BigDecimal.valueOf(qty));
    }
}