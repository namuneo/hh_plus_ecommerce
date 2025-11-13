package sample.hhplus_w2.service.cart;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sample.hhplus_w2.domain.cart.Cart;
import sample.hhplus_w2.domain.cart.CartItem;
import sample.hhplus_w2.domain.cart.CartStatus;
import sample.hhplus_w2.domain.product.Product;
import sample.hhplus_w2.repository.cart.CartItemRepository;
import sample.hhplus_w2.repository.cart.CartRepository;
import sample.hhplus_w2.repository.product.ProductRepository;

import java.util.List;

@Service
public class CartService {

    private final CartRepository cartRepository;
    private final CartItemRepository cartItemRepository;
    private final ProductRepository productRepository;

    public CartService(CartRepository cartRepository, CartItemRepository cartItemRepository, ProductRepository productRepository) {
        this.cartRepository = cartRepository;
        this.cartItemRepository = cartItemRepository;
        this.productRepository = productRepository;
    }

    @Transactional
    public Cart getOrCreateCart(Long userId) {
        return cartRepository.findByUserIdAndStatus(userId, CartStatus.ACTIVE)
                .orElseGet(() -> {
                    Cart cart = Cart.createForUser(userId);
                    return cartRepository.save(cart);
                });
    }

    @Transactional(readOnly = true)
    public List<CartItem> getCartItems(Long cartId) {
        return cartItemRepository.findByCartId(cartId);
    }

    @Transactional
    public CartItem addItem(Long userId, Long productId, Integer quantity) {
        Cart cart = getOrCreateCart(userId);
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new IllegalArgumentException("상품을 찾을 수 없습니다: " + productId));

        if (!product.hasStock(quantity)) {
            throw new IllegalStateException("재고가 부족합니다.");
        }

        CartItem existingItem = cartItemRepository.findByCartIdAndProductId(cart.getId(), productId)
                .orElse(null);

        if (existingItem != null) {
            existingItem.increaseQuantity(quantity);
            existingItem.updatePriceSnapshot(product.getPrice());
            return cartItemRepository.save(existingItem);
        } else {
            CartItem newItem = CartItem.create(cart.getId(), productId, quantity, product.getPrice());
            return cartItemRepository.save(newItem);
        }
    }

    @Transactional
    public CartItem updateItemQuantity(Long itemId, Integer quantity) {
        CartItem item = cartItemRepository.findById(itemId)
                .orElseThrow(() -> new IllegalArgumentException("장바구니 항목을 찾을 수 없습니다: " + itemId));
        item.changeQuantity(quantity);
        return cartItemRepository.save(item);
    }

    @Transactional
    public void removeItem(Long itemId) {
        cartItemRepository.delete(itemId);
    }

    @Transactional
    public void clearCart(Long cartId) {
        cartItemRepository.deleteByCartId(cartId);
    }
}
