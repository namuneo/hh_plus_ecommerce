package sample.hhplus_w2.service.coupon;

import jakarta.persistence.OptimisticLockException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sample.hhplus_w2.domain.coupon.Coupon;
import sample.hhplus_w2.domain.coupon.CouponStatus;
import sample.hhplus_w2.domain.coupon.CouponUser;
import sample.hhplus_w2.repository.coupon.CouponRepository;
import sample.hhplus_w2.repository.coupon.CouponUserRepository;

import java.util.List;

@Service
public class CouponService {

    private final CouponRepository couponRepository;
    private final CouponUserRepository couponUserRepository;

    public CouponService(CouponRepository couponRepository, CouponUserRepository couponUserRepository) {
        this.couponRepository = couponRepository;
        this.couponUserRepository = couponUserRepository;
    }

    @Transactional
    public Coupon createCoupon(Coupon coupon) {
        return couponRepository.save(coupon);
    }

    @Transactional(readOnly = true)
    public Coupon getCoupon(Long id) {
        return couponRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("쿠폰을 찾을 수 없습니다: " + id));
    }

    @Transactional(readOnly = true)
    public Coupon getCouponByCode(String code) {
        return couponRepository.findByCode(code)
                .orElseThrow(() -> new IllegalArgumentException("쿠폰 코드를 찾을 수 없습니다: " + code));
    }

    @Transactional(readOnly = true)
    public List<Coupon> getPublishedCoupons() {
        return couponRepository.findByStatus(CouponStatus.PUBLISHED);
    }

    /**
     * 쿠폰 발급 (낙관적 락 사용)
     * OptimisticLockException 발생 시 예외 전파하여 재시도 가능
     */
    @Transactional
    public CouponUser issueCoupon(Long couponId, Long userId) {
        try {
            Coupon coupon = getCoupon(couponId);

            if (couponUserRepository.findByCouponIdAndUserId(couponId, userId).isPresent()) {
                throw new IllegalStateException("이미 발급받은 쿠폰입니다.");
            }

            if (!coupon.canIssue()) {
                throw new IllegalStateException("발급 불가능한 쿠폰입니다.");
            }

            boolean issued = coupon.issue();
            if (!issued) {
                throw new IllegalStateException("쿠폰이 모두 소진되었습니다.");
            }

            couponRepository.save(coupon);

            CouponUser couponUser = CouponUser.issue(couponId, userId);
            return couponUserRepository.save(couponUser);
        } catch (OptimisticLockException | ObjectOptimisticLockingFailureException e) {
            // 낙관적 락 충돌 - 다른 트랜잭션이 먼저 수정함
            throw new IllegalStateException("쿠폰 발급 중 충돌이 발생했습니다. 다시 시도해주세요.");
        }
    }

    @Transactional(readOnly = true)
    public List<CouponUser> getUserCoupons(Long userId) {
        return couponUserRepository.findByUserId(userId);
    }

    @Transactional
    public void publishCoupon(Long couponId) {
        Coupon coupon = getCoupon(couponId);
        coupon.publish();
        couponRepository.save(coupon);
    }
}
