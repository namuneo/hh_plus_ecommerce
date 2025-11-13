package sample.hhplus_w2.infrastructure.category;

import org.springframework.data.jpa.repository.JpaRepository;
import sample.hhplus_w2.domain.category.Category;

import java.util.List;

public interface CategoryJpaRepository extends JpaRepository<Category, Long> {
    List<Category> findByIsActive(Boolean isActive);
}
