package sample.hhplus_w2.service.coupon;

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
import sample.hhplus_w2.domain.coupon.Coupon;
import sample.hhplus_w2.domain.coupon.CouponType;
import sample.hhplus_w2.repository.coupon.CouponRepository;
import sample.hhplus_w2.repository.coupon.CouponUserRepository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 쿠폰 발급 분산락 통합 테스트
 * - TestContainer 기반 Redis 환경
 * - 선착순 쿠폰 동시성 제어 검증
 */
@SpringBootTest
@Testcontainers
class CouponServiceDistributedLockTest {

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
    private CouponService couponService;

    @Autowired
    private CouponRepository couponRepository;

    @Autowired
    private CouponUserRepository couponUserRepository;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    private Coupon testCoupon;

    @BeforeEach
    void setUp() {
        // 선착순 10개 쿠폰 생성
        testCoupon = Coupon.create(
            "TEST-COUPON-001",
            CouponType.PERCENTAGE,
            BigDecimal.valueOf(10),
            10, // 선착순 10개
            1, // 1인당 1개
            LocalDateTime.now(),
            LocalDateTime.now().plusDays(30),
            BigDecimal.ZERO
        );
        testCoupon.publish();
        testCoupon = couponRepository.save(testCoupon);
    }

    @AfterEach
    void tearDown() {
        couponUserRepository.deleteAll();
        couponRepository.deleteAll();
        redisTemplate.getConnectionFactory().getConnection().serverCommands().flushAll();
    }

    @Test
    @DisplayName("분산락: 선착순 10개 쿠폰, 50명 동시 요청 - 정확히 10명만 발급")
    void distributedLock_FirstCome_ExactlyTen() throws InterruptedException {
        // given
        int threadCount = 50;
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);
        CountDownLatch latch = new CountDownLatch(threadCount);
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);

        // when
        for (int i = 0; i < threadCount; i++) {
            final long userId = i + 1;
            executor.submit(() -> {
                try {
                    couponService.issueCouponWithDistributedLock(testCoupon.getId(), userId);
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
        Coupon result = couponService.getCoupon(testCoupon.getId());
        assertThat(result.getIssued()).isEqualTo(10); // 정확히 10개 발급
        assertThat(successCount.get()).isEqualTo(10);
        assertThat(failCount.get()).isEqualTo(40);
    }

    @Test
    @DisplayName("분산락: 선착순 50개 쿠폰, 50명 요청 - 모두 성공")
    void distributedLock_AllSuccess() throws InterruptedException {
        // given
        // 50개 쿠폰 생성
        Coupon largeCoupon = Coupon.create(
            "TEST-COUPON-002",
            CouponType.PERCENTAGE,
            BigDecimal.valueOf(10),
            50,
            1,
            LocalDateTime.now(),
            LocalDateTime.now().plusDays(30),
            BigDecimal.ZERO
        );
        largeCoupon.publish();
        largeCoupon = couponRepository.save(largeCoupon);

        int threadCount = 50;
        AtomicInteger successCount = new AtomicInteger(0);
        CountDownLatch latch = new CountDownLatch(threadCount);
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);

        // when
        Long couponId = largeCoupon.getId();
        for (int i = 0; i < threadCount; i++) {
            final long userId = i + 1;
            executor.submit(() -> {
                try {
                    couponService.issueCouponWithDistributedLock(couponId, userId);
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    // 실패
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        executor.shutdown();

        // then
        Coupon result = couponService.getCoupon(couponId);
        assertThat(result.getIssued()).isEqualTo(50);
        assertThat(successCount.get()).isEqualTo(threadCount);
    }

    @Test
    @DisplayName("분산락: Race Condition 방지 - 초과 발급 없음")
    void distributedLock_PreventOverIssuance() throws InterruptedException {
        // given
        int threadCount = 100; // 10개 쿠폰에 100명 요청
        AtomicInteger successCount = new AtomicInteger(0);
        CountDownLatch latch = new CountDownLatch(threadCount);
        ExecutorService executor = Executors.newFixedThreadPool(50);

        // when
        for (int i = 0; i < threadCount; i++) {
            final long userId = i + 1;
            executor.submit(() -> {
                try {
                    couponService.issueCouponWithDistributedLock(testCoupon.getId(), userId);
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    // 실패
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        executor.shutdown();

        // then
        Coupon result = couponService.getCoupon(testCoupon.getId());
        long issuedCount = couponUserRepository.findByCouponId(testCoupon.getId()).size();

        assertThat(result.getIssued()).isEqualTo(10); // 정확히 10개
        assertThat(issuedCount).isEqualTo(10); // CouponUser도 10개
        assertThat(successCount.get()).isEqualTo(10);
    }

    @Test
    @DisplayName("분산락: 중복 발급 방지")
    void distributedLock_PreventDuplicateIssuance() throws InterruptedException {
        // given
        long userId = 1L;
        int attemptCount = 5; // 같은 유저가 5번 시도
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);
        CountDownLatch latch = new CountDownLatch(attemptCount);
        ExecutorService executor = Executors.newFixedThreadPool(attemptCount);

        // when
        for (int i = 0; i < attemptCount; i++) {
            executor.submit(() -> {
                try {
                    couponService.issueCouponWithDistributedLock(testCoupon.getId(), userId);
                    successCount.incrementAndGet();
                } catch (IllegalStateException e) {
                    if (e.getMessage().contains("이미 발급받은 쿠폰입니다")) {
                        failCount.incrementAndGet();
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        executor.shutdown();

        // then
        long issuedCount = couponUserRepository.findByCouponId(testCoupon.getId()).size();
        assertThat(issuedCount).isEqualTo(1); // 1개만 발급
        assertThat(successCount.get()).isEqualTo(1);
        assertThat(failCount.get()).isEqualTo(4);
    }

    @Test
    @DisplayName("분산락: 높은 동시성 (200명 요청)")
    void distributedLock_HighConcurrency() throws InterruptedException {
        // given
        // 100개 쿠폰 생성
        Coupon largeCoupon = Coupon.create(
            "TEST-COUPON-003",
            CouponType.PERCENTAGE,
            BigDecimal.valueOf(10),
            100,
            1,
            LocalDateTime.now(),
            LocalDateTime.now().plusDays(30),
            BigDecimal.ZERO
        );
        largeCoupon.publish();
        largeCoupon = couponRepository.save(largeCoupon);

        int threadCount = 200;
        AtomicInteger successCount = new AtomicInteger(0);
        CountDownLatch latch = new CountDownLatch(threadCount);
        ExecutorService executor = Executors.newFixedThreadPool(100);

        // when
        Long couponId = largeCoupon.getId();
        for (int i = 0; i < threadCount; i++) {
            final long userId = i + 1;
            executor.submit(() -> {
                try {
                    couponService.issueCouponWithDistributedLock(couponId, userId);
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    // 실패
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        executor.shutdown();

        // then
        Coupon result = couponService.getCoupon(couponId);
        assertThat(result.getIssued()).isEqualTo(100); // 정확히 100개
        assertThat(successCount.get()).isEqualTo(100);
    }
}
