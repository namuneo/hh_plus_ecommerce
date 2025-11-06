package sample.hhplus_w2.repository.coupon.impl;

import org.springframework.stereotype.Repository;
import sample.hhplus_w2.domain.coupon.CouponUser;
import sample.hhplus_w2.domain.coupon.CouponUserStatus;
import sample.hhplus_w2.repository.coupon.CouponUserRepository;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

@Repository
public class CouponUserRepositoryImpl implements CouponUserRepository {
    private final Map<Long, CouponUser> store = new ConcurrentHashMap<>();
    private final AtomicLong idGenerator = new AtomicLong(1);

    @Override
    public CouponUser save(CouponUser couponUser) {
        if (couponUser.getId() == null) {
            couponUser.assignId(idGenerator.getAndIncrement());
        }
        store.put(couponUser.getId(), couponUser);
        return couponUser;
    }

    @Override
    public Optional<CouponUser> findById(Long id) {
        return Optional.ofNullable(store.get(id));
    }

    @Override
    public Optional<CouponUser> findByCouponIdAndUserId(Long couponId, Long userId) {
        return store.values().stream()
                .filter(cu -> cu.getCouponId().equals(couponId)
                        && cu.getUserId().equals(userId))
                .findFirst();
    }

    @Override
    public List<CouponUser> findByUserId(Long userId) {
        return store.values().stream()
                .filter(cu -> cu.getUserId().equals(userId))
                .collect(Collectors.toList());
    }

    @Override
    public List<CouponUser> findByCouponId(Long couponId) {
        return store.values().stream()
                .filter(cu -> cu.getCouponId().equals(couponId))
                .collect(Collectors.toList());
    }

    @Override
    public List<CouponUser> findByUserIdAndStatus(Long userId, CouponUserStatus status) {
        return store.values().stream()
                .filter(cu -> cu.getUserId().equals(userId)
                        && cu.getStatus().equals(status))
                .collect(Collectors.toList());
    }

    @Override
    public Optional<CouponUser> findByOrderId(Long orderId) {
        return store.values().stream()
                .filter(cu -> cu.getOrderId() != null && cu.getOrderId().equals(orderId))
                .findFirst();
    }

    @Override
    public List<CouponUser> findAll() {
        return store.values().stream().collect(Collectors.toList());
    }

    @Override
    public void delete(Long id) {
        store.remove(id);
    }

    @Override
    public void deleteAll() {
        store.clear();
    }
}
