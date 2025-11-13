package sample.hhplus_w2.repository.coupon.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import sample.hhplus_w2.domain.coupon.CouponUser;
import sample.hhplus_w2.domain.coupon.CouponUserStatus;
import sample.hhplus_w2.infrastructure.coupon.CouponUserJpaRepository;
import sample.hhplus_w2.repository.coupon.CouponUserRepository;

import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class CouponUserRepositoryImpl implements CouponUserRepository {
    private final CouponUserJpaRepository jpaRepository;

    @Override
    public CouponUser save(CouponUser couponUser) {
        return jpaRepository.save(couponUser);
    }

    @Override
    public Optional<CouponUser> findById(Long id) {
        return jpaRepository.findById(id);
    }

    @Override
    public Optional<CouponUser> findByCouponIdAndUserId(Long couponId, Long userId) {
        return jpaRepository.findByCouponIdAndUserId(couponId, userId);
    }

    @Override
    public List<CouponUser> findByUserId(Long userId) {
        return jpaRepository.findByUserId(userId);
    }

    @Override
    public List<CouponUser> findByCouponId(Long couponId) {
        return jpaRepository.findByCouponId(couponId);
    }

    @Override
    public List<CouponUser> findByUserIdAndStatus(Long userId, CouponUserStatus status) {
        return jpaRepository.findByUserIdAndStatus(userId, status);
    }

    @Override
    public Optional<CouponUser> findByOrderId(Long orderId) {
        return jpaRepository.findByOrderId(orderId);
    }

    @Override
    public List<CouponUser> findAll() {
        return jpaRepository.findAll();
    }

    @Override
    public void delete(Long id) {
        jpaRepository.deleteById(id);
    }

    @Override
    public void deleteAll() {
        jpaRepository.deleteAll();
    }
}
