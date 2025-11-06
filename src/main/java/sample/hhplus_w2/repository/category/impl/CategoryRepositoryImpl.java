package sample.hhplus_w2.repository.category.impl;

import org.springframework.stereotype.Repository;
import sample.hhplus_w2.domain.category.Category;
import sample.hhplus_w2.repository.category.CategoryRepository;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

@Repository
public class CategoryRepositoryImpl implements CategoryRepository {
    private final Map<Long, Category> store = new ConcurrentHashMap<>();
    private final AtomicLong idGenerator = new AtomicLong(1);

    @Override
    public Category save(Category category) {
        if (category.getId() == null) {
            category.assignId(idGenerator.getAndIncrement());
        }
        store.put(category.getId(), category);
        return category;
    }

    @Override
    public Optional<Category> findById(Long id) {
        return Optional.ofNullable(store.get(id));
    }

    @Override
    public List<Category> findAll() {
        return store.values().stream().collect(Collectors.toList());
    }

    @Override
    public List<Category> findByIsActive(Boolean isActive) {
        return store.values().stream()
                .filter(category -> category.getIsActive().equals(isActive))
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
