package sample.hhplus_w2.infrastructure.coupon;

import org.springframework.data.jpa.repository.JpaRepository;
import sample.hhplus_w2.domain.coupon.CouponUser;
import sample.hhplus_w2.domain.coupon.CouponUserStatus;

import java.util.List;
import java.util.Optional;

public interface CouponUserJpaRepository extends JpaRepository<CouponUser, Long> {
    Optional<CouponUser> findByCouponIdAndUserId(Long couponId, Long userId);
    List<CouponUser> findByUserId(Long userId);
    List<CouponUser> findByCouponId(Long couponId);
    List<CouponUser> findByUserIdAndStatus(Long userId, CouponUserStatus status);
    Optional<CouponUser> findByOrderId(Long orderId);
}
