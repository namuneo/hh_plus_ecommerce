package sample.hhplus_w2.domain.coupon.event;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;

import java.time.LocalDateTime;

/**
 * 쿠폰 발급 결과 이벤트
 *
 * Kafka Topic: coupon-issue-result
 */
@Getter
public class CouponIssueResultEvent {

    private final String requestId;
    private final Long couponId;
    private final Long userId;
    private final String status;  // SUCCESS, FAILED
    private final LocalDateTime issuedAt;
    private final String errorMessage;

    private CouponIssueResultEvent(
            String requestId,
            Long couponId,
            Long userId,
            String status,
            LocalDateTime issuedAt,
            String errorMessage) {
        this.requestId = requestId;
        this.couponId = couponId;
        this.userId = userId;
        this.status = status;
        this.issuedAt = issuedAt;
        this.errorMessage = errorMessage;
    }

    @JsonCreator
    public static CouponIssueResultEvent of(
            @JsonProperty("requestId") String requestId,
            @JsonProperty("couponId") Long couponId,
            @JsonProperty("userId") Long userId,
            @JsonProperty("status") String status,
            @JsonProperty("issuedAt") LocalDateTime issuedAt,
            @JsonProperty("errorMessage") String errorMessage) {
        return new CouponIssueResultEvent(requestId, couponId, userId, status, issuedAt, errorMessage);
    }

    public static CouponIssueResultEvent success(CouponIssueRequestEvent request) {
        return new CouponIssueResultEvent(
                request.getRequestId(),
                request.getCouponId(),
                request.getUserId(),
                "SUCCESS",
                LocalDateTime.now(),
                null
        );
    }

    public static CouponIssueResultEvent failure(CouponIssueRequestEvent request, String errorMessage) {
        return new CouponIssueResultEvent(
                request.getRequestId(),
                request.getCouponId(),
                request.getUserId(),
                "FAILED",
                LocalDateTime.now(),
                errorMessage
        );
    }
}