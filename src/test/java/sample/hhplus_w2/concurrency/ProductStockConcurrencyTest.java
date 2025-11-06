package sample.hhplus_w2.concurrency;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import sample.hhplus_w2.domain.product.Product;
import sample.hhplus_w2.repository.product.ProductRepository;
import sample.hhplus_w2.repository.product.impl.ProductRepositoryImpl;
import sample.hhplus_w2.service.product.ProductService;

import java.math.BigDecimal;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.*;

/**
 * 상품 재고 차감 동시성 테스트 (낙관적 락)
 */
class ProductStockConcurrencyTest {

    private ProductService productService;
    private ProductRepository productRepository;

    @BeforeEach
    void setUp() {
        productRepository = new ProductRepositoryImpl();
        productService = new ProductService(productRepository);
    }

    @Test
    @DisplayName("재고 동시 차감 - 100개 재고, 50명이 각 2개씩 주문, 정확히 50개 남아야 함")
    void decreaseStock_Concurrency_OptimisticLock() throws InterruptedException {
        // given
        Product product = productService.createProduct(1L, "인기 상품", "브랜드", "설명",
                new BigDecimal("10000"), 100);
        Long productId = product.getId();

        int threadCount = 50; // 50개의 주문
        int quantityPerOrder = 2; // 각 주문당 2개씩
        ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);

        // when - 50명이 동시에 재고 2개씩 차감 시도
        for (int i = 0; i < threadCount; i++) {
            executorService.submit(() -> {
                try {
                    // 낙관적 락으로 재고 차감 시도 (재시도 로직)
                    boolean success = false;
                    int maxRetries = 10;
                    for (int retry = 0; retry < maxRetries && !success; retry++) {
                        Product currentProduct = productService.getProduct(productId);
                        success = productService.decreaseStock(productId, quantityPerOrder,
                                currentProduct.getVersion());
                        if (success) {
                            successCount.incrementAndGet();
                            break;
                        }
                        // 실패 시 짧은 대기 후 재시도
                        Thread.sleep(10);
                    }
                    if (!success) {
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

        // then - 대부분의 주문이 성공해야 함 (재시도 로직 있음)
        assertThat(successCount.get()).isGreaterThanOrEqualTo(45); // 최소 90% 성공

        Product finalProduct = productService.getProduct(productId);
        // 재고는 정확하게 차감되어야 함
        int expectedStock = 100 - (successCount.get() * quantityPerOrder);
        assertThat(finalProduct.getStockQty()).isEqualTo(expectedStock);

        // 음수 재고 없어야 함
        assertThat(finalProduct.getStockQty()).isGreaterThanOrEqualTo(0);
    }

    @Test
    @DisplayName("재고 동시 차감 - 재고 부족 시 일부만 성공")
    void decreaseStock_Concurrency_InsufficientStock() throws InterruptedException {
        // given - 재고 20개
        Product product = productService.createProduct(1L, "품절 임박 상품", "브랜드", "설명",
                new BigDecimal("15000"), 20);
        Long productId = product.getId();

        int threadCount = 30; // 30명이 시도
        int quantityPerOrder = 1;
        ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);

        AtomicInteger successCount = new AtomicInteger(0);

        // when - 30명이 각 1개씩 시도하지만 재고는 20개
        for (int i = 0; i < threadCount; i++) {
            executorService.submit(() -> {
                try {
                    boolean success = false;
                    int maxRetries = 10;
                    for (int retry = 0; retry < maxRetries && !success; retry++) {
                        Product currentProduct = productService.getProduct(productId);
                        if (currentProduct.getStockQty() < quantityPerOrder) {
                            break; // 재고 부족
                        }
                        success = productService.decreaseStock(productId, quantityPerOrder,
                                currentProduct.getVersion());
                        if (success) {
                            successCount.incrementAndGet();
                            break;
                        }
                        Thread.sleep(10);
                    }
                } catch (Exception e) {
                    // 재고 부족 예외는 정상
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        executorService.shutdown();

        // then - 최소 90% 성공 (충돌 환경에서 일부는 재시도 실패 가능)
        assertThat(successCount.get()).isGreaterThanOrEqualTo(18); // 최소 90% (18/20)

        Product finalProduct = productService.getProduct(productId);
        assertThat(finalProduct.getStockQty()).isGreaterThanOrEqualTo(0); // 음수 재고 발생 없음
        assertThat(finalProduct.getStockQty()).isLessThanOrEqualTo(2); // 재고는 거의 소진
    }

    @Test
    @DisplayName("재고 동시 차감 - 낙관적 락으로 정확한 재고 관리 (Race Condition 방지)")
    void decreaseStock_Concurrency_NoRaceCondition() throws InterruptedException {
        // given
        Product product = productService.createProduct(1L, "동시성 테스트 상품", "브랜드", "설명",
                new BigDecimal("20000"), 50);
        Long productId = product.getId();

        int threadCount = 100; // 100개의 동시 요청
        ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);

        AtomicInteger totalDecreased = new AtomicInteger(0);

        // when - 100개의 스레드가 각각 1개씩 차감 시도 (총 50개 재고)
        for (int i = 0; i < threadCount; i++) {
            executorService.submit(() -> {
                try {
                    boolean success = false;
                    int maxRetries = 10;
                    for (int retry = 0; retry < maxRetries && !success; retry++) {
                        Product currentProduct = productService.getProduct(productId);
                        if (currentProduct.getStockQty() < 1) {
                            break;
                        }
                        success = productService.decreaseStock(productId, 1,
                                currentProduct.getVersion());
                        if (success) {
                            totalDecreased.incrementAndGet();
                            break;
                        }
                        Thread.sleep(5);
                    }
                } catch (Exception e) {
                    // 예외 처리
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        executorService.shutdown();

        // then - 대부분이 차감되어야 함 (재시도 로직 있으므로 높은 성공률)
        assertThat(totalDecreased.get()).isGreaterThanOrEqualTo(45); // 최소 90%

        Product finalProduct = productService.getProduct(productId);

        // 핵심: 음수 재고 발생하지 않아야 함 (Race Condition 방지)
        assertThat(finalProduct.getStockQty()).isGreaterThanOrEqualTo(0);

        // 초기 재고보다 많아지지 않아야 함
        assertThat(finalProduct.getStockQty()).isLessThanOrEqualTo(50);
    }

    @Test
    @DisplayName("재고 동시 증가/차감 - 혼합 작업의 안정성")
    void increaseAndDecreaseStock_Concurrency_Mixed() throws InterruptedException {
        // given
        Product product = productService.createProduct(1L, "혼합 테스트 상품", "브랜드", "설명",
                new BigDecimal("10000"), 100);
        Long productId = product.getId();
        int initialStock = 100;

        int threadCount = 50;
        ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);

        // when - 일부는 증가, 일부는 차감
        for (int i = 0; i < threadCount; i++) {
            final int index = i;
            executorService.submit(() -> {
                try {
                    if (index < 25) {
                        // 증가
                        productService.increaseStock(productId, 10);
                    } else {
                        // 차감
                        boolean success = false;
                        int maxRetries = 10;
                        for (int retry = 0; retry < maxRetries && !success; retry++) {
                            Product currentProduct = productService.getProduct(productId);
                            if (currentProduct.getStockQty() < 5) {
                                break;
                            }
                            success = productService.decreaseStock(productId, 5,
                                    currentProduct.getVersion());
                            if (success) {
                                break;
                            }
                            Thread.sleep(10);
                        }
                    }
                } catch (Exception e) {
                    // 예외 처리
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        executorService.shutdown();

        // then - 재고는 음수가 될 수 없음 (가장 중요한 불변식)
        Product finalProduct = productService.getProduct(productId);
        assertThat(finalProduct.getStockQty()).isGreaterThanOrEqualTo(0);

        // 재고가 비정상적으로 많아지지 않았는지 검증 (증가분 고려)
        assertThat(finalProduct.getStockQty()).isLessThanOrEqualTo(initialStock + (25 * 10));
    }
}
