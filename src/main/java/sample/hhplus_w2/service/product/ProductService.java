package sample.hhplus_w2.service.product;

import jakarta.persistence.OptimisticLockException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sample.hhplus_w2.domain.product.Product;
import sample.hhplus_w2.infrastructure.lock.DistributedLock;
import sample.hhplus_w2.repository.product.ProductRepository;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;

/**
 * Product Service
 */
@Slf4j
@Service
public class ProductService {

    private final ProductRepository productRepository;
    private final DistributedLock distributedLock;

    public ProductService(ProductRepository productRepository, DistributedLock distributedLock) {
        this.productRepository = productRepository;
        this.distributedLock = distributedLock;
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

    /**
     * 재고 차감 (분산락 사용)
     *
     * 분산 환경에서의 동시성 제어:
     * 1. 분산락 획득 (Redis SETNX)
     * 2. 트랜잭션 시작 및 재고 차감
     * 3. 트랜잭션 커밋
     * 4. 분산락 해제
     *
     * 주의사항:
     * - 락 획득 → 트랜잭션 시작 순서 보장
     * - 트랜잭션 종료 후 락 해제
     * - 락 타임아웃은 트랜잭션 실행 시간보다 충분히 길어야 함
     *
     * @param productId 상품 ID
     * @param quantity 차감 수량
     */
    public void decreaseStockWithDistributedLock(Long productId, Integer quantity) {
        String lockKey = "product:stock:" + productId;

        // 분산락으로 보호된 재고 차감
        distributedLock.executeWithLock(
            lockKey,
            Duration.ofSeconds(5),  // 락 획득 대기 시간
            Duration.ofSeconds(10), // 락 보유 시간
            () -> {
                // 트랜잭션 내부에서 실행
                decreaseStockInTransaction(productId, quantity);
                return null;
            }
        );
    }

    /**
     * 트랜잭션 내부에서 재고 차감
     * - 분산락이 이미 획득된 상태에서 실행됨
     */
    @Transactional
    public void decreaseStockInTransaction(Long productId, Integer quantity) {
        Product product = getProduct(productId);
        product.decreaseStock(quantity);
        productRepository.save(product);
        log.info("재고 차감 완료: productId={}, quantity={}, remainingStock={}",
            productId, quantity, product.getStockQty());
    }
}
