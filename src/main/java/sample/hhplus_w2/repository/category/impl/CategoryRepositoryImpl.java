package sample.hhplus_w2.repository.category.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import sample.hhplus_w2.domain.category.Category;
import sample.hhplus_w2.infrastructure.category.CategoryJpaRepository;
import sample.hhplus_w2.repository.category.CategoryRepository;

import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class CategoryRepositoryImpl implements CategoryRepository {
    private final CategoryJpaRepository jpaRepository;

    @Override
    public Category save(Category category) {
        return jpaRepository.save(category);
    }

    @Override
    public Optional<Category> findById(Long id) {
        return jpaRepository.findById(id);
    }

    @Override
    public List<Category> findAll() {
        return jpaRepository.findAll();
    }

    @Override
    public List<Category> findByIsActive(Boolean isActive) {
        return jpaRepository.findByIsActive(isActive);
    }

    @Override
    public void delete(Long id) {
        jpaRepository.deleteById(id);
    }

    @Override
    public void deleteAll() {
        jpaRepository.deleteAll();
    }
}
