package sample.hhplus_w2.domain.order.event;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 주문 완료 이벤트
 * 주문 결제가 성공적으로 완료되었을 때 발행되는 도메인 이벤트
 */
@Getter
public class OrderCompletedEvent {

    private final Long orderId;
    private final Long userId;
    private final List<OrderItemSnapshot> orderItems;
    private final LocalDateTime completedAt;

    @JsonCreator
    private OrderCompletedEvent(
            @JsonProperty("orderId") Long orderId,
            @JsonProperty("userId") Long userId,
            @JsonProperty("orderItems") List<OrderItemSnapshot> orderItems,
            @JsonProperty("completedAt") LocalDateTime completedAt) {
        this.orderId = orderId;
        this.userId = userId;
        this.orderItems = orderItems;
        this.completedAt = completedAt;
    }

    public static OrderCompletedEvent of(Long orderId, Long userId, List<OrderItemSnapshot> orderItems) {
        return new OrderCompletedEvent(orderId, userId, orderItems, LocalDateTime.now());
    }

    /**
     * 주문 항목 스냅샷 (직렬화 가능)
     */
    @Getter
    public static class OrderItemSnapshot {
        private final Long productId;
        private final Integer qty;

        @JsonCreator
        public OrderItemSnapshot(
                @JsonProperty("productId") Long productId,
                @JsonProperty("qty") Integer qty) {
            this.productId = productId;
            this.qty = qty;
        }
    }
}