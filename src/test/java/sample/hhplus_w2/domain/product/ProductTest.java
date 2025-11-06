package sample.hhplus_w2.domain.product;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.*;

class ProductTest {

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
        Product product = Product.create(categoryId, name, brand, description, price, stockQty);

        // then
        assertThat(product).isNotNull();
        assertThat(product.getCategoryId()).isEqualTo(categoryId);
        assertThat(product.getName()).isEqualTo(name);
        assertThat(product.getBrand()).isEqualTo(brand);
        assertThat(product.getPrice()).isEqualTo(price);
        assertThat(product.getStockQty()).isEqualTo(stockQty);
        assertThat(product.getIsActive()).isTrue();
        assertThat(product.getVersion()).isEqualTo(0);
    }

    @Test
    @DisplayName("재고 증가 - 정상")
    void increaseStock() {
        // given
        Product product = Product.create(1L, "상품", "브랜드", "설명", new BigDecimal("10000"), 10);
        Integer increaseQty = 5;
        Integer expectedStock = 15;

        // when
        product.increaseStock(increaseQty);

        // then
        assertThat(product.getStockQty()).isEqualTo(expectedStock);
    }

    @Test
    @DisplayName("재고 감소 - 정상 (낙관적 락 성공)")
    void decreaseStock_Success() {
        // given
        Product product = Product.create(1L, "상품", "브랜드", "설명", new BigDecimal("10000"), 10);
        Integer decreaseQty = 5;
        Integer expectedVersion = 0;

        // when
        boolean result = product.decreaseStock(decreaseQty, expectedVersion);

        // then
        assertThat(result).isTrue();
        assertThat(product.getStockQty()).isEqualTo(5);
        assertThat(product.getVersion()).isEqualTo(1);
    }

    @Test
    @DisplayName("재고 감소 - 버전 불일치로 실패 (낙관적 락 충돌)")
    void decreaseStock_VersionMismatch() {
        // given
        Product product = Product.create(1L, "상품", "브랜드", "설명", new BigDecimal("10000"), 10);
        Integer decreaseQty = 5;
        Integer wrongVersion = 999;

        // when
        boolean result = product.decreaseStock(decreaseQty, wrongVersion);

        // then
        assertThat(result).isFalse();
        assertThat(product.getStockQty()).isEqualTo(10); // 재고 변경 없음
        assertThat(product.getVersion()).isEqualTo(0); // 버전 변경 없음
    }

    @Test
    @DisplayName("재고 감소 - 재고 부족으로 예외 발생")
    void decreaseStock_InsufficientStock() {
        // given
        Product product = Product.create(1L, "상품", "브랜드", "설명", new BigDecimal("10000"), 5);
        Integer decreaseQty = 10;

        // when & then
        assertThatThrownBy(() -> product.decreaseStock(decreaseQty, 0))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("재고가 부족합니다");
    }

    @Test
    @DisplayName("재고 확인 - 충분한 재고")
    void hasStock_Sufficient() {
        // given
        Product product = Product.create(1L, "상품", "브랜드", "설명", new BigDecimal("10000"), 10);

        // when & then
        assertThat(product.hasStock(5)).isTrue();
        assertThat(product.hasStock(10)).isTrue();
    }

    @Test
    @DisplayName("재고 확인 - 부족한 재고")
    void hasStock_Insufficient() {
        // given
        Product product = Product.create(1L, "상품", "브랜드", "설명", new BigDecimal("10000"), 5);

        // when & then
        assertThat(product.hasStock(10)).isFalse();
    }

    @Test
    @DisplayName("상품 정보 업데이트")
    void updateInfo() {
        // given
        Product product = Product.create(1L, "상품", "브랜드", "설명", new BigDecimal("10000"), 10);
        String newName = "새 상품명";
        String newBrand = "새 브랜드";
        String newDescription = "새 설명";
        BigDecimal newPrice = new BigDecimal("20000");

        // when
        product.updateInfo(newName, newBrand, newDescription, newPrice);

        // then
        assertThat(product.getName()).isEqualTo(newName);
        assertThat(product.getBrand()).isEqualTo(newBrand);
        assertThat(product.getDescription()).isEqualTo(newDescription);
        assertThat(product.getPrice()).isEqualTo(newPrice);
    }

    @Test
    @DisplayName("상품 활성화/비활성화")
    void activateAndDeactivate() {
        // given
        Product product = Product.create(1L, "상품", "브랜드", "설명", new BigDecimal("10000"), 10);

        // when
        product.deactivate();

        // then
        assertThat(product.getIsActive()).isFalse();

        // when
        product.activate();

        // then
        assertThat(product.getIsActive()).isTrue();
    }
}
