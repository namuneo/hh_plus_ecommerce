package sample.hhplus_w2.service.cart;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import sample.hhplus_w2.domain.cart.Cart;
import sample.hhplus_w2.domain.cart.CartItem;
import sample.hhplus_w2.domain.product.Product;
import sample.hhplus_w2.repository.cart.CartItemRepository;
import sample.hhplus_w2.repository.cart.CartRepository;
import sample.hhplus_w2.repository.cart.impl.CartItemRepositoryImpl;
import sample.hhplus_w2.repository.cart.impl.CartRepositoryImpl;
import sample.hhplus_w2.repository.product.ProductRepository;
import sample.hhplus_w2.repository.product.impl.ProductRepositoryImpl;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

class CartServiceTest {

    private CartService cartService;
    private CartRepository cartRepository;
    private CartItemRepository cartItemRepository;
    private ProductRepository productRepository;

    @BeforeEach
    void setUp() {
        cartRepository = new CartRepositoryImpl();
        cartItemRepository = new CartItemRepositoryImpl();
        productRepository = new ProductRepositoryImpl();
        cartService = new CartService(cartRepository, cartItemRepository, productRepository);
    }

    @Test
    @DisplayName("장바구니 조회 또는 생성 - 새로 생성")
    void getOrCreateCart_Create() {
        // given
        Long userId = 1L;

        // when
        Cart cart = cartService.getOrCreateCart(userId);

        // then
        assertThat(cart).isNotNull();
        assertThat(cart.getId()).isNotNull();
        assertThat(cart.getUserId()).isEqualTo(userId);
    }

    @Test
    @DisplayName("장바구니 조회 또는 생성 - 기존 장바구니 조회")
    void getOrCreateCart_Existing() {
        // given
        Long userId = 1L;
        Cart firstCart = cartService.getOrCreateCart(userId);

        // when
        Cart secondCart = cartService.getOrCreateCart(userId);

        // then
        assertThat(secondCart.getId()).isEqualTo(firstCart.getId());
    }

    @Test
    @DisplayName("장바구니 아이템 조회")
    void getCartItems() {
        // given
        Long userId = 1L;
        Cart cart = cartService.getOrCreateCart(userId);

        Product product = Product.create(1L, "상품", "브랜드", "설명",
                new BigDecimal("10000"), 100);
        product = productRepository.save(product);

        cartService.addItem(userId, product.getId(), 2);

        // when
        List<CartItem> items = cartService.getCartItems(cart.getId());

        // then
        assertThat(items).hasSize(1);
        assertThat(items.get(0).getQty()).isEqualTo(2);
    }

    @Test
    @DisplayName("장바구니에 아이템 추가 - 새 아이템")
    void addItem_New() {
        // given
        Long userId = 1L;
        Product product = Product.create(1L, "상품", "브랜드", "설명",
                new BigDecimal("10000"), 100);
        product = productRepository.save(product);

        // when
        CartItem cartItem = cartService.addItem(userId, product.getId(), 2);

        // then
        assertThat(cartItem).isNotNull();
        assertThat(cartItem.getProductId()).isEqualTo(product.getId());
        assertThat(cartItem.getQty()).isEqualTo(2);
        assertThat(cartItem.getUnitPriceSnapshot()).isEqualTo(product.getPrice());
    }

    @Test
    @DisplayName("장바구니에 아이템 추가 - 기존 아이템 수량 증가")
    void addItem_Existing() {
        // given
        Long userId = 1L;
        Product product = Product.create(1L, "상품", "브랜드", "설명",
                new BigDecimal("10000"), 100);
        product = productRepository.save(product);

        cartService.addItem(userId, product.getId(), 2);

        // when
        CartItem cartItem = cartService.addItem(userId, product.getId(), 3);

        // then
        assertThat(cartItem.getQty()).isEqualTo(5); // 2 + 3
    }

    @Test
    @DisplayName("장바구니에 아이템 추가 - 재고 부족으로 예외 발생")
    void addItem_InsufficientStock() {
        // given
        Long userId = 1L;
        Product product = Product.create(1L, "상품", "브랜드", "설명",
                new BigDecimal("10000"), 5);
        Product savedProduct = productRepository.save(product);
        Long productId = savedProduct.getId();

        // when & then
        assertThatThrownBy(() -> cartService.addItem(userId, productId, 10))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("재고가 부족합니다");
    }

    @Test
    @DisplayName("장바구니에 아이템 추가 - 상품이 존재하지 않으면 예외 발생")
    void addItem_ProductNotFound() {
        // given
        Long userId = 1L;

        // when & then
        assertThatThrownBy(() -> cartService.addItem(userId, 999L, 1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("상품을 찾을 수 없습니다");
    }

    @Test
    @DisplayName("장바구니 아이템 수량 변경")
    void updateItemQuantity() {
        // given
        Long userId = 1L;
        Product product = Product.create(1L, "상품", "브랜드", "설명",
                new BigDecimal("10000"), 100);
        product = productRepository.save(product);

        CartItem cartItem = cartService.addItem(userId, product.getId(), 5);

        // when
        CartItem updated = cartService.updateItemQuantity(cartItem.getId(), 3);

        // then
        assertThat(updated.getQty()).isEqualTo(3);
    }

    @Test
    @DisplayName("장바구니 아이템 삭제")
    void removeItem() {
        // given
        Long userId = 1L;
        Product product = Product.create(1L, "상품", "브랜드", "설명",
                new BigDecimal("10000"), 100);
        product = productRepository.save(product);

        CartItem cartItem = cartService.addItem(userId, product.getId(), 2);
        Cart cart = cartService.getOrCreateCart(userId);

        // when
        cartService.removeItem(cartItem.getId());

        // then
        List<CartItem> items = cartService.getCartItems(cart.getId());
        assertThat(items).isEmpty();
    }

    @Test
    @DisplayName("장바구니 비우기")
    void clearCart() {
        // given
        Long userId = 1L;
        Product product1 = Product.create(1L, "상품1", "브랜드", "설명",
                new BigDecimal("10000"), 100);
        product1 = productRepository.save(product1);

        Product product2 = Product.create(1L, "상품2", "브랜드", "설명",
                new BigDecimal("20000"), 50);
        product2 = productRepository.save(product2);

        cartService.addItem(userId, product1.getId(), 2);
        cartService.addItem(userId, product2.getId(), 1);

        Cart cart = cartService.getOrCreateCart(userId);

        // when
        cartService.clearCart(cart.getId());

        // then
        List<CartItem> items = cartService.getCartItems(cart.getId());
        assertThat(items).isEmpty();
    }
}
