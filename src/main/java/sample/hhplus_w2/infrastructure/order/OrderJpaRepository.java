package sample.hhplus_w2.infrastructure.order;

import org.springframework.data.jpa.repository.JpaRepository;
import sample.hhplus_w2.domain.order.Order;
import sample.hhplus_w2.domain.order.OrderStatus;

import java.util.List;

public interface OrderJpaRepository extends JpaRepository<Order, Long> {
    List<Order> findByUserId(Long userId);
    List<Order> findByStatus(OrderStatus status);
    List<Order> findByUserIdAndStatus(Long userId, OrderStatus status);
}
