package sample.hhplus_w2.domain.coupon;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.*;

class CouponTest {

    @Test
    @DisplayName("쿠폰 생성 - 정액 할인")
    void createCoupon_Fixed() {
        // given
        String code = "FIXED1000";
        CouponType type = CouponType.FIXED;
        BigDecimal amount = new BigDecimal("1000");
        Integer totalIssuable = 100;
        Integer perUserLimit = 1;
        LocalDateTime validFrom = LocalDateTime.now().minusDays(1);
        LocalDateTime validTo = LocalDateTime.now().plusDays(30);
        BigDecimal minOrderAmount = new BigDecimal("10000");

        // when
        Coupon coupon = Coupon.create(code, type, amount, totalIssuable, perUserLimit,
                validFrom, validTo, minOrderAmount);

        // then
        assertThat(coupon).isNotNull();
        assertThat(coupon.getCode()).isEqualTo(code);
        assertThat(coupon.getType()).isEqualTo(type);
        assertThat(coupon.getAmount()).isEqualTo(amount);
        assertThat(coupon.getTotalIssuable()).isEqualTo(totalIssuable);
        assertThat(coupon.getIssued()).isEqualTo(0);
        assertThat(coupon.getStatus()).isEqualTo(CouponStatus.DRAFT);
    }

    @Test
    @DisplayName("쿠폰 생성 - 정률 할인")
    void createCoupon_Percentage() {
        // given
        String code = "PERCENT10";
        CouponType type = CouponType.PERCENTAGE;
        BigDecimal amount = new BigDecimal("10"); // 10%

        // when
        Coupon coupon = Coupon.create(code, type, amount, 100, 1,
                null, null, BigDecimal.ZERO);

        // then
        assertThat(coupon).isNotNull();
        assertThat(coupon.getType()).isEqualTo(CouponType.PERCENTAGE);
        assertThat(coupon.getAmount()).isEqualTo(amount);
    }

    @Test
    @DisplayName("쿠폰 생성 - 잘못된 할인 금액으로 예외 발생")
    void createCoupon_InvalidAmount() {
        // when & then
        assertThatThrownBy(() ->
                Coupon.create("TEST", CouponType.FIXED, BigDecimal.ZERO, 100, 1,
                        null, null, null)
        ).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("할인 금액/율은 0보다 커야 합니다");
    }

    @Test
    @DisplayName("쿠폰 생성 - 잘못된 발급 수량으로 예외 발생")
    void createCoupon_InvalidTotalIssuable() {
        // when & then
        assertThatThrownBy(() ->
                Coupon.create("TEST", CouponType.FIXED, new BigDecimal("1000"), 0, 1,
                        null, null, null)
        ).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("발급 수량은 1 이상이어야 합니다");
    }

    @Test
    @DisplayName("쿠폰 발급 - 정상")
    void issueCoupon_Success() {
        // given
        Coupon coupon = Coupon.create("TEST", CouponType.FIXED, new BigDecimal("1000"),
                10, 1, null, null, null);
        coupon.publish(); // 발급 시작

        // when
        boolean result = coupon.issue();

        // then
        assertThat(result).isTrue();
        assertThat(coupon.getIssued()).isEqualTo(1);
    }

    @Test
    @DisplayName("쿠폰 발급 - 수량 소진")
    void issueCoupon_SoldOut() {
        // given
        Coupon coupon = Coupon.create("TEST", CouponType.FIXED, new BigDecimal("1000"),
                2, 1, null, null, null);
        coupon.publish();
        coupon.issue();
        coupon.issue();

        // when
        boolean result = coupon.issue();

        // then
        assertThat(result).isFalse();
        assertThat(coupon.getIssued()).isEqualTo(2);
    }

    @Test
    @DisplayName("쿠폰 발급 - DRAFT 상태에서 예외 발생")
    void issueCoupon_NotPublished() {
        // given
        Coupon coupon = Coupon.create("TEST", CouponType.FIXED, new BigDecimal("1000"),
                10, 1, null, null, null);
        // publish 하지 않음 (DRAFT 상태)

        // when & then
        assertThatThrownBy(() -> coupon.issue())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("발급 가능한 상태가 아닙니다");
    }

    @Test
    @DisplayName("발급 가능 여부 - 정상")
    void canIssue_True() {
        // given
        Coupon coupon = Coupon.create("TEST", CouponType.FIXED, new BigDecimal("1000"),
                10, 1, null, null, null);
        coupon.publish();

        // when & then
        assertThat(coupon.canIssue()).isTrue();
    }

    @Test
    @DisplayName("발급 가능 여부 - DRAFT 상태")
    void canIssue_Draft() {
        // given
        Coupon coupon = Coupon.create("TEST", CouponType.FIXED, new BigDecimal("1000"),
                10, 1, null, null, null);

        // when & then
        assertThat(coupon.canIssue()).isFalse();
    }

    @Test
    @DisplayName("유효 기간 확인 - 유효함")
    void isValidPeriod_Valid() {
        // given
        LocalDateTime validFrom = LocalDateTime.now().minusDays(1);
        LocalDateTime validTo = LocalDateTime.now().plusDays(1);
        Coupon coupon = Coupon.create("TEST", CouponType.FIXED, new BigDecimal("1000"),
                10, 1, validFrom, validTo, null);

        // when & then
        assertThat(coupon.isValidPeriod()).isTrue();
    }

    @Test
    @DisplayName("유효 기간 확인 - 만료됨")
    void isValidPeriod_Expired() {
        // given
        LocalDateTime validFrom = LocalDateTime.now().minusDays(10);
        LocalDateTime validTo = LocalDateTime.now().minusDays(1);
        Coupon coupon = Coupon.create("TEST", CouponType.FIXED, new BigDecimal("1000"),
                10, 1, validFrom, validTo, null);

        // when & then
        assertThat(coupon.isValidPeriod()).isFalse();
    }

    @Test
    @DisplayName("쿠폰 상태 변경 - DRAFT → PUBLISHED")
    void publish() {
        // given
        Coupon coupon = Coupon.create("TEST", CouponType.FIXED, new BigDecimal("1000"),
                10, 1, null, null, null);

        // when
        coupon.publish();

        // then
        assertThat(coupon.getStatus()).isEqualTo(CouponStatus.PUBLISHED);
    }

    @Test
    @DisplayName("쿠폰 상태 변경 - PUBLISHED → PAUSED")
    void pause() {
        // given
        Coupon coupon = Coupon.create("TEST", CouponType.FIXED, new BigDecimal("1000"),
                10, 1, null, null, null);
        coupon.publish();

        // when
        coupon.pause();

        // then
        assertThat(coupon.getStatus()).isEqualTo(CouponStatus.PAUSED);
    }

    @Test
    @DisplayName("쿠폰 상태 변경 - EXPIRED")
    void expire() {
        // given
        Coupon coupon = Coupon.create("TEST", CouponType.FIXED, new BigDecimal("1000"),
                10, 1, null, null, null);

        // when
        coupon.expire();

        // then
        assertThat(coupon.getStatus()).isEqualTo(CouponStatus.EXPIRED);
    }

    @Test
    @DisplayName("할인 금액 계산 - 정액 할인")
    void calculateDiscount_Fixed() {
        // given
        BigDecimal discountAmount = new BigDecimal("5000");
        Coupon coupon = Coupon.create("FIXED5000", CouponType.FIXED, discountAmount,
                10, 1, null, null, new BigDecimal("10000"));
        BigDecimal orderAmount = new BigDecimal("50000");

        // when
        BigDecimal discount = coupon.calculateDiscount(orderAmount);

        // then
        assertThat(discount).isEqualTo(new BigDecimal("5000"));
    }

    @Test
    @DisplayName("할인 금액 계산 - 정률 할인")
    void calculateDiscount_Percentage() {
        // given
        BigDecimal discountRate = new BigDecimal("10"); // 10%
        Coupon coupon = Coupon.create("PERCENT10", CouponType.PERCENTAGE, discountRate,
                10, 1, null, null, BigDecimal.ZERO);
        BigDecimal orderAmount = new BigDecimal("50000");

        // when
        BigDecimal discount = coupon.calculateDiscount(orderAmount);

        // then
        assertThat(discount).isEqualTo(new BigDecimal("5000.00"));
    }

    @Test
    @DisplayName("할인 금액 계산 - 최소 주문 금액 미충족으로 예외 발생")
    void calculateDiscount_BelowMinOrderAmount() {
        // given
        Coupon coupon = Coupon.create("TEST", CouponType.FIXED, new BigDecimal("1000"),
                10, 1, null, null, new BigDecimal("10000"));
        BigDecimal orderAmount = new BigDecimal("5000");

        // when & then
        assertThatThrownBy(() -> coupon.calculateDiscount(orderAmount))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("최소 주문 금액을 충족하지 않습니다");
    }
}
