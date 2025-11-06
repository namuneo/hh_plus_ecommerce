package sample.hhplus_w2.repository.order.impl;

import org.springframework.stereotype.Repository;
import sample.hhplus_w2.domain.order.OrderItem;
import sample.hhplus_w2.repository.order.OrderItemRepository;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

@Repository
public class OrderItemRepositoryImpl implements OrderItemRepository {
    private final Map<Long, OrderItem> store = new ConcurrentHashMap<>();
    private final AtomicLong idGenerator = new AtomicLong(1);

    @Override
    public OrderItem save(OrderItem orderItem) {
        if (orderItem.getId() == null) {
            orderItem.assignId(idGenerator.getAndIncrement());
        }
        store.put(orderItem.getId(), orderItem);
        return orderItem;
    }

    @Override
    public Optional<OrderItem> findById(Long id) {
        return Optional.ofNullable(store.get(id));
    }

    @Override
    public List<OrderItem> findByOrderId(Long orderId) {
        return store.values().stream()
                .filter(item -> item.getOrderId().equals(orderId))
                .collect(Collectors.toList());
    }

    @Override
    public List<OrderItem> findByProductId(Long productId) {
        return store.values().stream()
                .filter(item -> item.getProductId().equals(productId))
                .collect(Collectors.toList());
    }

    @Override
    public List<OrderItem> findAll() {
        return store.values().stream().collect(Collectors.toList());
    }

    @Override
    public void delete(Long id) {
        store.remove(id);
    }

    @Override
    public void deleteByOrderId(Long orderId) {
        List<Long> idsToDelete = store.values().stream()
                .filter(item -> item.getOrderId().equals(orderId))
                .map(OrderItem::getId)
                .collect(Collectors.toList());
        idsToDelete.forEach(store::remove);
    }

    @Override
    public void deleteAll() {
        store.clear();
    }
}
