package sample.hhplus_w2.domain.coupon;

import jakarta.persistence.*;
import lombok.Getter;

import java.time.LocalDateTime;

/**
 * CouponUser 도메인 엔티티
 * 사용자별 쿠폰 발급/사용 내역
 */
@Entity
@Table(name = "coupon_user", indexes = {
        @Index(name = "uk_coupon_user", columnList = "coupon_id, user_id", unique = true),
        @Index(name = "idx_coupon_user_status", columnList = "user_id, status")
})
@Getter
public class CouponUser {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "coupon_id", nullable = false)
    private Long couponId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "order_id")
    private Long orderId;                   // 사용된 주문 ID (null 가능)

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CouponUserStatus status;        // ISSUED, USED, EXPIRED

    @Column(name = "issued_at", nullable = false)
    private LocalDateTime issuedAt;         // 발급 시각

    @Column(name = "used_at")
    private LocalDateTime usedAt;           // 사용 시각

    protected CouponUser() {
    }

    /**
     * 쿠폰 발급
     */
    public static CouponUser issue(Long couponId, Long userId) {
        CouponUser couponUser = new CouponUser();
        couponUser.couponId = couponId;
        couponUser.userId = userId;
        couponUser.status = CouponUserStatus.ISSUED;
        couponUser.issuedAt = LocalDateTime.now();
        return couponUser;
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
     * 쿠폰 사용
     */
    public void use(Long orderId) {
        if (!CouponUserStatus.ISSUED.equals(this.status)) {
            throw new IllegalStateException("발급된 상태의 쿠폰만 사용할 수 있습니다.");
        }
        this.orderId = orderId;
        this.status = CouponUserStatus.USED;
        this.usedAt = LocalDateTime.now();
    }

    /**
     * 쿠폰 만료
     */
    public void expire() {
        if (CouponUserStatus.USED.equals(this.status)) {
            throw new IllegalStateException("이미 사용된 쿠폰은 만료할 수 없습니다.");
        }
        this.status = CouponUserStatus.EXPIRED;
    }

    /**
     * 쿠폰 사용 가능 여부
     */
    public boolean isUsable() {
        return CouponUserStatus.ISSUED.equals(this.status);
    }
}
