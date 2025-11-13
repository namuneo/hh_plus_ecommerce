package sample.hhplus_w2.infrastructure.order;

import org.springframework.data.jpa.repository.JpaRepository;
import sample.hhplus_w2.domain.order.OrderHistory;

import java.util.List;

public interface OrderHistoryJpaRepository extends JpaRepository<OrderHistory, Long> {
    List<OrderHistory> findByOrderIdOrderByCreatedAt(Long orderId);
}
