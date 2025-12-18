package sample.hhplus_w2.application.compensation;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sample.hhplus_w2.domain.order.ActorType;
import sample.hhplus_w2.domain.order.Order;
import sample.hhplus_w2.domain.order.OrderHistory;
import sample.hhplus_w2.domain.order.OrderItem;
import sample.hhplus_w2.domain.order.OrderStatus;
import sample.hhplus_w2.domain.product.Product;
import sample.hhplus_w2.repository.order.OrderHistoryRepository;
import sample.hhplus_w2.repository.order.OrderItemRepository;
import sample.hhplus_w2.repository.order.OrderRepository;
import sample.hhplus_w2.repository.product.ProductRepository;

import java.util.List;

/**
 * 주문 보상 트랜잭션 서비스
 *
 * 목적:
 * - 주문 처리 실패 시 이미 수행된 작업을 롤백
 * - 분산 트랜잭션 환경에서 데이터 일관성 보장
 *
 * 보상 시나리오:
 * 1. 재고 복구: 주문 취소 또는 결제 실패 시 차감된 재고 복원
 * 2. 주문 취소: 결제 실패 시 주문 상태를 CANCELLED로 변경
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderCompensationService {

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final OrderHistoryRepository orderHistoryRepository;
    private final ProductRepository productRepository;

    /**
     * 재고 복구 (주문 취소 또는 결제 실패 시)
     *
     * @param orderId 주문 ID
     */
    @Transactional
    public void restoreStock(Long orderId) {
        log.info("재고 복구 시작: orderId={}", orderId);

        List<OrderItem> items = orderItemRepository.findByOrderId(orderId);

        for (OrderItem item : items) {
            Product product = productRepository.findById(item.getProductId())
                    .orElseThrow(() -> new IllegalArgumentException("상품을 찾을 수 없습니다: " + item.getProductId()));

            // 재고 증가 (보상 작업)
            product.increaseStock(item.getQty());
            productRepository.save(product);

            log.info("재고 복구 완료: productId={}, qty={}, newStock={}",
                    item.getProductId(), item.getQty(), product.getStockQty());
        }

        log.info("전체 재고 복구 완료: orderId={}, items={}", orderId, items.size());
    }

    /**
     * 주문 취소 처리
     *
     * @param orderId 주문 ID
     * @param reason 취소 사유
     */
    @Transactional
    public void cancelOrder(Long orderId, String reason) {
        log.info("주문 취소 시작: orderId={}, reason={}", orderId, reason);

        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("주문을 찾을 수 없습니다: " + orderId));

        OrderStatus previousStatus = order.getStatus();

        // 주문 취소
        order.cancel();
        orderRepository.save(order);

        // 히스토리 기록
        OrderHistory history = OrderHistory.create(
                orderId,
                previousStatus,
                OrderStatus.CANCELLED,
                reason,
                ActorType.SYSTEM
        );
        orderHistoryRepository.save(history);

        log.info("주문 취소 완료: orderId={}, previousStatus={}", orderId, previousStatus);
    }

    /**
     * 주문 취소 및 재고 복구 (원자적 보상)
     *
     * @param orderId 주문 ID
     * @param reason 취소 사유
     */
    @Transactional
    public void cancelOrderWithStockRestore(Long orderId, String reason) {
        log.info("주문 취소 및 재고 복구 시작: orderId={}, reason={}", orderId, reason);

        try {
            // 1. 재고 복구
            restoreStock(orderId);

            // 2. 주문 취소
            cancelOrder(orderId, reason);

            log.info("주문 취소 및 재고 복구 성공: orderId={}", orderId);

        } catch (Exception e) {
            log.error("주문 취소 및 재고 복구 실패: orderId={}, error={}", orderId, e.getMessage(), e);
            throw new RuntimeException("보상 트랜잭션 실패: " + e.getMessage(), e);
        }
    }

    /**
     * 주문 만료 처리 (TTL 초과)
     *
     * @param orderId 주문 ID
     */
    @Transactional
    public void expireOrder(Long orderId) {
        log.info("주문 만료 처리 시작: orderId={}", orderId);

        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("주문을 찾을 수 없습니다: " + orderId));

        if (order.isExpired()) {
            order.expire();
            orderRepository.save(order);

            OrderHistory history = OrderHistory.create(
                    orderId,
                    OrderStatus.PENDING,
                    OrderStatus.EXPIRED,
                    "결제 시간 초과",
                    ActorType.SYSTEM
            );
            orderHistoryRepository.save(history);

            log.info("주문 만료 처리 완료: orderId={}", orderId);
        } else {
            log.warn("주문이 만료되지 않음: orderId={}", orderId);
        }
    }
}