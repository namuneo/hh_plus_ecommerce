package sample.hhplus_w2.domain.cart;

import jakarta.persistence.*;
import lombok.Getter;

import java.time.LocalDateTime;

/**
 * Cart 도메인 엔티티
 */
@Entity
@Table(name = "cart", indexes = {
        @Index(name = "idx_cart_user", columnList = "user_id, status"),
        @Index(name = "idx_cart_guest", columnList = "guest_token")
})
@Getter
public class Cart {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id")
    private Long userId;         // 회원 장바구니

    @Column(name = "guest_token", length = 255)
    private String guestToken;   // 비회원 장바구니 (토큰 기반)

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CartStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    protected Cart() {
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
     * 회원 장바구니 생성
     */
    public static Cart createForUser(Long userId) {
        Cart cart = new Cart();
        cart.userId = userId;
        cart.status = CartStatus.ACTIVE;
        cart.createdAt = LocalDateTime.now();
        cart.updatedAt = LocalDateTime.now();
        return cart;
    }

    /**
     * 비회원 장바구니 생성
     */
    public static Cart createForGuest(String guestToken) {
        Cart cart = new Cart();
        cart.guestToken = guestToken;
        cart.status = CartStatus.ACTIVE;
        cart.createdAt = LocalDateTime.now();
        cart.updatedAt = LocalDateTime.now();
        return cart;
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
     * 주문 완료로 상태 변경
     */
    public void markAsOrdered() {
        this.status = CartStatus.ORDERED;
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * 방치된 장바구니로 표시
     */
    public void markAsAbandoned() {
        this.status = CartStatus.ABANDONED;
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * 활성 장바구니 여부
     */
    public boolean isActive() {
        return CartStatus.ACTIVE.equals(this.status);
    }
}