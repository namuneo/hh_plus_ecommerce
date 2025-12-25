package sample.hhplus_w2.repository.coupon;

import sample.hhplus_w2.domain.coupon.Coupon;
import sample.hhplus_w2.domain.coupon.CouponStatus;

import java.util.List;
import java.util.Optional;

public interface CouponRepository {
    Coupon save(Coupon coupon);
    Optional<Coupon> findById(Long id);
    Optional<Coupon> findByCode(String code);
    List<Coupon> findByStatus(CouponStatus status);
    List<Coupon> findAll();
    void delete(Long id);
    void deleteAll();

    /**
     * 쿠폰 발급 수량 원자적 증가
     *
     * Race Condition 방지를 위한 Atomic DB Update
     *
     * @param couponId 쿠폰 ID
     * @return 업데이트된 행 수 (0 = 수량 소진, 1 = 성공)
     */
    int incrementIssuedQty(Long couponId);
}
