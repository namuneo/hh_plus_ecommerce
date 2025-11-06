package sample.hhplus_w2.domain.order;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.*;

class OrderTest {

    @Test
    @DisplayName("주문 생성 - 정상")
    void createOrder() {
        // given
        Long userId = 1L;
        BigDecimal totalAmount = new BigDecimal("50000");
        BigDecimal shippingFee = new BigDecimal("3000");
        Integer ttlMinutes = 30;

        // when
        Order order = Order.create(userId, totalAmount, shippingFee, ttlMinutes);

        // then
        assertThat(order).isNotNull();
        assertThat(order.getUserId()).isEqualTo(userId);
        assertThat(order.getTotal()).isEqualTo(totalAmount);
        assertThat(order.getDiscountTotal()).isEqualTo(BigDecimal.ZERO);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING);
        assertThat(order.getExpiresAt()).isAfter(LocalDateTime.now());
    }

    @Test
    @DisplayName("주문 만료 체크 - 만료되지 않음")
    void isExpired_NotExpired() {
        // given
        Order order = Order.create(1L, new BigDecimal("10000"), BigDecimal.ZERO, 30);

        // when & then
        assertThat(order.isExpired()).isFalse();
    }

    @Test
    @DisplayName("주문 결제 완료 처리")
    void markAsPaid() {
        // given
        Order order = Order.create(1L, new BigDecimal("10000"), BigDecimal.ZERO, 30);

        // when
        order.markAsPaid();

        // then
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PAID);
    }

    @Test
    @DisplayName("주문 만료 처리")
    void expire() {
        // given
        Order order = Order.create(1L, new BigDecimal("10000"), BigDecimal.ZERO, 30);

        // when
        order.expire();

        // then
        assertThat(order.getStatus()).isEqualTo(OrderStatus.EXPIRED);
    }

    @Test
    @DisplayName("주문 취소 처리")
    void cancel() {
        // given
        Order order = Order.create(1L, new BigDecimal("10000"), BigDecimal.ZERO, 30);

        // when
        order.cancel();

        // then
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
    }

    @Test
    @DisplayName("최종 금액 계산 - 배송비 포함")
    void calculateFinalAmount() {
        // given
        BigDecimal totalAmount = new BigDecimal("100000");
        BigDecimal shippingFee = new BigDecimal("3000");

        // when
        Order order = Order.create(1L, totalAmount, shippingFee, 30);

        // then
        assertThat(order.getFinalAmount()).isEqualTo(new BigDecimal("103000"));
    }
}
