package sample.hhplus_w2.domain.cart;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.*;

class CartItemTest {

    @Test
    @DisplayName("장바구니 아이템 생성 - 정상")
    void createCartItem() {
        // given
        Long cartId = 1L;
        Long productId = 100L;
        Integer qty = 2;
        BigDecimal unitPrice = new BigDecimal("10000");

        // when
        CartItem cartItem = CartItem.create(cartId, productId, qty, unitPrice);

        // then
        assertThat(cartItem).isNotNull();
        assertThat(cartItem.getCartId()).isEqualTo(cartId);
        assertThat(cartItem.getProductId()).isEqualTo(productId);
        assertThat(cartItem.getQty()).isEqualTo(qty);
        assertThat(cartItem.getUnitPriceSnapshot()).isEqualTo(unitPrice);
        assertThat(cartItem.getTotalPrice()).isEqualTo(new BigDecimal("20000"));
    }

    @Test
    @DisplayName("수량 증가")
    void increaseQuantity() {
        // given
        CartItem cartItem = CartItem.create(1L, 1L, 2, new BigDecimal("10000"));

        // when
        cartItem.increaseQuantity(3);

        // then
        assertThat(cartItem.getQty()).isEqualTo(5);
        assertThat(cartItem.getTotalPrice()).isEqualTo(new BigDecimal("50000"));
    }

    @Test
    @DisplayName("수량 변경")
    void changeQuantity() {
        // given
        CartItem cartItem = CartItem.create(1L, 1L, 5, new BigDecimal("10000"));

        // when
        cartItem.changeQuantity(3);

        // then
        assertThat(cartItem.getQty()).isEqualTo(3);
        assertThat(cartItem.getTotalPrice()).isEqualTo(new BigDecimal("30000"));
    }

    @Test
    @DisplayName("수량 변경 - 0 이하로 예외 발생")
    void changeQuantity_InvalidQuantity() {
        // given
        CartItem cartItem = CartItem.create(1L, 1L, 5, new BigDecimal("10000"));

        // when & then
        assertThatThrownBy(() -> cartItem.changeQuantity(0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("수량은 1 이상이어야 합니다");
    }

    @Test
    @DisplayName("가격 스냅샷 업데이트")
    void updatePriceSnapshot() {
        // given
        CartItem cartItem = CartItem.create(1L, 1L, 2, new BigDecimal("10000"));
        BigDecimal newPrice = new BigDecimal("15000");

        // when
        cartItem.updatePriceSnapshot(newPrice);

        // then
        assertThat(cartItem.getUnitPriceSnapshot()).isEqualTo(newPrice);
        assertThat(cartItem.getTotalPrice()).isEqualTo(new BigDecimal("30000"));
    }

    @Test
    @DisplayName("총 가격 계산")
    void calculateTotalPrice() {
        // given
        Integer qty = 4;
        BigDecimal unitPrice = new BigDecimal("12500");

        // when
        CartItem cartItem = CartItem.create(1L, 1L, qty, unitPrice);

        // then
        assertThat(cartItem.getTotalPrice()).isEqualTo(new BigDecimal("50000"));
    }
}
