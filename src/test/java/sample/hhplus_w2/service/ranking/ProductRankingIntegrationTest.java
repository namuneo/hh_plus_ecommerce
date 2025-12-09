package sample.hhplus_w2.service.ranking;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import sample.hhplus_w2.service.ranking.ProductRankingService.ProductRanking;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ProductRankingService 통합 테스트
 *
 * Redis TestContainer 기반
 * - Sorted Set 기본 동작 검증
 * - 동시성 환경에서 Atomicity 검증
 * - 랭킹 조회 정확성 검증
 */
@SpringBootTest
@Testcontainers
class ProductRankingIntegrationTest {

    @Container
    static GenericContainer<?> redis = new GenericContainer<>(
            DockerImageName.parse("redis:7.2-alpine")
    ).withExposedPorts(6379);

    @DynamicPropertySource
    static void registerRedisProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
    }

    @Autowired
    private ProductRankingService rankingService;

    @BeforeEach
    void setUp() {
        // 각 테스트 전 랭킹 초기화
        rankingService.resetRanking();
    }

    @Test
    @DisplayName("상품 주문 수량 증가 - 기본 동작")
    void incrementProductOrder() {
        // given
        Long productId = 1L;

        // when
        rankingService.incrementProductOrder(productId, 5);
        rankingService.incrementProductOrder(productId, 3);

        // then
        Integer orderCount = rankingService.getProductOrderCount(productId);
        assertThat(orderCount).isEqualTo(8);
    }

    @Test
    @DisplayName("TOP N 상품 랭킹 조회 - 정확한 순위")
    void getTopProducts() {
        // given
        rankingService.incrementProductOrder(1L, 100);  // 1위
        rankingService.incrementProductOrder(2L, 50);   // 2위
        rankingService.incrementProductOrder(3L, 80);   // 3위
        rankingService.incrementProductOrder(4L, 60);   // 4위
        rankingService.incrementProductOrder(5L, 30);   // 5위

        // when
        List<ProductRanking> top3 = rankingService.getTopProducts(3);

        // then
        assertThat(top3).hasSize(3);
        assertThat(top3.get(0).getProductId()).isEqualTo(1L);
        assertThat(top3.get(0).getRank()).isEqualTo(1);
        assertThat(top3.get(0).getOrderCount()).isEqualTo(100);

        assertThat(top3.get(1).getProductId()).isEqualTo(3L);
        assertThat(top3.get(1).getRank()).isEqualTo(2);
        assertThat(top3.get(1).getOrderCount()).isEqualTo(80);

        assertThat(top3.get(2).getProductId()).isEqualTo(4L);
        assertThat(top3.get(2).getRank()).isEqualTo(3);
        assertThat(top3.get(2).getOrderCount()).isEqualTo(60);
    }

    @Test
    @DisplayName("특정 상품 랭킹 조회 - 순위와 주문 수량")
    void getProductRank() {
        // given
        rankingService.incrementProductOrder(1L, 100);
        rankingService.incrementProductOrder(2L, 50);
        rankingService.incrementProductOrder(3L, 80);

        // when
        Integer rank1 = rankingService.getProductRank(1L);
        Integer rank2 = rankingService.getProductRank(2L);
        Integer rank3 = rankingService.getProductRank(3L);
        Integer rankNotExist = rankingService.getProductRank(999L);

        // then
        assertThat(rank1).isEqualTo(1);
        assertThat(rank2).isEqualTo(3);
        assertThat(rank3).isEqualTo(2);
        assertThat(rankNotExist).isNull();
    }

    @Test
    @DisplayName("동시성 테스트 - 100명이 동시에 주문 시 정확한 집계")
    void concurrentIncrement() throws InterruptedException {
        // given
        Long productId = 1L;
        int threadCount = 100;
        int quantityPerOrder = 1;

        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);

        // when
        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    rankingService.incrementProductOrder(productId, quantityPerOrder);
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        executor.shutdown();

        // then
        Integer totalOrderCount = rankingService.getProductOrderCount(productId);
        assertThat(totalOrderCount).isEqualTo(threadCount * quantityPerOrder);
    }

    @Test
    @DisplayName("동시성 테스트 - 여러 상품 동시 주문 시 랭킹 정확성")
    void concurrentMultipleProducts() throws InterruptedException {
        // given
        int threadCount = 50;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);

        AtomicInteger product1Orders = new AtomicInteger(0);
        AtomicInteger product2Orders = new AtomicInteger(0);
        AtomicInteger product3Orders = new AtomicInteger(0);

        // when: 상품 1, 2, 3에 무작위로 주문
        for (int i = 0; i < threadCount; i++) {
            final int index = i;
            executor.submit(() -> {
                try {
                    if (index % 3 == 0) {
                        rankingService.incrementProductOrder(1L, 1);
                        product1Orders.incrementAndGet();
                    } else if (index % 3 == 1) {
                        rankingService.incrementProductOrder(2L, 2);
                        product2Orders.incrementAndGet();
                    } else {
                        rankingService.incrementProductOrder(3L, 3);
                        product3Orders.incrementAndGet();
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        executor.shutdown();

        // then: 각 상품의 주문 수량 정확성 검증
        Integer count1 = rankingService.getProductOrderCount(1L);
        Integer count2 = rankingService.getProductOrderCount(2L);
        Integer count3 = rankingService.getProductOrderCount(3L);

        assertThat(count1).isEqualTo(product1Orders.get() * 1);
        assertThat(count2).isEqualTo(product2Orders.get() * 2);
        assertThat(count3).isEqualTo(product3Orders.get() * 3);

        // 랭킹 순서 검증
        List<ProductRanking> topProducts = rankingService.getTopProducts(3);
        assertThat(topProducts).hasSize(3);

        // 상품 3이 1위 (3 * 17 = 51)
        assertThat(topProducts.get(0).getProductId()).isEqualTo(3L);
        // 상품 2가 2위 (2 * 17 = 34)
        assertThat(topProducts.get(1).getProductId()).isEqualTo(2L);
        // 상품 1이 3위 (1 * 16 = 16)
        assertThat(topProducts.get(2).getProductId()).isEqualTo(1L);
    }

    @Test
    @DisplayName("빈 랭킹 조회 - 주문이 없을 때")
    void getTopProductsEmpty() {
        // when
        List<ProductRanking> topProducts = rankingService.getTopProducts(10);

        // then
        assertThat(topProducts).isEmpty();
    }

    @Test
    @DisplayName("랭킹 초기화 후 재수집")
    void resetAndRecount() {
        // given
        rankingService.incrementProductOrder(1L, 100);
        rankingService.incrementProductOrder(2L, 50);

        // when
        rankingService.resetRanking();

        // then
        Integer count1 = rankingService.getProductOrderCount(1L);
        Integer count2 = rankingService.getProductOrderCount(2L);
        assertThat(count1).isEqualTo(0);
        assertThat(count2).isEqualTo(0);

        List<ProductRanking> topProducts = rankingService.getTopProducts(10);
        assertThat(topProducts).isEmpty();
    }

    @Test
    @DisplayName("대용량 랭킹 처리 - 1000개 상품")
    void largeScaleRanking() {
        // given
        for (long i = 1; i <= 1000; i++) {
            rankingService.incrementProductOrder(i, (int) (1000 - i + 1));
        }

        // when
        List<ProductRanking> top10 = rankingService.getTopProducts(10);

        // then
        assertThat(top10).hasSize(10);
        assertThat(top10.get(0).getProductId()).isEqualTo(1L);
        assertThat(top10.get(0).getOrderCount()).isEqualTo(1000);
        assertThat(top10.get(9).getProductId()).isEqualTo(10L);
        assertThat(top10.get(9).getOrderCount()).isEqualTo(991);
    }
}
