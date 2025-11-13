package sample.hhplus_w2.repository.product;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import sample.hhplus_w2.domain.product.Product;
import sample.hhplus_w2.repository.product.impl.ProductRepositoryImpl;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;

@DataJpaTest
@ActiveProfiles("test")
@Import(ProductRepositoryImpl.class)
class ProductRepositoryTest {

    @Autowired
    private ProductRepository productRepository;

    @Test
    @DisplayName("상품 저장 - 신규")
    void save_New() {
        // given
        Product product = Product.create(1L, "상품", "브랜드", "설명",
                new BigDecimal("10000"), 100);

        // when
        Product saved = productRepository.save(product);

        // then
        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getName()).isEqualTo("상품");
    }

    @Test
    @DisplayName("상품 저장 - 업데이트")
    void save_Update() {
        // given
        Product product = Product.create(1L, "상품", "브랜드", "설명",
                new BigDecimal("10000"), 100);
        Product saved = productRepository.save(product);

        // when
        saved.updateInfo("새 상품명", "새 브랜드", "새 설명", new BigDecimal("20000"));
        Product updated = productRepository.save(saved);

        // then
        assertThat(updated.getId()).isEqualTo(saved.getId());
        assertThat(updated.getName()).isEqualTo("새 상품명");
    }

    @Test
    @DisplayName("상품 조회 - ID로")
    void findById() {
        // given
        Product product = Product.create(1L, "상품", "브랜드", "설명",
                new BigDecimal("10000"), 100);
        Product saved = productRepository.save(product);

        // when
        Optional<Product> found = productRepository.findById(saved.getId());

        // then
        assertThat(found).isPresent();
        assertThat(found.get().getName()).isEqualTo("상품");
    }

    @Test
    @DisplayName("상품 조회 - 존재하지 않는 ID")
    void findById_NotFound() {
        // when
        Optional<Product> found = productRepository.findById(999L);

        // then
        assertThat(found).isEmpty();
    }

    @Test
    @DisplayName("모든 상품 조회")
    void findAll() {
        // given
        productRepository.save(Product.create(1L, "상품1", "브랜드", "설명",
                new BigDecimal("10000"), 100));
        productRepository.save(Product.create(1L, "상품2", "브랜드", "설명",
                new BigDecimal("20000"), 50));

        // when
        List<Product> products = productRepository.findAll();

        // then
        assertThat(products).hasSize(2);
    }

    @Test
    @DisplayName("카테고리별 상품 조회")
    void findByCategoryId() {
        // given
        productRepository.save(Product.create(1L, "상품1", "브랜드", "설명",
                new BigDecimal("10000"), 100));
        productRepository.save(Product.create(2L, "상품2", "브랜드", "설명",
                new BigDecimal("20000"), 50));
        productRepository.save(Product.create(1L, "상품3", "브랜드", "설명",
                new BigDecimal("15000"), 30));

        // when
        List<Product> category1Products = productRepository.findByCategoryId(1L);

        // then
        assertThat(category1Products).hasSize(2);
    }

    @Test
    @DisplayName("활성화 상태로 상품 조회")
    void findByIsActive() {
        // given
        Product product1 = productRepository.save(Product.create(1L, "상품1", "브랜드", "설명",
                new BigDecimal("10000"), 100));
        Product product2 = productRepository.save(Product.create(1L, "상품2", "브랜드", "설명",
                new BigDecimal("20000"), 50));

        product2.deactivate();
        productRepository.save(product2);

        // when
        List<Product> activeProducts = productRepository.findByIsActive(true);

        // then
        assertThat(activeProducts).hasSize(1);
        assertThat(activeProducts.get(0).getId()).isEqualTo(product1.getId());
    }

    @Test
    @DisplayName("상품 삭제")
    void delete() {
        // given
        Product product = Product.create(1L, "상품", "브랜드", "설명",
                new BigDecimal("10000"), 100);
        Product saved = productRepository.save(product);

        // when
        productRepository.delete(saved.getId());

        // then
        Optional<Product> found = productRepository.findById(saved.getId());
        assertThat(found).isEmpty();
    }
}
