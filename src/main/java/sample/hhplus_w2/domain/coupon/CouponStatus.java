package sample.hhplus_w2.domain.coupon;

/**
 * 쿠폰 정책 상태
 */
public enum CouponStatus {
    DRAFT,       // 초안
    PUBLISHED,   // 공개 (발급 가능)
    PAUSED,      // 일시정지
    EXPIRED      // 만료
}