package sample.hhplus_w2.application.listener;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import sample.hhplus_w2.domain.order.OrderItem;
import sample.hhplus_w2.domain.order.event.OrderCompletedEvent;
import sample.hhplus_w2.service.ranking.ProductRankingService;

/**
 * 주문 이벤트 리스너
 * 주문 완료 이벤트를 처리하여 부가 로직을 원 트랜잭션과 분리
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderEventListener {

    private final ProductRankingService rankingService;

    /**
     * 주문 완료 이벤트 처리
     * - TransactionPhase.AFTER_COMMIT: 원 트랜잭션이 커밋된 이후에 실행
     * - @Async: 비동기로 실행하여 주문 처리 성능에 영향 없음
     * - Propagation.REQUIRES_NEW: 새로운 트랜잭션에서 실행 (원 트랜잭션과 독립적)
     */
    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void handleOrderCompleted(OrderCompletedEvent event) {
        log.info("주문 완료 이벤트 수신: orderId={}, userId={}", event.getOrderId(), event.getUserId());

        try {
            // 랭킹 업데이트: 주문 완료 시 상품별 주문 수량 증가
            for (OrderItem item : event.getOrderItems()) {
                rankingService.incrementProductOrder(item.getProductId(), item.getQty());
            }
            log.info("랭킹 업데이트 완료: orderId={}, items={}", event.getOrderId(), event.getOrderItems().size());
        } catch (Exception e) {
            // 랭킹 업데이트 실패해도 주문은 이미 완료된 상태
            // 별도 모니터링/재처리 로직 필요
            log.error("랭킹 업데이트 실패: orderId={}, error={}", event.getOrderId(), e.getMessage(), e);
        }
    }
}
