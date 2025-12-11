package sample.hhplus_w2.application.listener;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import sample.hhplus_w2.domain.cart.Cart;
import sample.hhplus_w2.domain.cart.CartItem;
import sample.hhplus_w2.domain.order.Order;
import sample.hhplus_w2.domain.order.OrderStatus;
import sample.hhplus_w2.domain.product.Product;
import sample.hhplus_w2.repository.cart.CartItemRepository;
import sample.hhplus_w2.repository.cart.CartRepository;
import sample.hhplus_w2.repository.product.ProductRepository;
import sample.hhplus_w2.service.order.OrderService;
import sample.hhplus_w2.service.ranking.ProductRankingService;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * 주문 완료 이벤트 리스너 통합 테스트
 * 트랜잭션 분리 및 비동기 처리 검증
 */
@SpringBootTest
@ActiveProfiles("test")
class OrderEventListenerTest {

    @Autowired
    private OrderService orderService;

    @Autowired
    private CartRepository cartRepository;

    @Autowired
    private CartItemRepository cartItemRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private ProductRankingService rankingService;

    @Test
    @DisplayName("주문 완료 시 이벤트가 발행되고 비동기로 랭킹이 업데이트된다")
    @Transactional
    void orderCompletedEvent_UpdatesRanking_Asynchronously() throws Exception {
        // given
        Long userId = 1L;
        Cart cart = Cart.createForUser(userId);
        cart = cartRepository.save(cart);

        Product product = Product.create(1L, "상품", "브랜드", "설명",
                new BigDecimal("10000"), 100);
        product = productRepository.save(product);
        Long productId = product.getId();

        CartItem cartItem = CartItem.create(cart.getId(), productId, 5, product.getPrice());
        cartItemRepository.save(cartItem);

        Order order = orderService.createOrder(userId, cart.getId());

        // 초기 랭킹 점수 확인
        Integer initialCount = rankingService.getProductOrderCount(productId);

        // when
        orderService.processPayment(order.getId());

        // then
        // 주문은 즉시 완료됨
        Order completedOrder = orderService.getOrder(order.getId());
        assertThat(completedOrder.getStatus()).isEqualTo(OrderStatus.PAID);

        // 랭킹 업데이트는 비동기로 처리되므로 약간의 지연이 있을 수 있음
        // Awaitility를 사용하여 비동기 작업 완료 대기
        await()
                .atMost(java.time.Duration.ofSeconds(5))
                .untilAsserted(() -> {
                    Integer updatedCount = rankingService.getProductOrderCount(productId);
                    assertThat(updatedCount).isGreaterThan(initialCount != null ? initialCount : 0);
                });
    }

    @Test
    @DisplayName("주문 완료 트랜잭션과 랭킹 업데이트 트랜잭션이 분리되어 독립적으로 동작한다")
    @Transactional
    void orderAndRankingTransactions_AreIndependent() throws Exception {
        // given
        Long userId = 2L;
        Cart cart = Cart.createForUser(userId);
        cart = cartRepository.save(cart);

        Product product = Product.create(1L, "상품2", "브랜드", "설명",
                new BigDecimal("15000"), 50);
        product = productRepository.save(product);
        Long productId = product.getId();

        CartItem cartItem = CartItem.create(cart.getId(), productId, 3, product.getPrice());
        cartItemRepository.save(cartItem);

        Order order = orderService.createOrder(userId, cart.getId());

        // when
        orderService.processPayment(order.getId());

        // then
        // 주문 트랜잭션은 즉시 커밋되어야 함
        Order completedOrder = orderService.getOrder(order.getId());
        assertThat(completedOrder.getStatus()).isEqualTo(OrderStatus.PAID);

        // 재고도 즉시 차감되어야 함
        Product updatedProduct = productRepository.findById(productId).orElseThrow();
        assertThat(updatedProduct.getStockQty()).isEqualTo(47); // 50 - 3

        // 랭킹 업데이트는 별도 트랜잭션에서 비동기로 처리됨
        await()
                .atMost(java.time.Duration.ofSeconds(5))
                .untilAsserted(() -> {
                    Integer count = rankingService.getProductOrderCount(productId);
                    assertThat(count).isNotNull();
                    assertThat(count).isGreaterThan(0);
                });
    }
}