package sample.hhplus_w2.repository.product;

import sample.hhplus_w2.domain.product.Product;

import java.util.List;
import java.util.Optional;

public interface ProductRepository {
    Product save(Product product);
    Optional<Product> findById(Long id);
    List<Product> findByCategoryId(Long categoryId);
    List<Product> findAll();
    List<Product> findByIsActive(Boolean isActive);
    void delete(Long id);
    void deleteAll();
}
