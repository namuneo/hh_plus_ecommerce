package sample.hhplus_w2.concurrency;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import sample.hhplus_w2.domain.coupon.Coupon;
import sample.hhplus_w2.domain.coupon.CouponType;
import sample.hhplus_w2.repository.coupon.CouponRepository;
import sample.hhplus_w2.repository.coupon.CouponUserRepository;
import sample.hhplus_w2.service.coupon.CouponService;

import java.math.BigDecimal;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.*;

/**
 * 쿠폰 선착순 발급 동시성 테스트
 */
@SpringBootTest
@ActiveProfiles("test")
class CouponConcurrencyTest {

    @Autowired
    private CouponService couponService;

    @Autowired
    private CouponRepository couponRepository;

    @Autowired
    private CouponUserRepository couponUserRepository;

    @AfterEach
    void tearDown() {
        couponUserRepository.deleteAll();
        couponRepository.deleteAll();
    }

    @Test
    @DisplayName("선착순 쿠폰 발급 - 100명이 동시에 10개 쿠폰 발급 시도, 정확히 10명만 성공")
    void issueCoupon_Concurrency_FirstComeFirstServed() throws InterruptedException {
        // given - 총 10개만 발급 가능한 쿠폰 생성
        Coupon coupon = Coupon.create("CONCURRENT_TEST", CouponType.FIXED, new BigDecimal("1000"),
                10, 5, null, null, null);
        coupon = couponService.createCoupon(coupon);
        couponService.publishCoupon(coupon.getId());
        Long couponId = coupon.getId();

        int threadCount = 100; // 100명의 사용자
        ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);

        // when - 100명이 동시에 쿠폰 발급 시도
        for (int i = 0; i < threadCount; i++) {
            final long userId = i + 1;
            executorService.submit(() -> {
                try {
                    couponService.issueCoupon(couponId, userId);
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    failCount.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        executorService.shutdown();

        // then - 정확히 10명만 성공해야 함
        assertThat(successCount.get()).isEqualTo(10);
        assertThat(failCount.get()).isEqualTo(90);

        // 쿠폰 발급 수량 확인
        Coupon issuedCoupon = couponService.getCoupon(couponId);
        assertThat(issuedCoupon.getIssued()).isEqualTo(10);
    }

    @Test
    @DisplayName("선착순 쿠폰 발급 - 50명이 동시에 50개 쿠폰 발급 시도, 모두 성공")
    void issueCoupon_Concurrency_AllSuccess() throws InterruptedException {
        // given
        Coupon coupon = Coupon.create("ENOUGH_COUPON", CouponType.FIXED, new BigDecimal("2000"),
                50, 1, null, null, null);
        coupon = couponService.createCoupon(coupon);
        couponService.publishCoupon(coupon.getId());
        Long couponId = coupon.getId();

        int threadCount = 50;
        ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);

        AtomicInteger successCount = new AtomicInteger(0);

        // when
        for (int i = 0; i < threadCount; i++) {
            final long userId = i + 1;
            executorService.submit(() -> {
                try {
                    couponService.issueCoupon(couponId, userId);
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    // 예외 발생하면 안됨
                }
                latch.countDown();
            });
        }

        latch.await();
        executorService.shutdown();

        // then
        assertThat(successCount.get()).isEqualTo(50);
        Coupon issuedCoupon = couponService.getCoupon(couponId);
        assertThat(issuedCoupon.getIssued()).isEqualTo(50);
    }

    @Test
    @DisplayName("선착순 쿠폰 발급 - Race Condition이 발생하지 않아야 함 (정확성 검증)")
    void issueCoupon_Concurrency_NoRaceCondition() throws InterruptedException {
        // given - 총 30개 발급 가능
        Coupon coupon = Coupon.create("RACE_TEST", CouponType.PERCENTAGE, new BigDecimal("10"),
                30, 10, null, null, null);
        coupon = couponService.createCoupon(coupon);
        couponService.publishCoupon(coupon.getId());
        Long couponId = coupon.getId();

        int threadCount = 200; // 200명이 시도
        ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);

        AtomicInteger successCount = new AtomicInteger(0);

        // when
        for (int i = 0; i < threadCount; i++) {
            final long userId = i + 1;
            executorService.submit(() -> {
                try {
                    couponService.issueCoupon(couponId, userId);
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    // 실패는 정상
                }
                latch.countDown();
            });
        }

        latch.await();
        executorService.shutdown();

        // then - 정확히 30명만 성공해야 하며, 초과 발급 없어야 함
        assertThat(successCount.get()).isEqualTo(30);

        Coupon finalCoupon = couponService.getCoupon(couponId);
        assertThat(finalCoupon.getIssued()).isEqualTo(30);

        // Race Condition 발생하면 issued > totalIssuable 가능성이 있으므로 검증
        assertThat(finalCoupon.getIssued()).isLessThanOrEqualTo(finalCoupon.getTotalIssuable());
    }

    @Test
    @DisplayName("쿠폰 중복 발급 방지 - 동일 사용자가 여러 번 시도해도 1번만 성공")
    void issueCoupon_Concurrency_PreventDuplicate() throws InterruptedException {
        // given
        Coupon coupon = Coupon.create("DUPLICATE_TEST", CouponType.FIXED, new BigDecimal("5000"),
                100, 5, null, null, null);
        coupon = couponService.createCoupon(coupon);
        couponService.publishCoupon(coupon.getId());
        Long couponId = coupon.getId();

        int threadCount = 10; // 동일 사용자가 10번 시도
        Long sameUserId = 999L;
        ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);

        AtomicInteger successCount = new AtomicInteger(0);

        // when - 동일 사용자가 동시에 여러 번 발급 시도
        for (int i = 0; i < threadCount; i++) {
            executorService.submit(() -> {
                try {
                    couponService.issueCoupon(couponId, sameUserId);
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    // 중복 발급 예외는 정상
                }
                latch.countDown();
            });
        }

        latch.await();
        executorService.shutdown();

        // then - 정확히 1번만 성공해야 함
        assertThat(successCount.get()).isEqualTo(1);
    }
}
