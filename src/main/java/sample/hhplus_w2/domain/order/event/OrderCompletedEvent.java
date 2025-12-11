package sample.hhplus_w2.domain.order.event;

import lombok.Getter;
import sample.hhplus_w2.domain.order.OrderItem;

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
    private final List<OrderItem> orderItems;
    private final LocalDateTime completedAt;

    private OrderCompletedEvent(Long orderId, Long userId, List<OrderItem> orderItems, LocalDateTime completedAt) {
        this.orderId = orderId;
        this.userId = userId;
        this.orderItems = orderItems;
        this.completedAt = completedAt;
    }

    public static OrderCompletedEvent of(Long orderId, Long userId, List<OrderItem> orderItems) {
        return new OrderCompletedEvent(orderId, userId, orderItems, LocalDateTime.now());
    }
}