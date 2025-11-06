package sample.hhplus_w2.controller.cart;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import sample.hhplus_w2.domain.cart.Cart;
import sample.hhplus_w2.domain.cart.CartItem;
import sample.hhplus_w2.service.cart.CartService;

import java.util.List;

@RestController
@RequestMapping("/api/cart")
public class CartController {

    private final CartService cartService;

    public CartController(CartService cartService) {
        this.cartService = cartService;
    }

    @GetMapping
    public ResponseEntity<Cart> getCart(@RequestParam Long userId) {
        Cart cart = cartService.getOrCreateCart(userId);
        return ResponseEntity.ok(cart);
    }

    @GetMapping("/items")
    public ResponseEntity<List<CartItem>> getCartItems(@RequestParam Long cartId) {
        List<CartItem> items = cartService.getCartItems(cartId);
        return ResponseEntity.ok(items);
    }

    @PostMapping("/items")
    public ResponseEntity<CartItem> addItem(
            @RequestParam Long userId,
            @RequestParam Long productId,
            @RequestParam Integer quantity) {
        CartItem item = cartService.addItem(userId, productId, quantity);
        return ResponseEntity.ok(item);
    }

    @PutMapping("/items/{itemId}")
    public ResponseEntity<CartItem> updateItemQuantity(
            @PathVariable Long itemId,
            @RequestParam Integer quantity) {
        CartItem item = cartService.updateItemQuantity(itemId, quantity);
        return ResponseEntity.ok(item);
    }

    @DeleteMapping("/items/{itemId}")
    public ResponseEntity<Void> removeItem(@PathVariable Long itemId) {
        cartService.removeItem(itemId);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/{cartId}")
    public ResponseEntity<Void> clearCart(@PathVariable Long cartId) {
        cartService.clearCart(cartId);
        return ResponseEntity.ok().build();
    }
}
