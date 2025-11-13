package sample.hhplus_w2.repository.order.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import sample.hhplus_w2.domain.order.OrderItem;
import sample.hhplus_w2.infrastructure.order.OrderItemJpaRepository;
import sample.hhplus_w2.repository.order.OrderItemRepository;

import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class OrderItemRepositoryImpl implements OrderItemRepository {
    private final OrderItemJpaRepository jpaRepository;

    @Override
    public OrderItem save(OrderItem orderItem) {
        return jpaRepository.save(orderItem);
    }

    @Override
    public Optional<OrderItem> findById(Long id) {
        return jpaRepository.findById(id);
    }

    @Override
    public List<OrderItem> findByOrderId(Long orderId) {
        return jpaRepository.findByOrderId(orderId);
    }

    @Override
    public List<OrderItem> findByProductId(Long productId) {
        return jpaRepository.findByProductId(productId);
    }

    @Override
    public List<OrderItem> findAll() {
        return jpaRepository.findAll();
    }

    @Override
    public void delete(Long id) {
        jpaRepository.deleteById(id);
    }

    @Override
    public void deleteByOrderId(Long orderId) {
        List<OrderItem> items = jpaRepository.findByOrderId(orderId);
        jpaRepository.deleteAll(items);
    }

    @Override
    public void deleteAll() {
        jpaRepository.deleteAll();
    }
}
