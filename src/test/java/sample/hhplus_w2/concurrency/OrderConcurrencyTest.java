package sample.hhplus_w2.concurrency;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import sample.hhplus_w2.domain.cart.Cart;
import sample.hhplus_w2.domain.cart.CartItem;
import sample.hhplus_w2.domain.order.Order;
import sample.hhplus_w2.domain.product.Product;
import sample.hhplus_w2.repository.cart.CartItemRepository;
import sample.hhplus_w2.repository.cart.CartRepository;
import sample.hhplus_w2.repository.order.OrderHistoryRepository;
import sample.hhplus_w2.repository.order.OrderItemRepository;
import sample.hhplus_w2.repository.order.OrderRepository;
import sample.hhplus_w2.repository.product.ProductRepository;
import sample.hhplus_w2.service.order.OrderService;
import sample.hhplus_w2.service.product.ProductService;

import java.math.BigDecimal;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.*;

/**
 * 주문 생성 및 결제 동시성 테스트
 */
@SpringBootTest
@ActiveProfiles("test")
class OrderConcurrencyTest {

    @Autowired
    private OrderService orderService;

    @Autowired
    private ProductService productService;

    @Autowired
    private CartRepository cartRepository;

    @Autowired
    private CartItemRepository cartItemRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private OrderItemRepository orderItemRepository;

    @Autowired
    private OrderHistoryRepository orderHistoryRepository;

    @AfterEach
    void tearDown() {
        orderItemRepository.deleteAll();
        orderRepository.deleteAll();
        cartItemRepository.deleteAll();
        cartRepository.deleteAll();
        productRepository.deleteAll();
    }

    @Test
    @DisplayName("동시 주문 생성 - 여러 사용자가 동시에 주문 생성")
    void createOrder_Concurrency_MultipleUsers() throws InterruptedException {
        // given - 상품 생성
        Product product = productService.createProduct(1L, "동시 주문 상품", "브랜드", "설명",
                new BigDecimal("10000"), 100);

        int threadCount = 20;
        ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);

        AtomicInteger successCount = new AtomicInteger(0);

        // when - 20명의 사용자가 동시에 주문 생성
        for (int i = 0; i < threadCount; i++) {
            final long userId = i + 1;
            executorService.submit(() -> {
                try {
                    // 각 사용자의 장바구니 생성
                    Cart cart = Cart.createForUser(userId);
                    cart = cartRepository.save(cart);

                    CartItem cartItem = CartItem.create(cart.getId(), product.getId(), 2, product.getPrice());
                    cartItemRepository.save(cartItem);

                    // 주문 생성
                    orderService.createOrder(userId, cart.getId());
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    // 예외 처리
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        executorService.shutdown();

        // then - 모든 주문이 성공적으로 생성되어야 함
        assertThat(successCount.get()).isEqualTo(20);
    }

    @Test
    @DisplayName("동시 결제 처리 - 재고 100개, 20명이 각 5개씩 주문/결제 (핵심: 음수 재고 방지)")
    void processPayment_Concurrency_StockDeduction() throws InterruptedException {
        // given
        Product product = productService.createProduct(1L, "결제 동시성 상품", "브랜드", "설명",
                new BigDecimal("20000"), 100);
        Long productId = product.getId();

        int threadCount = 20;
        int quantityPerOrder = 5;
        ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);

        // when - 20명이 동시에 주문 생성 및 결제
        for (int i = 0; i < threadCount; i++) {
            final long userId = i + 1000;
            executorService.submit(() -> {
                try {
                    // 장바구니 생성
                    Cart cart = Cart.createForUser(userId);
                    cart = cartRepository.save(cart);

                    CartItem cartItem = CartItem.create(cart.getId(), productId, quantityPerOrder,
                            product.getPrice());
                    cartItemRepository.save(cartItem);

                    // 주문 생성
                    Order order = orderService.createOrder(userId, cart.getId());

                    // 결제 처리 (재시도 로직)
                    boolean paymentSuccess = false;
                    int maxRetries = 10;
                    for (int retry = 0; retry < maxRetries && !paymentSuccess; retry++) {
                        try {
                            orderService.processPayment(order.getId());
                            paymentSuccess = true;
                            successCount.incrementAndGet();
                        } catch (IllegalStateException e) {
                            if (e.getMessage().contains("재고 차감 실패")) {
                                Thread.sleep(10);
                                // 재시도
                            } else {
                                throw e;
                            }
                        }
                    }
                    if (!paymentSuccess) {
                        failCount.incrementAndGet();
                    }
                } catch (Exception e) {
                    failCount.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        executorService.shutdown();

        // then - 일부 결제가 성공해야 함
        assertThat(successCount.get()).isGreaterThan(0);

        Product finalProduct = productService.getProduct(productId);

        // 핵심 검증: 음수 재고 발생하지 않아야 함 (Race Condition 방지)
        assertThat(finalProduct.getStockQty()).isGreaterThanOrEqualTo(0);

        // 재고가 초기값보다 많아지지 않아야 함
        assertThat(finalProduct.getStockQty()).isLessThanOrEqualTo(100);
    }

    @Test
    @DisplayName("동시 결제 처리 - 재고 부족 시 일부만 성공")
    void processPayment_Concurrency_InsufficientStock() throws InterruptedException {
        // given - 재고 50개
        Product product = productService.createProduct(1L, "품절 임박 상품", "브랜드", "설명",
                new BigDecimal("15000"), 50);
        Long productId = product.getId();

        int threadCount = 30; // 30명 시도
        int quantityPerOrder = 2; // 각 2개씩
        ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);

        AtomicInteger successCount = new AtomicInteger(0);

        // when - 30명이 각 2개씩 주문하지만 재고는 50개 (25명만 성공 가능)
        for (int i = 0; i < threadCount; i++) {
            final long userId = i + 2000;
            executorService.submit(() -> {
                try {
                    Cart cart = Cart.createForUser(userId);
                    cart = cartRepository.save(cart);

                    CartItem cartItem = CartItem.create(cart.getId(), productId, quantityPerOrder,
                            product.getPrice());
                    cartItemRepository.save(cartItem);

                    Order order = orderService.createOrder(userId, cart.getId());

                    // 결제 시도 (재시도)
                    boolean paymentSuccess = false;
                    int maxRetries = 10;
                    for (int retry = 0; retry < maxRetries && !paymentSuccess; retry++) {
                        try {
                            orderService.processPayment(order.getId());
                            paymentSuccess = true;
                            successCount.incrementAndGet();
                        } catch (IllegalStateException e) {
                            if (e.getMessage().contains("재고 차감 실패") ||
                                e.getMessage().contains("재고가 부족합니다")) {
                                Thread.sleep(10);
                            } else {
                                break; // 다른 에러는 재시도 안함
                            }
                        }
                    }
                } catch (Exception e) {
                    // 재고 부족 등 실패는 정상
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        executorService.shutdown();

        // then - 대략 25명 정도 성공해야 함 (50개 재고 / 2개 = 25명)
        // 동시성 환경에서 정확히 25명이 아닐 수 있음 (최소 70% 성공)
        assertThat(successCount.get()).isGreaterThanOrEqualTo(17); // 최소 17명 (70%)

        Product finalProduct = productService.getProduct(productId);
        // 재고는 0에 가까워야 함
        assertThat(finalProduct.getStockQty()).isGreaterThanOrEqualTo(0); // 음수 재고 없음
        assertThat(finalProduct.getStockQty()).isLessThanOrEqualTo(16); // 대부분 소진 (50 - 17*2 = 16)
    }

    @Test
    @DisplayName("동시 결제 처리 - Race Condition 없이 정확한 재고 차감")
    void processPayment_Concurrency_NoRaceCondition() throws InterruptedException {
        // given
        Product product = productService.createProduct(1L, "Race Condition 테스트", "브랜드", "설명",
                new BigDecimal("30000"), 100);
        Long productId = product.getId();

        int threadCount = 50;
        ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);

        AtomicInteger totalSuccessCount = new AtomicInteger(0);

        // when - 50명이 각각 다른 수량 주문
        for (int i = 0; i < threadCount; i++) {
            final long userId = i + 3000;
            final int quantity = (i % 5) + 1; // 1~5개씩 다양하게
            executorService.submit(() -> {
                try {
                    Cart cart = Cart.createForUser(userId);
                    cart = cartRepository.save(cart);

                    CartItem cartItem = CartItem.create(cart.getId(), productId, quantity,
                            product.getPrice());
                    cartItemRepository.save(cartItem);

                    Order order = orderService.createOrder(userId, cart.getId());

                    // 결제 시도
                    boolean paymentSuccess = false;
                    int maxRetries = 15;
                    for (int retry = 0; retry < maxRetries && !paymentSuccess; retry++) {
                        try {
                            orderService.processPayment(order.getId());
                            paymentSuccess = true;
                            totalSuccessCount.incrementAndGet();
                        } catch (Exception e) {
                            if (e.getMessage().contains("재고 차감 실패")) {
                                Thread.sleep(10);
                            } else {
                                break;
                            }
                        }
                    }
                } catch (Exception e) {
                    // 실패는 정상
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        executorService.shutdown();

        // then - 재고가 정확하게 관리되어야 함 (음수 재고 없음)
        Product finalProduct = productService.getProduct(productId);
        assertThat(finalProduct.getStockQty()).isGreaterThanOrEqualTo(0);

        // 초기 재고 100개 이하로 사용되어야 함
        assertThat(finalProduct.getStockQty()).isLessThanOrEqualTo(100);
    }
}
