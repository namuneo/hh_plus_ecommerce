package sample.hhplus_w2.infrastructure.order;

import org.springframework.data.jpa.repository.JpaRepository;
import sample.hhplus_w2.domain.order.OrderItem;

import java.util.List;

public interface OrderItemJpaRepository extends JpaRepository<OrderItem, Long> {
    List<OrderItem> findByOrderId(Long orderId);
    List<OrderItem> findByProductId(Long productId);
}
