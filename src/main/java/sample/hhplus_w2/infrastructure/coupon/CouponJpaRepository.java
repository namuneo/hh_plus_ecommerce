package sample.hhplus_w2.infrastructure.coupon;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import sample.hhplus_w2.domain.coupon.Coupon;
import sample.hhplus_w2.domain.coupon.CouponStatus;

import java.util.List;
import java.util.Optional;

public interface CouponJpaRepository extends JpaRepository<Coupon, Long> {
    Optional<Coupon> findByCode(String code);
    List<Coupon> findByStatus(CouponStatus status);

    /**
     * 쿠폰 발급 수량 원자적 증가
     *
     * Race Condition 방지:
     * - issued_qty < total_qty 조건으로 수량 검증
     * - 동시에 issued_qty 증가
     *
     * @param couponId 쿠폰 ID
     * @return 업데이트된 행 수 (0 = 수량 소진, 1 = 성공)
     */
    @Modifying
    @Query("UPDATE Coupon c SET c.issuedQty = c.issuedQty + 1 " +
           "WHERE c.id = :couponId AND c.issuedQty < c.totalQty AND c.status = 'PUBLISHED'")
    int incrementIssuedQty(@Param("couponId") Long couponId);
}
