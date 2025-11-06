package sample.hhplus_w2.service.product;

import org.springframework.stereotype.Service;
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

    public Product createProduct(Long categoryId, String name, String brand, String description,
                                BigDecimal price, Integer stockQty) {
        Product product = Product.create(categoryId, name, brand, description, price, stockQty);
        return productRepository.save(product);
    }

    public Product getProduct(Long id) {
        return productRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("상품을 찾을 수 없습니다: " + id));
    }

    public List<Product> getAllProducts() {
        return productRepository.findAll();
    }

    public List<Product> getProductsByCategory(Long categoryId) {
        return productRepository.findByCategoryId(categoryId);
    }

    public List<Product> getActiveProducts() {
        return productRepository.findByIsActive(true);
    }

    public Product updateProduct(Long id, String name, String brand, String description, BigDecimal price) {
        Product product = getProduct(id);
        product.updateInfo(name, brand, description, price);
        return productRepository.save(product);
    }

    public void increaseStock(Long productId, Integer quantity) {
        Product product = getProduct(productId);
        product.increaseStock(quantity);
        productRepository.save(product);
    }

    public boolean decreaseStock(Long productId, Integer quantity, Integer expectedVersion) {
        Product product = getProduct(productId);
        boolean success = product.decreaseStock(quantity, expectedVersion);
        if (success) {
            productRepository.save(product);
        }
        return success;
    }
}
