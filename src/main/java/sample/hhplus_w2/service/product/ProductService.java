package sample.hhplus_w2.service.product;

import jakarta.persistence.OptimisticLockException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sample.hhplus_w2.domain.product.Product;
import sample.hhplus_w2.repository.product.ProductRepository;

import java.math.BigDecimal;
import java.util.List;

/**
 * Product Service
 */
@Service
public class ProductService {

    private final ProductRepository productRepository;

    public ProductService(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    @Transactional
    public Product createProduct(Long categoryId, String name, String brand, String description,
                                BigDecimal price, Integer stockQty) {
        Product product = Product.create(categoryId, name, brand, description, price, stockQty);
        return productRepository.save(product);
    }

    @Transactional(readOnly = true)
    public Product getProduct(Long id) {
        return productRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("상품을 찾을 수 없습니다: " + id));
    }

    @Transactional(readOnly = true)
    public List<Product> getAllProducts() {
        return productRepository.findAll();
    }

    @Transactional(readOnly = true)
    public List<Product> getProductsByCategory(Long categoryId) {
        return productRepository.findByCategoryId(categoryId);
    }

    @Transactional(readOnly = true)
    public List<Product> getActiveProducts() {
        return productRepository.findByIsActive(true);
    }

    @Transactional
    public Product updateProduct(Long id, String name, String brand, String description, BigDecimal price) {
        Product product = getProduct(id);
        product.updateInfo(name, brand, description, price);
        return productRepository.save(product);
    }

    @Transactional
    public void increaseStock(Long productId, Integer quantity) {
        Product product = getProduct(productId);
        product.increaseStock(quantity);
        productRepository.save(product);
    }

    /**
     * 재고 차감 (낙관적 락 사용)
     * OptimisticLockException 발생 시 false 반환
     */
    @Transactional
    public boolean decreaseStock(Long productId, Integer quantity) {
        try {
            Product product = getProduct(productId);
            product.decreaseStock(quantity);
            productRepository.save(product);
            return true;
        } catch (OptimisticLockException | ObjectOptimisticLockingFailureException e) {
            // 낙관적 락 충돌 - 다른 트랜잭션이 먼저 수정함
            return false;
        } catch (IllegalStateException e) {
            // 재고 부족
            throw e;
        }
    }
}
