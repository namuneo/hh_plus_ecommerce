package sample.hhplus_w2.service.product;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import sample.hhplus_w2.domain.product.Product;
import sample.hhplus_w2.repository.product.ProductRepository;

import java.math.BigDecimal;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 재고 차감 분산락 통합 테스트
 * - TestContainer 기반 Redis 환경
 * - 동시성 제어 검증
 */
@SpringBootTest
@Testcontainers
class ProductServiceDistributedLockTest {

    @Container
    static GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:7.2-alpine"))
        .withExposedPorts(6379)
        .withReuse(true);

    @DynamicPropertySource
    static void registerRedisProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
    }

    @Autowired
    private ProductService productService;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    private Product testProduct;

    @BeforeEach
    void setUp() {
        // 테스트용 상품 생성
        testProduct = productService.createProduct(
            1L,
            "테스트 상품",
            "테스트 브랜드",
            "테스트 설명",
            BigDecimal.valueOf(10000),
            100
        );
    }

    @AfterEach
    void tearDown() {
        productRepository.deleteAll();
        redisTemplate.getConnectionFactory().getConnection().serverCommands().flushAll();
    }

    @Test
    @DisplayName("분산락: 동시에 50명이 각각 2개씩 구매 - 재고 정확성")
    void distributedLock_ConcurrentStockDecrease_Accuracy() throws InterruptedException {
        // given
        int threadCount = 50;
        int quantityPerRequest = 2;
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);
        CountDownLatch latch = new CountDownLatch(threadCount);
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);

        // when
        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    productService.decreaseStockWithDistributedLock(testProduct.getId(), quantityPerRequest);
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    failCount.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        executor.shutdown();

        // then
        Product result = productService.getProduct(testProduct.getId());
        assertThat(result.getStockQty()).isEqualTo(0); // 100 - (50 * 2) = 0
        assertThat(successCount.get()).isEqualTo(threadCount);
        assertThat(failCount.get()).isZero();
    }

    @Test
    @DisplayName("분산락: 재고 부족 시 예외 발생")
    void distributedLock_StockShortage_Exception() throws InterruptedException {
        // given
        int threadCount = 60; // 100개 재고, 60명이 2개씩 요청 (초과)
        int quantityPerRequest = 2;
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);
        CountDownLatch latch = new CountDownLatch(threadCount);
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);

        // when
        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    productService.decreaseStockWithDistributedLock(testProduct.getId(), quantityPerRequest);
                    successCount.incrementAndGet();
                } catch (IllegalStateException e) {
                    if (e.getMessage().contains("재고가 부족합니다")) {
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
        executor.shutdown();

        // then
        Product result = productService.getProduct(testProduct.getId());
        assertThat(result.getStockQty()).isGreaterThanOrEqualTo(0); // 음수 재고 없음
        assertThat(successCount.get()).isEqualTo(50); // 100 / 2 = 50명 성공
        assertThat(failCount.get()).isEqualTo(10); // 10명 실패
    }

    @Test
    @DisplayName("분산락: 100개 재고, 100명이 1개씩 구매 - 정확히 0개 남음")
    void distributedLock_ExactStockDecrease() throws InterruptedException {
        // given
        int threadCount = 100;
        int quantityPerRequest = 1;
        AtomicInteger successCount = new AtomicInteger(0);
        CountDownLatch latch = new CountDownLatch(threadCount);
        ExecutorService executor = Executors.newFixedThreadPool(50);

        // when
        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    productService.decreaseStockWithDistributedLock(testProduct.getId(), quantityPerRequest);
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    // 재고 부족
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        executor.shutdown();

        // then
        Product result = productService.getProduct(testProduct.getId());
        assertThat(result.getStockQty()).isZero();
        assertThat(successCount.get()).isEqualTo(threadCount);
    }

    @Test
    @DisplayName("분산락: 순차 처리로 Race Condition 방지")
    void distributedLock_PreventRaceCondition() throws InterruptedException {
        // given
        int threadCount = 10;
        int quantityPerRequest = 5;
        CountDownLatch latch = new CountDownLatch(threadCount);
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);

        // when
        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    productService.decreaseStockWithDistributedLock(testProduct.getId(), quantityPerRequest);
                } catch (Exception e) {
                    // 예외 무시
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        executor.shutdown();

        // then
        Product result = productService.getProduct(testProduct.getId());
        assertThat(result.getStockQty()).isEqualTo(50); // 100 - (10 * 5) = 50
    }

    @Test
    @DisplayName("분산락: 높은 동시성 (200개 요청)")
    void distributedLock_HighConcurrency() throws InterruptedException {
        // given
        // 재고 1000개로 증가
        productService.increaseStock(testProduct.getId(), 900);

        int threadCount = 200;
        int quantityPerRequest = 5;
        AtomicInteger successCount = new AtomicInteger(0);
        CountDownLatch latch = new CountDownLatch(threadCount);
        ExecutorService executor = Executors.newFixedThreadPool(100);

        // when
        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    productService.decreaseStockWithDistributedLock(testProduct.getId(), quantityPerRequest);
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    // 재고 부족
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        executor.shutdown();

        // then
        Product result = productService.getProduct(testProduct.getId());
        assertThat(result.getStockQty()).isZero(); // 1000 - (200 * 5) = 0
        assertThat(successCount.get()).isEqualTo(threadCount);
    }
}
