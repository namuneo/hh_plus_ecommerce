package sample.hhplus_w2.repository.order.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import sample.hhplus_w2.domain.order.OrderHistory;
import sample.hhplus_w2.infrastructure.order.OrderHistoryJpaRepository;
import sample.hhplus_w2.repository.order.OrderHistoryRepository;

import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class OrderHistoryRepositoryImpl implements OrderHistoryRepository {
    private final OrderHistoryJpaRepository jpaRepository;

    @Override
    public OrderHistory save(OrderHistory orderHistory) {
        return jpaRepository.save(orderHistory);
    }

    @Override
    public Optional<OrderHistory> findById(Long id) {
        return jpaRepository.findById(id);
    }

    @Override
    public List<OrderHistory> findByOrderId(Long orderId) {
        return jpaRepository.findByOrderIdOrderByCreatedAt(orderId);
    }

    @Override
    public List<OrderHistory> findAll() {
        return jpaRepository.findAll();
    }

    @Override
    public void delete(Long id) {
        jpaRepository.deleteById(id);
    }

    @Override
    public void deleteByOrderId(Long orderId) {
        List<OrderHistory> histories = jpaRepository.findByOrderIdOrderByCreatedAt(orderId);
        jpaRepository.deleteAll(histories);
    }

    @Override
    public void deleteAll() {
        jpaRepository.deleteAll();
    }
}
