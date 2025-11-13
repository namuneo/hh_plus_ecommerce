package sample.hhplus_w2.infrastructure.product;

import org.springframework.data.jpa.repository.JpaRepository;
import sample.hhplus_w2.domain.product.Product;

import java.util.List;

public interface ProductJpaRepository extends JpaRepository<Product, Long> {
    List<Product> findByCategoryId(Long categoryId);
    List<Product> findByIsActive(Boolean isActive);
}
