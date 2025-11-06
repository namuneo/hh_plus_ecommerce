package sample.hhplus_w2.domain.order;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.*;

class OrderItemTest {

    @Test
    @DisplayName("주문 아이템 생성 - 정상")
    void createOrderItem() {
        // given
        Long orderId = 1L;
        Long productId = 100L;
        Integer qty = 3;
        BigDecimal unitPrice = new BigDecimal("10000");

        // when
        OrderItem orderItem = OrderItem.create(orderId, productId, qty, unitPrice);

        // then
        assertThat(orderItem).isNotNull();
        assertThat(orderItem.getOrderId()).isEqualTo(orderId);
        assertThat(orderItem.getProductId()).isEqualTo(productId);
        assertThat(orderItem.getQty()).isEqualTo(qty);
        assertThat(orderItem.getUnitPrice()).isEqualTo(unitPrice);
        assertThat(orderItem.getSubtotal()).isEqualTo(new BigDecimal("30000"));
    }

    @Test
    @DisplayName("총 가격 계산 - 수량 * 단가")
    void calculateSubtotal() {
        // given
        BigDecimal unitPrice = new BigDecimal("15000");
        Integer qty = 5;

        // when
        OrderItem orderItem = OrderItem.create(1L, 1L, qty, unitPrice);

        // then
        assertThat(orderItem.getSubtotal()).isEqualTo(new BigDecimal("75000"));
    }
}
