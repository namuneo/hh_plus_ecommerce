package sample.hhplus_w2.infrastructure.cart;

import org.springframework.data.jpa.repository.JpaRepository;
import sample.hhplus_w2.domain.cart.CartItem;

import java.util.List;
import java.util.Optional;

public interface CartItemJpaRepository extends JpaRepository<CartItem, Long> {
    List<CartItem> findByCartId(Long cartId);
    Optional<CartItem> findByCartIdAndProductId(Long cartId, Long productId);
}
