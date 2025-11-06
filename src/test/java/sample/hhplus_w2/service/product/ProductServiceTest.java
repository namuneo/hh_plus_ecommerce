package sample.hhplus_w2.service.product;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import sample.hhplus_w2.domain.product.Product;
import sample.hhplus_w2.repository.product.ProductRepository;
import sample.hhplus_w2.repository.product.impl.ProductRepositoryImpl;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

class ProductServiceTest {

    private ProductService productService;
    private ProductRepository productRepository;

    @BeforeEach
    void setUp() {
        productRepository = new ProductRepositoryImpl();
        productService = new ProductService(productRepository);
    }

    @Test
    @DisplayName("상품 생성 - 정상")
    void createProduct() {
        // given
        Long categoryId = 1L;
        String name = "테스트 상품";
        String brand = "테스트 브랜드";
        String description = "테스트 설명";
        BigDecimal price = new BigDecimal("10000");
        Integer stockQty = 100;

        // when
        Product product = productService.createProduct(categoryId, name, brand, description, price, stockQty);

        // then
        assertThat(product).isNotNull();
        assertThat(product.getId()).isNotNull();
        assertThat(product.getName()).isEqualTo(name);
        assertThat(product.getStockQty()).isEqualTo(stockQty);
    }

    @Test
    @DisplayName("상품 조회 - 정상")
    void getProduct() {
        // given
        Product created = productService.createProduct(1L, "상품", "브랜드", "설명",
                new BigDecimal("10000"), 100);

        // when
        Product found = productService.getProduct(created.getId());

        // then
        assertThat(found).isNotNull();
        assertThat(found.getId()).isEqualTo(created.getId());
        assertThat(found.getName()).isEqualTo("상품");
    }

    @Test
    @DisplayName("상품 조회 - 존재하지 않는 ID로 예외 발생")
    void getProduct_NotFound() {
        // when & then
        assertThatThrownBy(() -> productService.getProduct(999L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("상품을 찾을 수 없습니다");
    }

    @Test
    @DisplayName("모든 상품 조회")
    void getAllProducts() {
        // given
        productService.createProduct(1L, "상품1", "브랜드", "설명", new BigDecimal("10000"), 100);
        productService.createProduct(1L, "상품2", "브랜드", "설명", new BigDecimal("20000"), 50);

        // when
        List<Product> products = productService.getAllProducts();

        // then
        assertThat(products).hasSize(2);
    }

    @Test
    @DisplayName("카테고리별 상품 조회")
    void getProductsByCategory() {
        // given
        productService.createProduct(1L, "상품1", "브랜드", "설명", new BigDecimal("10000"), 100);
        productService.createProduct(2L, "상품2", "브랜드", "설명", new BigDecimal("20000"), 50);
        productService.createProduct(1L, "상품3", "브랜드", "설명", new BigDecimal("15000"), 30);

        // when
        List<Product> category1Products = productService.getProductsByCategory(1L);

        // then
        assertThat(category1Products).hasSize(2);
    }

    @Test
    @DisplayName("활성 상품 조회")
    void getActiveProducts() {
        // given
        Product product1 = productService.createProduct(1L, "상품1", "브랜드", "설명",
                new BigDecimal("10000"), 100);
        Product product2 = productService.createProduct(1L, "상품2", "브랜드", "설명",
                new BigDecimal("20000"), 50);

        product2.deactivate();
        productRepository.save(product2);

        // when
        List<Product> activeProducts = productService.getActiveProducts();

        // then
        assertThat(activeProducts).hasSize(1);
        assertThat(activeProducts.get(0).getId()).isEqualTo(product1.getId());
    }

    @Test
    @DisplayName("상품 정보 업데이트")
    void updateProduct() {
        // given
        Product created = productService.createProduct(1L, "원래 상품", "원래 브랜드", "원래 설명",
                new BigDecimal("10000"), 100);
        String newName = "새 상품명";
        String newBrand = "새 브랜드";
        String newDescription = "새 설명";
        BigDecimal newPrice = new BigDecimal("20000");

        // when
        Product updated = productService.updateProduct(created.getId(), newName, newBrand,
                newDescription, newPrice);

        // then
        assertThat(updated.getName()).isEqualTo(newName);
        assertThat(updated.getBrand()).isEqualTo(newBrand);
        assertThat(updated.getDescription()).isEqualTo(newDescription);
        assertThat(updated.getPrice()).isEqualTo(newPrice);
    }

    @Test
    @DisplayName("재고 증가")
    void increaseStock() {
        // given
        Product product = productService.createProduct(1L, "상품", "브랜드", "설명",
                new BigDecimal("10000"), 10);

        // when
        productService.increaseStock(product.getId(), 5);

        // then
        Product updated = productService.getProduct(product.getId());
        assertThat(updated.getStockQty()).isEqualTo(15);
    }

    @Test
    @DisplayName("재고 감소 - 낙관적 락 성공")
    void decreaseStock_Success() {
        // given
        Product product = productService.createProduct(1L, "상품", "브랜드", "설명",
                new BigDecimal("10000"), 10);

        // when
        boolean result = productService.decreaseStock(product.getId(), 5, product.getVersion());

        // then
        assertThat(result).isTrue();
        Product updated = productService.getProduct(product.getId());
        assertThat(updated.getStockQty()).isEqualTo(5);
        assertThat(updated.getVersion()).isEqualTo(1);
    }

    @Test
    @DisplayName("재고 감소 - 버전 불일치로 실패")
    void decreaseStock_VersionMismatch() {
        // given
        Product product = productService.createProduct(1L, "상품", "브랜드", "설명",
                new BigDecimal("10000"), 10);
        Integer wrongVersion = 999;

        // when
        boolean result = productService.decreaseStock(product.getId(), 5, wrongVersion);

        // then
        assertThat(result).isFalse();
        Product updated = productService.getProduct(product.getId());
        assertThat(updated.getStockQty()).isEqualTo(10); // 재고 변경 없음
    }
}
