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
}
