package sample.hhplus_w2.service.coupon;

import org.springframework.stereotype.Service;
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

    public Coupon createCoupon(Coupon coupon) {
        return couponRepository.save(coupon);
    }

    public Coupon getCoupon(Long id) {
        return couponRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("쿠폰을 찾을 수 없습니다: " + id));
    }

    public Coupon getCouponByCode(String code) {
        return couponRepository.findByCode(code)
                .orElseThrow(() -> new IllegalArgumentException("쿠폰 코드를 찾을 수 없습니다: " + code));
    }

    public List<Coupon> getPublishedCoupons() {
        return couponRepository.findByStatus(CouponStatus.PUBLISHED);
    }

    public synchronized CouponUser issueCoupon(Long couponId, Long userId) {
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
    }

    public List<CouponUser> getUserCoupons(Long userId) {
        return couponUserRepository.findByUserId(userId);
    }

    public void publishCoupon(Long couponId) {
        Coupon coupon = getCoupon(couponId);
        coupon.publish();
        couponRepository.save(coupon);
    }
}
