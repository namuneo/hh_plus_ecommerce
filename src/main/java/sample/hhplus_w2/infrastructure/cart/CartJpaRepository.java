package sample.hhplus_w2.infrastructure.cart;

import org.springframework.data.jpa.repository.JpaRepository;
import sample.hhplus_w2.domain.cart.Cart;
import sample.hhplus_w2.domain.cart.CartStatus;

import java.util.Optional;

public interface CartJpaRepository extends JpaRepository<Cart, Long> {
    Optional<Cart> findByUserId(Long userId);
    Optional<Cart> findByGuestToken(String guestToken);
    Optional<Cart> findByUserIdAndStatus(Long userId, CartStatus status);
}
