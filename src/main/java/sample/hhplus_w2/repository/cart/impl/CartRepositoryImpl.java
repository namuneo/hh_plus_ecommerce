package sample.hhplus_w2.repository.cart.impl;

import org.springframework.stereotype.Repository;
import sample.hhplus_w2.domain.cart.Cart;
import sample.hhplus_w2.domain.cart.CartStatus;
import sample.hhplus_w2.repository.cart.CartRepository;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

@Repository
public class CartRepositoryImpl implements CartRepository {
    private final Map<Long, Cart> store = new ConcurrentHashMap<>();
    private final AtomicLong idGenerator = new AtomicLong(1);

    @Override
    public Cart save(Cart cart) {
        if (cart.getId() == null) {
            cart.assignId(idGenerator.getAndIncrement());
        }
        store.put(cart.getId(), cart);
        return cart;
    }

    @Override
    public Optional<Cart> findById(Long id) {
        return Optional.ofNullable(store.get(id));
    }

    @Override
    public Optional<Cart> findByUserId(Long userId) {
        return store.values().stream()
                .filter(cart -> cart.getUserId() != null && cart.getUserId().equals(userId))
                .findFirst();
    }

    @Override
    public Optional<Cart> findByGuestToken(String guestToken) {
        return store.values().stream()
                .filter(cart -> cart.getGuestToken() != null && cart.getGuestToken().equals(guestToken))
                .findFirst();
    }

    @Override
    public Optional<Cart> findByUserIdAndStatus(Long userId, CartStatus status) {
        return store.values().stream()
                .filter(cart -> cart.getUserId() != null
                        && cart.getUserId().equals(userId)
                        && cart.getStatus().equals(status))
                .findFirst();
    }

    @Override
    public List<Cart> findAll() {
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
