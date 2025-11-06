package sample.hhplus_w2.repository.order;

import sample.hhplus_w2.domain.order.OrderItem;

import java.util.List;
import java.util.Optional;

public interface OrderItemRepository {
    OrderItem save(OrderItem orderItem);
    Optional<OrderItem> findById(Long id);
    List<OrderItem> findByOrderId(Long orderId);
    List<OrderItem> findByProductId(Long productId);
    List<OrderItem> findAll();
    void delete(Long id);
    void deleteByOrderId(Long orderId);
    void deleteAll();
}
