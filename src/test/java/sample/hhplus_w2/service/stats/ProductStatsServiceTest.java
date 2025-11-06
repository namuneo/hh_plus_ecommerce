package sample.hhplus_w2.service.stats;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import sample.hhplus_w2.domain.cart.Cart;
import sample.hhplus_w2.domain.cart.CartItem;
import sample.hhplus_w2.domain.order.Order;
import sample.hhplus_w2.domain.product.Product;
import sample.hhplus_w2.domain.stats.ProductSalesStats;
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
import sample.hhplus_w2.repository.stats.ProductSalesStatsRepository;
import sample.hhplus_w2.repository.stats.impl.ProductSalesStatsRepositoryImpl;
import sample.hhplus_w2.service.order.OrderService;
import sample.hhplus_w2.service.product.ProductService;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

class ProductStatsServiceTest {

    private ProductStatsService productStatsService;
    private OrderService orderService;
    private ProductService productService;
    private CartRepository cartRepository;
    private CartItemRepository cartItemRepository;
    private ProductRepository productRepository;

    @BeforeEach
    void setUp() {
        ProductSalesStatsRepository statsRepository = new ProductSalesStatsRepositoryImpl();
        OrderRepository orderRepository = new OrderRepositoryImpl();
        OrderItemRepository orderItemRepository = new OrderItemRepositoryImpl();
        OrderHistoryRepository orderHistoryRepository = new OrderHistoryRepositoryImpl();
        cartRepository = new CartRepositoryImpl();
        cartItemRepository = new CartItemRepositoryImpl();
        productRepository = new ProductRepositoryImpl();

        productStatsService = new ProductStatsService(statsRepository, orderRepository,
                orderItemRepository, productRepository);
        orderService = new OrderService(orderRepository, orderItemRepository, orderHistoryRepository,
                cartItemRepository, productRepository);
        productService = new ProductService(productRepository);
    }

    @Test
    @DisplayName("인기 상품 집계 - 판매량 기준 TOP 5")
    void getTopProductsByPeriod() {
        // given - 상품 3개 생성
        Product product1 = productService.createProduct(1L, "인기상품1", "브랜드", "설명",
                new BigDecimal("10000"), 100);
        Product product2 = productService.createProduct(1L, "인기상품2", "브랜드", "설명",
                new BigDecimal("15000"), 100);
        Product product3 = productService.createProduct(1L, "인기상품3", "브랜드", "설명",
                new BigDecimal("20000"), 100);

        // 주문 생성 및 결제 (product1: 10개, product2: 5개, product3: 3개)
        createAndPayOrder(1L, product1.getId(), 10);
        createAndPayOrder(2L, product2.getId(), 5);
        createAndPayOrder(3L, product3.getId(), 3);

        // when - 최근 3일 인기 상품 조회
        List<ProductSalesStats> topProducts = productStatsService.getTopProductsByPeriod(3, 5);

        // then
        assertThat(topProducts).isNotEmpty();
        assertThat(topProducts).hasSize(3);

        // 판매량 순서: product1(10) > product2(5) > product3(3)
        assertThat(topProducts.get(0).getProductId()).isEqualTo(product1.getId());
        assertThat(topProducts.get(0).getSalesCount()).isEqualTo(10);

        assertThat(topProducts.get(1).getProductId()).isEqualTo(product2.getId());
        assertThat(topProducts.get(1).getSalesCount()).isEqualTo(5);

        assertThat(topProducts.get(2).getProductId()).isEqualTo(product3.getId());
        assertThat(topProducts.get(2).getSalesCount()).isEqualTo(3);
    }

    @Test
    @DisplayName("인기 상품 집계 - 매출액 기준 TOP 5")
    void getTopProductsByRevenue() {
        // given
        Product product1 = productService.createProduct(1L, "저가상품", "브랜드", "설명",
                new BigDecimal("5000"), 100);
        Product product2 = productService.createProduct(1L, "고가상품", "브랜드", "설명",
                new BigDecimal("50000"), 100);

        // product1: 10개 * 5000 = 50000원
        // product2: 2개 * 50000 = 100000원
        createAndPayOrder(1L, product1.getId(), 10);
        createAndPayOrder(2L, product2.getId(), 2);

        // when - 매출액 기준 조회
        List<ProductSalesStats> topProducts = productStatsService.getTopProductsByRevenue(3, 5);

        // then - product2가 1위 (매출액이 더 큼)
        assertThat(topProducts).isNotEmpty();
        assertThat(topProducts.get(0).getProductId()).isEqualTo(product2.getId());
        assertThat(topProducts.get(0).getSalesAmount()).isEqualTo(new BigDecimal("100000"));
    }

    @Test
    @DisplayName("집계 로직 - 여러 주문에서 동일 상품 합산")
    void aggregateSalesStats_MultipleOrders() {
        // given
        Product product = productService.createProduct(1L, "반복구매상품", "브랜드", "설명",
                new BigDecimal("10000"), 100);

        // 동일 상품 여러 번 주문
        createAndPayOrder(1L, product.getId(), 3);
        createAndPayOrder(2L, product.getId(), 5);
        createAndPayOrder(3L, product.getId(), 2);

        // when
        List<ProductSalesStats> topProducts = productStatsService.getTopProductsByPeriod(3, 5);

        // then - 총 10개 판매로 집계되어야 함
        assertThat(topProducts).hasSize(1);
        assertThat(topProducts.get(0).getSalesCount()).isEqualTo(10);
        assertThat(topProducts.get(0).getSalesAmount()).isEqualTo(new BigDecimal("100000"));
    }

    /**
     * 주문 생성 및 결제 헬퍼 메서드
     */
    private void createAndPayOrder(Long userId, Long productId, Integer quantity) {
        Product product = productService.getProduct(productId);

        Cart cart = Cart.createForUser(userId);
        cart = cartRepository.save(cart);

        CartItem cartItem = CartItem.create(cart.getId(), productId, quantity, product.getPrice());
        cartItemRepository.save(cartItem);

        Order order = orderService.createOrder(userId, cart.getId());
        orderService.processPayment(order.getId());
    }
}
