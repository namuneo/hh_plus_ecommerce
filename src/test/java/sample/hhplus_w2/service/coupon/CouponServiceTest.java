package sample.hhplus_w2.service.coupon;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import sample.hhplus_w2.domain.coupon.Coupon;
import sample.hhplus_w2.domain.coupon.CouponStatus;
import sample.hhplus_w2.domain.coupon.CouponType;
import sample.hhplus_w2.domain.coupon.CouponUser;
import sample.hhplus_w2.repository.coupon.CouponRepository;
import sample.hhplus_w2.repository.coupon.CouponUserRepository;
import sample.hhplus_w2.repository.coupon.impl.CouponRepositoryImpl;
import sample.hhplus_w2.repository.coupon.impl.CouponUserRepositoryImpl;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

class CouponServiceTest {

    private CouponService couponService;
    private CouponRepository couponRepository;
    private CouponUserRepository couponUserRepository;

    @BeforeEach
    void setUp() {
        couponRepository = new CouponRepositoryImpl();
        couponUserRepository = new CouponUserRepositoryImpl();
        couponService = new CouponService(couponRepository, couponUserRepository);
    }

    @Test
    @DisplayName("쿠폰 생성 - 정상")
    void createCoupon() {
        // given
        Coupon coupon = Coupon.create("TEST1000", CouponType.FIXED, new BigDecimal("1000"),
                100, 1, null, null, null);

        // when
        Coupon created = couponService.createCoupon(coupon);

        // then
        assertThat(created).isNotNull();
        assertThat(created.getId()).isNotNull();
        assertThat(created.getCode()).isEqualTo("TEST1000");
    }

    @Test
    @DisplayName("쿠폰 조회 - ID로")
    void getCoupon() {
        // given
        Coupon coupon = Coupon.create("TEST1000", CouponType.FIXED, new BigDecimal("1000"),
                100, 1, null, null, null);
        Coupon created = couponService.createCoupon(coupon);

        // when
        Coupon found = couponService.getCoupon(created.getId());

        // then
        assertThat(found).isNotNull();
        assertThat(found.getId()).isEqualTo(created.getId());
    }

    @Test
    @DisplayName("쿠폰 조회 - 코드로")
    void getCouponByCode() {
        // given
        Coupon coupon = Coupon.create("TEST1000", CouponType.FIXED, new BigDecimal("1000"),
                100, 1, null, null, null);
        couponService.createCoupon(coupon);

        // when
        Coupon found = couponService.getCouponByCode("TEST1000");

        // then
        assertThat(found).isNotNull();
        assertThat(found.getCode()).isEqualTo("TEST1000");
    }

    @Test
    @DisplayName("공개된 쿠폰 조회")
    void getPublishedCoupons() {
        // given
        Coupon coupon1 = Coupon.create("COUPON1", CouponType.FIXED, new BigDecimal("1000"),
                100, 1, null, null, null);
        coupon1 = couponService.createCoupon(coupon1);
        couponService.publishCoupon(coupon1.getId());

        Coupon coupon2 = Coupon.create("COUPON2", CouponType.FIXED, new BigDecimal("2000"),
                50, 1, null, null, null);
        couponService.createCoupon(coupon2); // DRAFT 상태

        // when
        List<Coupon> publishedCoupons = couponService.getPublishedCoupons();

        // then
        assertThat(publishedCoupons).hasSize(1);
        assertThat(publishedCoupons.get(0).getStatus()).isEqualTo(CouponStatus.PUBLISHED);
    }

    @Test
    @DisplayName("쿠폰 발급 - 정상")
    void issueCoupon() {
        // given
        Coupon coupon = Coupon.create("TEST1000", CouponType.FIXED, new BigDecimal("1000"),
                10, 1, null, null, null);
        coupon = couponService.createCoupon(coupon);
        couponService.publishCoupon(coupon.getId());

        Long userId = 1L;

        // when
        CouponUser couponUser = couponService.issueCoupon(coupon.getId(), userId);

        // then
        assertThat(couponUser).isNotNull();
        assertThat(couponUser.getId()).isNotNull();
        assertThat(couponUser.getCouponId()).isEqualTo(coupon.getId());
        assertThat(couponUser.getUserId()).isEqualTo(userId);

        Coupon updatedCoupon = couponService.getCoupon(coupon.getId());
        assertThat(updatedCoupon.getIssued()).isEqualTo(1);
    }

    @Test
    @DisplayName("쿠폰 발급 - 중복 발급으로 예외 발생")
    void issueCoupon_Duplicate() {
        // given
        Coupon coupon = Coupon.create("TEST1000", CouponType.FIXED, new BigDecimal("1000"),
                10, 1, null, null, null);
        Coupon savedCoupon = couponService.createCoupon(coupon);
        couponService.publishCoupon(savedCoupon.getId());

        Long userId = 1L;
        Long couponId = savedCoupon.getId();
        couponService.issueCoupon(couponId, userId);

        // when & then
        assertThatThrownBy(() -> couponService.issueCoupon(couponId, userId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("이미 발급받은 쿠폰입니다");
    }

    @Test
    @DisplayName("쿠폰 발급 - 수량 소진으로 예외 발생")
    void issueCoupon_SoldOut() {
        // given - 총 2개만 발급 가능한 쿠폰 생성
        Coupon soldOutCoupon = Coupon.create("SOLDOUT123", CouponType.FIXED, new BigDecimal("1000"),
                2, 10, null, null, null);
        Coupon savedSoldOut = couponService.createCoupon(soldOutCoupon);
        couponService.publishCoupon(savedSoldOut.getId());
        final Long couponId = savedSoldOut.getId();

        // 2명에게 발급
        couponService.issueCoupon(couponId, 1001L);
        couponService.issueCoupon(couponId, 1002L);

        // when & then - 3번째 발급 시도는 실패해야 함
        assertThatThrownBy(() -> couponService.issueCoupon(couponId, 1003L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("발급 불가능한 쿠폰입니다");
    }

    @Test
    @DisplayName("쿠폰 발급 - DRAFT 상태에서 예외 발생")
    void issueCoupon_NotPublished() {
        // given
        Coupon coupon = Coupon.create("TEST1000", CouponType.FIXED, new BigDecimal("1000"),
                10, 1, null, null, null);
        Coupon savedCoupon = couponService.createCoupon(coupon);
        Long couponId = savedCoupon.getId();
        // publish 하지 않음

        // when & then
        assertThatThrownBy(() -> couponService.issueCoupon(couponId, 1L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("발급 불가능한 쿠폰입니다");
    }

    @Test
    @DisplayName("사용자별 쿠폰 조회")
    void getUserCoupons() {
        // given
        Coupon coupon1 = Coupon.create("COUPON1", CouponType.FIXED, new BigDecimal("1000"),
                10, 1, null, null, null);
        coupon1 = couponService.createCoupon(coupon1);
        couponService.publishCoupon(coupon1.getId());

        Coupon coupon2 = Coupon.create("COUPON2", CouponType.FIXED, new BigDecimal("2000"),
                10, 1, null, null, null);
        coupon2 = couponService.createCoupon(coupon2);
        couponService.publishCoupon(coupon2.getId());

        Long userId = 1L;
        couponService.issueCoupon(coupon1.getId(), userId);
        couponService.issueCoupon(coupon2.getId(), userId);

        // when
        List<CouponUser> userCoupons = couponService.getUserCoupons(userId);

        // then
        assertThat(userCoupons).hasSize(2);
    }

    @Test
    @DisplayName("쿠폰 공개")
    void publishCoupon() {
        // given
        Coupon coupon = Coupon.create("TEST1000", CouponType.FIXED, new BigDecimal("1000"),
                10, 1, null, null, null);
        coupon = couponService.createCoupon(coupon);

        // when
        couponService.publishCoupon(coupon.getId());

        // then
        Coupon published = couponService.getCoupon(coupon.getId());
        assertThat(published.getStatus()).isEqualTo(CouponStatus.PUBLISHED);
    }
}
