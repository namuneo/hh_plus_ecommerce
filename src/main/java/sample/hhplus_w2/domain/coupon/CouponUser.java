package sample.hhplus_w2.domain.coupon;

import lombok.Getter;

import java.time.LocalDateTime;

/**
 * CouponUser 도메인 엔티티
 * 사용자별 쿠폰 발급/사용 내역
 */
@Getter
public class CouponUser {
    private Long id;
    private Long couponId;
    private Long userId;
    private Long orderId;                   // 사용된 주문 ID (null 가능)
    private CouponUserStatus status;        // ISSUED, USED, EXPIRED
    private LocalDateTime issuedAt;         // 발급 시각
    private LocalDateTime usedAt;           // 사용 시각

    private CouponUser() {
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
