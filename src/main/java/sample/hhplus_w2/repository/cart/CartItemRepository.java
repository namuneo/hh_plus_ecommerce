package sample.hhplus_w2.repository.cart;

import sample.hhplus_w2.domain.cart.CartItem;

import java.util.List;
import java.util.Optional;

public interface CartItemRepository {
    CartItem save(CartItem cartItem);
    Optional<CartItem> findById(Long id);
    List<CartItem> findByCartId(Long cartId);
    Optional<CartItem> findByCartIdAndProductId(Long cartId, Long productId);
    List<CartItem> findAll();
    void delete(Long id);
    void deleteByCartId(Long cartId);
    void deleteAll();
}
