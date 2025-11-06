package sample.hhplus_w2.repository.cart.impl;

import org.springframework.stereotype.Repository;
import sample.hhplus_w2.domain.cart.CartItem;
import sample.hhplus_w2.repository.cart.CartItemRepository;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

@Repository
public class CartItemRepositoryImpl implements CartItemRepository {
    private final Map<Long, CartItem> store = new ConcurrentHashMap<>();
    private final AtomicLong idGenerator = new AtomicLong(1);

    @Override
    public CartItem save(CartItem cartItem) {
        if (cartItem.getId() == null) {
            cartItem.assignId(idGenerator.getAndIncrement());
        }
        store.put(cartItem.getId(), cartItem);
        return cartItem;
    }

    @Override
    public Optional<CartItem> findById(Long id) {
        return Optional.ofNullable(store.get(id));
    }

    @Override
    public List<CartItem> findByCartId(Long cartId) {
        return store.values().stream()
                .filter(item -> item.getCartId().equals(cartId))
                .collect(Collectors.toList());
    }

    @Override
    public Optional<CartItem> findByCartIdAndProductId(Long cartId, Long productId) {
        return store.values().stream()
                .filter(item -> item.getCartId().equals(cartId)
                        && item.getProductId().equals(productId))
                .findFirst();
    }

    @Override
    public List<CartItem> findAll() {
        return store.values().stream().collect(Collectors.toList());
    }

    @Override
    public void delete(Long id) {
        store.remove(id);
    }

    @Override
    public void deleteByCartId(Long cartId) {
        List<Long> idsToDelete = store.values().stream()
                .filter(item -> item.getCartId().equals(cartId))
                .map(CartItem::getId)
                .collect(Collectors.toList());
        idsToDelete.forEach(store::remove);
    }

    @Override
    public void deleteAll() {
        store.clear();
    }
}
