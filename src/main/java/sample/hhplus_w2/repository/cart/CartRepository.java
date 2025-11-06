package sample.hhplus_w2.repository.cart;

import sample.hhplus_w2.domain.cart.Cart;
import sample.hhplus_w2.domain.cart.CartStatus;

import java.util.List;
import java.util.Optional;

public interface CartRepository {
    Cart save(Cart cart);
    Optional<Cart> findById(Long id);
    Optional<Cart> findByUserId(Long userId);
    Optional<Cart> findByGuestToken(String guestToken);
    Optional<Cart> findByUserIdAndStatus(Long userId, CartStatus status);
    List<Cart> findAll();
    void delete(Long id);
    void deleteAll();
}
