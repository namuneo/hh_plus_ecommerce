package sample.hhplus_w2.repository.order;

import sample.hhplus_w2.domain.order.Order;
import sample.hhplus_w2.domain.order.OrderStatus;

import java.util.List;
import java.util.Optional;

public interface OrderRepository {
    Order save(Order order);
    Optional<Order> findById(Long id);
    List<Order> findByUserId(Long userId);
    List<Order> findByStatus(OrderStatus status);
    List<Order> findByUserIdAndStatus(Long userId, OrderStatus status);
    List<Order> findAll();
    void delete(Long id);
    void deleteAll();
}
