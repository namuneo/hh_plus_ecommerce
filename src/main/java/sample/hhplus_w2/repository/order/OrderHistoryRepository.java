package sample.hhplus_w2.repository.order;

import sample.hhplus_w2.domain.order.OrderHistory;

import java.util.List;
import java.util.Optional;

public interface OrderHistoryRepository {
    OrderHistory save(OrderHistory orderHistory);
    Optional<OrderHistory> findById(Long id);
    List<OrderHistory> findByOrderId(Long orderId);
    List<OrderHistory> findAll();
    void delete(Long id);
    void deleteByOrderId(Long orderId);
    void deleteAll();
}
