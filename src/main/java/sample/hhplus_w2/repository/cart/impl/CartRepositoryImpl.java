package sample.hhplus_w2.repository.cart.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import sample.hhplus_w2.domain.cart.Cart;
import sample.hhplus_w2.domain.cart.CartStatus;
import sample.hhplus_w2.infrastructure.cart.CartJpaRepository;
import sample.hhplus_w2.repository.cart.CartRepository;

import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class CartRepositoryImpl implements CartRepository {
    private final CartJpaRepository jpaRepository;

    @Override
    public Cart save(Cart cart) {
        return jpaRepository.save(cart);
    }

    @Override
    public Optional<Cart> findById(Long id) {
        return jpaRepository.findById(id);
    }

    @Override
    public Optional<Cart> findByUserId(Long userId) {
        return jpaRepository.findByUserId(userId);
    }

    @Override
    public Optional<Cart> findByGuestToken(String guestToken) {
        return jpaRepository.findByGuestToken(guestToken);
    }

    @Override
    public Optional<Cart> findByUserIdAndStatus(Long userId, CartStatus status) {
        return jpaRepository.findByUserIdAndStatus(userId, status);
    }

    @Override
    public List<Cart> findAll() {
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
