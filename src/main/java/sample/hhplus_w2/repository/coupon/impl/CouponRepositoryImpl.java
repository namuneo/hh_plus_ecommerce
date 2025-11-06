package sample.hhplus_w2.repository.coupon.impl;

import org.springframework.stereotype.Repository;
import sample.hhplus_w2.domain.coupon.Coupon;
import sample.hhplus_w2.domain.coupon.CouponStatus;
import sample.hhplus_w2.repository.coupon.CouponRepository;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

@Repository
public class CouponRepositoryImpl implements CouponRepository {
    private final Map<Long, Coupon> store = new ConcurrentHashMap<>();
    private final AtomicLong idGenerator = new AtomicLong(1);

    @Override
    public Coupon save(Coupon coupon) {
        if (coupon.getId() == null) {
            coupon.assignId(idGenerator.getAndIncrement());
        }
        store.put(coupon.getId(), coupon);
        return coupon;
    }

    @Override
    public Optional<Coupon> findById(Long id) {
        return Optional.ofNullable(store.get(id));
    }

    @Override
    public Optional<Coupon> findByCode(String code) {
        return store.values().stream()
                .filter(coupon -> coupon.getCode().equals(code))
                .findFirst();
    }

    @Override
    public List<Coupon> findByStatus(CouponStatus status) {
        return store.values().stream()
                .filter(coupon -> coupon.getStatus().equals(status))
                .collect(Collectors.toList());
    }

    @Override
    public List<Coupon> findAll() {
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
