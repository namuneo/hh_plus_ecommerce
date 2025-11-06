package sample.hhplus_w2.domain.coupon;

import lombok.Getter;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;

/**
 * Coupon 도메인 엔티티
 * 쿠폰 정책 및 선착순 발급 관리
 */
@Getter
public class Coupon {
    private Long id;
    private String code;                    // 쿠폰 코드
    private CouponType type;                // FIXED(정액) / PERCENTAGE(정률)
    private BigDecimal amount;              // 할인 금액 또는 할인율
    private Integer totalIssuable;          // 총 발급 가능 수량
    private Integer issued;                 // 현재까지 발급된 수량
    private Integer perUserLimit;           // 1인당 발급 제한
    private LocalDateTime validFrom;        // 유효 기간 시작
    private LocalDateTime validTo;          // 유효 기간 종료
    private BigDecimal minOrderAmount;      // 최소 주문 금액
    private Boolean stackable;              // 중복 사용 가능 여부
    private CouponStatus status;            // DRAFT, PUBLISHED, PAUSED, EXPIRED
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    private Coupon() {
    }

    /**
     * 신규 쿠폰 생성
     */
    public static Coupon create(String code, CouponType type, BigDecimal amount,
                                Integer totalIssuable, Integer perUserLimit,
                                LocalDateTime validFrom, LocalDateTime validTo,
                                BigDecimal minOrderAmount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("할인 금액/율은 0보다 커야 합니다.");
        }
        if (totalIssuable <= 0) {
            throw new IllegalArgumentException("발급 수량은 1 이상이어야 합니다.");
        }

        Coupon coupon = new Coupon();
        coupon.code = code;
        coupon.type = type;
        coupon.amount = amount;
        coupon.totalIssuable = totalIssuable;
        coupon.issued = 0;
        coupon.perUserLimit = perUserLimit != null ? perUserLimit : 1;
        coupon.validFrom = validFrom;
        coupon.validTo = validTo;
        coupon.minOrderAmount = minOrderAmount != null ? minOrderAmount : BigDecimal.ZERO;
        coupon.stackable = false;
        coupon.status = CouponStatus.DRAFT;
        coupon.createdAt = LocalDateTime.now();
        coupon.updatedAt = LocalDateTime.now();
        return coupon;
    }

    /**
     * ID 설정 (Repository에서 사용)
     */
    public void assignId(Long id) {
        if (this.id != null) {
            throw new IllegalStateException("ID는 이미 할당되었습니다.");
        }
        this.id = id;
    }

    /**
     * 쿠폰 발급 (선착순)
     * @return 발급 성공 여부
     */
    public synchronized boolean issue() {
        if (!CouponStatus.PUBLISHED.equals(this.status)) {
            throw new IllegalStateException("발급 가능한 상태가 아닙니다.");
        }
        if (this.issued >= this.totalIssuable) {
            return false; // 발급 수량 소진
        }
        this.issued++;
        this.updatedAt = LocalDateTime.now();
        return true;
    }

    /**
     * 발급 가능 여부 확인
     */
    public boolean canIssue() {
        return CouponStatus.PUBLISHED.equals(this.status)
                && this.issued < this.totalIssuable
                && isValidPeriod();
    }

    /**
     * 유효 기간 확인
     */
    public boolean isValidPeriod() {
        LocalDateTime now = LocalDateTime.now();
        return (validFrom == null || now.isAfter(validFrom))
                && (validTo == null || now.isBefore(validTo));
    }

    /**
     * 쿠폰 공개 (발급 시작)
     */
    public void publish() {
        this.status = CouponStatus.PUBLISHED;
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * 쿠폰 일시정지
     */
    public void pause() {
        this.status = CouponStatus.PAUSED;
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * 쿠폰 만료
     */
    public void expire() {
        this.status = CouponStatus.EXPIRED;
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * 할인 금액 계산
     * @param orderAmount 주문 금액
     * @return 할인 금액
     */
    public BigDecimal calculateDiscount(BigDecimal orderAmount) {
        if (orderAmount.compareTo(this.minOrderAmount) < 0) {
            throw new IllegalArgumentException("최소 주문 금액을 충족하지 않습니다.");
        }

        if (CouponType.FIXED.equals(this.type)) {
            return this.amount; // 정액 할인
        } else {
            // 정률 할인 (amount는 퍼센트)
            return orderAmount.multiply(this.amount).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
        }
    }
}
