package sample.hhplus_w2.domain.coupon.event;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 쿠폰 발급 요청 이벤트
 *
 * Kafka Topic: coupon-issue-request
 */
@Getter
public class CouponIssueRequestEvent {

    private final String requestId;
    private final Long couponId;
    private final Long userId;
    private final LocalDateTime timestamp;

    private CouponIssueRequestEvent(String requestId, Long couponId, Long userId, LocalDateTime timestamp) {
        this.requestId = requestId;
        this.couponId = couponId;
        this.userId = userId;
        this.timestamp = timestamp;
    }

    @JsonCreator
    public static CouponIssueRequestEvent of(
            @JsonProperty("requestId") String requestId,
            @JsonProperty("couponId") Long couponId,
            @JsonProperty("userId") Long userId,
            @JsonProperty("timestamp") LocalDateTime timestamp) {
        return new CouponIssueRequestEvent(requestId, couponId, userId, timestamp);
    }

    public static CouponIssueRequestEvent create(Long couponId, Long userId) {
        return new CouponIssueRequestEvent(
                UUID.randomUUID().toString(),
                couponId,
                userId,
                LocalDateTime.now()
        );
    }
}
