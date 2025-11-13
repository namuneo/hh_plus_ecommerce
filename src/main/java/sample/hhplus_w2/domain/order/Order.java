package sample.hhplus_w2.domain.order;

import jakarta.persistence.*;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Order 도메인 엔티티
 */
@Entity
@Table(name = "order_", indexes = {
        @Index(name = "idx_order_user_created", columnList = "user_id, created_at"),
        @Index(name = "idx_order_status", columnList = "status, created_at"),
        @Index(name = "idx_order_expires", columnList = "expires_at")
})
@Getter
public class Order {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OrderStatus status;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal total;           // 총 주문 금액

    @Column(name = "discount_total", precision = 10, scale = 2)
    private BigDecimal discountTotal;   // 총 할인 금액

    @Column(name = "shipping_fee", precision = 10, scale = 2)
    private BigDecimal shippingFee;     // 배송비

    @Column(name = "expires_at")
    private LocalDateTime expiresAt;    // 주문 만료 시간 (결제 대기 TTL)

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    protected Order() {
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
     * 신규 주문 생성 (기본 30분 TTL)
     */
    public static Order create(Long userId, BigDecimal total, BigDecimal shippingFee) {
        return create(userId, total, shippingFee, 30);
    }

    /**
     * 신규 주문 생성 (TTL 지정)
     * @param ttlMinutes 결제 대기 시간 (분)
     */
    public static Order create(Long userId, BigDecimal total, BigDecimal shippingFee, int ttlMinutes) {
        if (total == null || total.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("주문 금액은 0 이상이어야 합니다.");
        }

        Order order = new Order();
        order.userId = userId;
        order.status = OrderStatus.PENDING;
        order.total = total;
        order.discountTotal = BigDecimal.ZERO;
        order.shippingFee = shippingFee != null ? shippingFee : BigDecimal.ZERO;
        order.expiresAt = LocalDateTime.now().plusMinutes(ttlMinutes);
        order.createdAt = LocalDateTime.now();
        order.updatedAt = LocalDateTime.now();
        return order;
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
     * 쿠폰 할인 적용
     */
    public void applyDiscount(BigDecimal discountAmount) {
        if (discountAmount == null || discountAmount.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("할인 금액은 0 이상이어야 합니다.");
        }
        this.discountTotal = discountAmount;
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * 최종 결제 금액 계산
     */
    public BigDecimal getFinalAmount() {
        return total.add(shippingFee).subtract(discountTotal);
    }

    /**
     * 결제 완료 처리
     */
    public void markAsPaid() {
        if (!OrderStatus.PENDING.equals(this.status)) {
            throw new IllegalStateException("결제 대기 상태의 주문만 결제할 수 있습니다.");
        }
        this.status = OrderStatus.PAID;
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * 주문 취소
     */
    public void cancel() {
        if (OrderStatus.PAID.equals(this.status)) {
            throw new IllegalStateException("결제 완료된 주문은 취소할 수 없습니다.");
        }
        this.status = OrderStatus.CANCELLED;
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * 주문 만료 처리
     */
    public void expire() {
        if (!OrderStatus.PENDING.equals(this.status)) {
            throw new IllegalStateException("결제 대기 상태의 주문만 만료할 수 있습니다.");
        }
        this.status = OrderStatus.EXPIRED;
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * 주문 만료 여부 확인
     */
    public boolean isExpired() {
        return LocalDateTime.now().isAfter(this.expiresAt);
    }
}
