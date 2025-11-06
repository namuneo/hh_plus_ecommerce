package sample.hhplus_w2.repository.category;

import sample.hhplus_w2.domain.category.Category;

import java.util.List;
import java.util.Optional;

/**
 * Category Repository 인터페이스
 */
public interface CategoryRepository {
    Category save(Category category);
    Optional<Category> findById(Long id);
    List<Category> findAll();
    List<Category> findByIsActive(Boolean isActive);
    void delete(Long id);
    void deleteAll();
}
