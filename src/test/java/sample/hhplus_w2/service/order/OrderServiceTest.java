package sample.hhplus_w2.service.order;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import sample.hhplus_w2.domain.cart.Cart;
import sample.hhplus_w2.domain.cart.CartItem;
import sample.hhplus_w2.domain.order.Order;
import sample.hhplus_w2.domain.order.OrderHistory;
import sample.hhplus_w2.domain.order.OrderItem;
import sample.hhplus_w2.domain.order.OrderStatus;
import sample.hhplus_w2.domain.product.Product;
import sample.hhplus_w2.repository.cart.CartItemRepository;
import sample.hhplus_w2.repository.cart.CartRepository;
import sample.hhplus_w2.repository.cart.impl.CartItemRepositoryImpl;
import sample.hhplus_w2.repository.cart.impl.CartRepositoryImpl;
import sample.hhplus_w2.repository.order.OrderHistoryRepository;
import sample.hhplus_w2.repository.order.OrderItemRepository;
import sample.hhplus_w2.repository.order.OrderRepository;
import sample.hhplus_w2.repository.order.impl.OrderHistoryRepositoryImpl;
import sample.hhplus_w2.repository.order.impl.OrderItemRepositoryImpl;
import sample.hhplus_w2.repository.order.impl.OrderRepositoryImpl;
import sample.hhplus_w2.repository.product.ProductRepository;
import sample.hhplus_w2.repository.product.impl.ProductRepositoryImpl;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

@DataJpaTest
@ActiveProfiles("test")
@Import({OrderRepositoryImpl.class, OrderItemRepositoryImpl.class, OrderHistoryRepositoryImpl.class,
        CartRepositoryImpl.class, CartItemRepositoryImpl.class, ProductRepositoryImpl.class, OrderService.class})
class OrderServiceTest {

    @Autowired
    private OrderService orderService;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private OrderItemRepository orderItemRepository;

    @Autowired
    private OrderHistoryRepository orderHistoryRepository;

    @Autowired
    private CartRepository cartRepository;

    @Autowired
    private CartItemRepository cartItemRepository;

    @Autowired
    private ProductRepository productRepository;

    @Test
    @DisplayName("주문 생성 - 정상")
    void createOrder() {
        // given
        Long userId = 1L;
        Cart cart = Cart.createForUser(userId);
        cart = cartRepository.save(cart);

        Product product = Product.create(1L, "상품", "브랜드", "설명",
                new BigDecimal("10000"), 100);
        product = productRepository.save(product);

        CartItem cartItem = CartItem.create(cart.getId(), product.getId(), 2, product.getPrice());
        cartItemRepository.save(cartItem);

        // when
        Order order = orderService.createOrder(userId, cart.getId());

        // then
        assertThat(order).isNotNull();
        assertThat(order.getId()).isNotNull();
        assertThat(order.getUserId()).isEqualTo(userId);
        assertThat(order.getTotal()).isEqualTo(new BigDecimal("20000"));
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING);

        List<OrderItem> orderItems = orderService.getOrderItems(order.getId());
        assertThat(orderItems).hasSize(1);
        assertThat(orderItems.get(0).getQty()).isEqualTo(2);
    }

    @Test
    @DisplayName("주문 생성 - 빈 장바구니로 예외 발생")
    void createOrder_EmptyCart() {
        // given
        Long userId = 1L;
        Cart cart = Cart.createForUser(userId);
        Cart savedCart = cartRepository.save(cart);
        Long cartId = savedCart.getId();

        // when & then
        assertThatThrownBy(() -> orderService.createOrder(userId, cartId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("장바구니가 비어있습니다");
    }

    @Test
    @DisplayName("결제 처리 - 정상")
    void processPayment() {
        // given
        Long userId = 1L;
        Cart cart = Cart.createForUser(userId);
        cart = cartRepository.save(cart);

        Product product = Product.create(1L, "상품", "브랜드", "설명",
                new BigDecimal("10000"), 100);
        product = productRepository.save(product);

        CartItem cartItem = CartItem.create(cart.getId(), product.getId(), 5, product.getPrice());
        cartItemRepository.save(cartItem);

        Order order = orderService.createOrder(userId, cart.getId());

        // when
        Order paidOrder = orderService.processPayment(order.getId());

        // then
        assertThat(paidOrder.getStatus()).isEqualTo(OrderStatus.PAID);

        Product updatedProduct = productRepository.findById(product.getId()).orElseThrow();
        assertThat(updatedProduct.getStockQty()).isEqualTo(95); // 100 - 5

        List<OrderHistory> history = orderService.getOrderHistory(order.getId());
        assertThat(history).hasSizeGreaterThanOrEqualTo(2); // PENDING, PAID
    }

    @Test
    @DisplayName("결제 처리 - 재고 부족으로 실패")
    void processPayment_InsufficientStock() {
        // given
        Long userId = 1L;
        Cart cart = Cart.createForUser(userId);
        cart = cartRepository.save(cart);

        Product product = Product.create(1L, "상품", "브랜드", "설명",
                new BigDecimal("10000"), 3);
        product = productRepository.save(product);

        CartItem cartItem = CartItem.create(cart.getId(), product.getId(), 5, product.getPrice());
        cartItemRepository.save(cartItem);

        Order order = orderService.createOrder(userId, cart.getId());

        // when & then
        assertThatThrownBy(() -> orderService.processPayment(order.getId()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("재고가 부족합니다");
    }

    @Test
    @DisplayName("결제 처리 - PENDING 상태가 아니면 예외 발생")
    void processPayment_NotPending() {
        // given
        Long userId = 1L;
        Cart cart = Cart.createForUser(userId);
        cart = cartRepository.save(cart);

        Product product = Product.create(1L, "상품", "브랜드", "설명",
                new BigDecimal("10000"), 100);
        product = productRepository.save(product);

        CartItem cartItem = CartItem.create(cart.getId(), product.getId(), 2, product.getPrice());
        cartItemRepository.save(cartItem);

        Order order = orderService.createOrder(userId, cart.getId());
        orderService.processPayment(order.getId()); // 첫 번째 결제 완료

        // when & then
        assertThatThrownBy(() -> orderService.processPayment(order.getId()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("결제 대기 상태가 아닙니다");
    }

    @Test
    @DisplayName("주문 조회 - 정상")
    void getOrder() {
        // given
        Long userId = 1L;
        Cart cart = Cart.createForUser(userId);
        cart = cartRepository.save(cart);

        Product product = Product.create(1L, "상품", "브랜드", "설명",
                new BigDecimal("10000"), 100);
        product = productRepository.save(product);

        CartItem cartItem = CartItem.create(cart.getId(), product.getId(), 2, product.getPrice());
        cartItemRepository.save(cartItem);

        Order created = orderService.createOrder(userId, cart.getId());

        // when
        Order found = orderService.getOrder(created.getId());

        // then
        assertThat(found).isNotNull();
        assertThat(found.getId()).isEqualTo(created.getId());
    }

    @Test
    @DisplayName("사용자별 주문 조회")
    void getOrdersByUser() {
        // given
        Long userId = 1L;
        Cart cart = Cart.createForUser(userId);
        cart = cartRepository.save(cart);

        Product product = Product.create(1L, "상품", "브랜드", "설명",
                new BigDecimal("10000"), 100);
        product = productRepository.save(product);

        CartItem cartItem1 = CartItem.create(cart.getId(), product.getId(), 2, product.getPrice());
        cartItemRepository.save(cartItem1);
        orderService.createOrder(userId, cart.getId());

        cartItemRepository.deleteByCartId(cart.getId());
        CartItem cartItem2 = CartItem.create(cart.getId(), product.getId(), 3, product.getPrice());
        cartItemRepository.save(cartItem2);
        orderService.createOrder(userId, cart.getId());

        // when
        List<Order> orders = orderService.getOrdersByUser(userId);

        // then
        assertThat(orders).hasSize(2);
    }

    @Test
    @DisplayName("주문 아이템 조회")
    void getOrderItems() {
        // given
        Long userId = 1L;
        Cart cart = Cart.createForUser(userId);
        cart = cartRepository.save(cart);

        Product product1 = Product.create(1L, "상품1", "브랜드", "설명",
                new BigDecimal("10000"), 100);
        product1 = productRepository.save(product1);

        Product product2 = Product.create(1L, "상품2", "브랜드", "설명",
                new BigDecimal("20000"), 50);
        product2 = productRepository.save(product2);

        CartItem cartItem1 = CartItem.create(cart.getId(), product1.getId(), 2, product1.getPrice());
        cartItemRepository.save(cartItem1);

        CartItem cartItem2 = CartItem.create(cart.getId(), product2.getId(), 1, product2.getPrice());
        cartItemRepository.save(cartItem2);

        Order order = orderService.createOrder(userId, cart.getId());

        // when
        List<OrderItem> orderItems = orderService.getOrderItems(order.getId());

        // then
        assertThat(orderItems).hasSize(2);
    }
}
