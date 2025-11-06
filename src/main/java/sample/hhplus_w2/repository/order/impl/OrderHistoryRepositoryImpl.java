package sample.hhplus_w2.repository.order.impl;

import org.springframework.stereotype.Repository;
import sample.hhplus_w2.domain.order.OrderHistory;
import sample.hhplus_w2.repository.order.OrderHistoryRepository;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

@Repository
public class OrderHistoryRepositoryImpl implements OrderHistoryRepository {
    private final Map<Long, OrderHistory> store = new ConcurrentHashMap<>();
    private final AtomicLong idGenerator = new AtomicLong(1);

    @Override
    public OrderHistory save(OrderHistory orderHistory) {
        if (orderHistory.getId() == null) {
            orderHistory.assignId(idGenerator.getAndIncrement());
        }
        store.put(orderHistory.getId(), orderHistory);
        return orderHistory;
    }

    @Override
    public Optional<OrderHistory> findById(Long id) {
        return Optional.ofNullable(store.get(id));
    }

    @Override
    public List<OrderHistory> findByOrderId(Long orderId) {
        return store.values().stream()
                .filter(history -> history.getOrderId().equals(orderId))
                .sorted((h1, h2) -> h1.getCreatedAt().compareTo(h2.getCreatedAt()))
                .collect(Collectors.toList());
    }

    @Override
    public List<OrderHistory> findAll() {
        return store.values().stream().collect(Collectors.toList());
    }

    @Override
    public void delete(Long id) {
        store.remove(id);
    }

    @Override
    public void deleteByOrderId(Long orderId) {
        List<Long> idsToDelete = store.values().stream()
                .filter(history -> history.getOrderId().equals(orderId))
                .map(OrderHistory::getId)
                .collect(Collectors.toList());
        idsToDelete.forEach(store::remove);
    }

    @Override
    public void deleteAll() {
        store.clear();
    }
}
