package sample.hhplus_w2.repository.cart.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import sample.hhplus_w2.domain.cart.CartItem;
import sample.hhplus_w2.infrastructure.cart.CartItemJpaRepository;
import sample.hhplus_w2.repository.cart.CartItemRepository;

import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class CartItemRepositoryImpl implements CartItemRepository {
    private final CartItemJpaRepository jpaRepository;

    @Override
    public CartItem save(CartItem cartItem) {
        return jpaRepository.save(cartItem);
    }

    @Override
    public Optional<CartItem> findById(Long id) {
        return jpaRepository.findById(id);
    }

    @Override
    public List<CartItem> findByCartId(Long cartId) {
        return jpaRepository.findByCartId(cartId);
    }

    @Override
    public Optional<CartItem> findByCartIdAndProductId(Long cartId, Long productId) {
        return jpaRepository.findByCartIdAndProductId(cartId, productId);
    }

    @Override
    public List<CartItem> findAll() {
        return jpaRepository.findAll();
    }

    @Override
    public void delete(Long id) {
        jpaRepository.deleteById(id);
    }

    @Override
    public void deleteByCartId(Long cartId) {
        List<CartItem> items = jpaRepository.findByCartId(cartId);
        jpaRepository.deleteAll(items);
    }

    @Override
    public void deleteAll() {
        jpaRepository.deleteAll();
    }
}
