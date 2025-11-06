package sample.hhplus_w2.repository.product.impl;

import org.springframework.stereotype.Repository;
import sample.hhplus_w2.domain.product.Product;
import sample.hhplus_w2.repository.product.ProductRepository;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

@Repository
public class ProductRepositoryImpl implements ProductRepository {
    private final Map<Long, Product> store = new ConcurrentHashMap<>();
    private final AtomicLong idGenerator = new AtomicLong(1);

    @Override
    public Product save(Product product) {
        if (product.getId() == null) {
            product.assignId(idGenerator.getAndIncrement());
        }
        store.put(product.getId(), product);
        return product;
    }

    @Override
    public Optional<Product> findById(Long id) {
        return Optional.ofNullable(store.get(id));
    }

    @Override
    public List<Product> findByCategoryId(Long categoryId) {
        return store.values().stream()
                .filter(product -> product.getCategoryId().equals(categoryId))
                .collect(Collectors.toList());
    }

    @Override
    public List<Product> findAll() {
        return store.values().stream().collect(Collectors.toList());
    }

    @Override
    public List<Product> findByIsActive(Boolean isActive) {
        return store.values().stream()
                .filter(product -> product.getIsActive().equals(isActive))
                .collect(Collectors.toList());
    }

    @Override
    public void delete(Long id) {
        store.remove(id);
    }

    @Override
    public void deleteAll() {
        store.clear();
    }
}
