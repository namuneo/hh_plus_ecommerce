package sample.hhplus_w2.repository.coupon;

import sample.hhplus_w2.domain.coupon.CouponUser;
import sample.hhplus_w2.domain.coupon.CouponUserStatus;

import java.util.List;
import java.util.Optional;

public interface CouponUserRepository {
    CouponUser save(CouponUser couponUser);
    Optional<CouponUser> findById(Long id);
    Optional<CouponUser> findByCouponIdAndUserId(Long couponId, Long userId);
    List<CouponUser> findByUserId(Long userId);
    List<CouponUser> findByCouponId(Long couponId);
    List<CouponUser> findByUserIdAndStatus(Long userId, CouponUserStatus status);
    Optional<CouponUser> findByOrderId(Long orderId);
    List<CouponUser> findAll();
    void delete(Long id);
    void deleteAll();
}
