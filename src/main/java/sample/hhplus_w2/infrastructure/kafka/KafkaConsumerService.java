package sample.hhplus_w2.infrastructure.kafka;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;
import sample.hhplus_w2.domain.order.event.OrderCompletedEvent;
import sample.hhplus_w2.service.ranking.ProductRankingService;

/**
 * Kafka Consumer 서비스
 *
 * 주문 완료 이벤트를 소비하여 비즈니스 로직 처리
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KafkaConsumerService {

    private final ProductRankingService rankingService;

    /**
     * 주문 완료 이벤트 소비
     *
     * @param event 주문 완료 이벤트
     * @param partition 파티션 번호
     * @param offset 오프셋
     * @param acknowledgment 수동 커밋을 위한 Acknowledgment
     */
    @KafkaListener(
            topics = "order-completed",
            groupId = "${spring.kafka.consumer.group-id}",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void consumeOrderCompletedEvent(
            @Payload OrderCompletedEvent event,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {

        log.info("Kafka 메시지 수신: topic=order-completed, partition={}, offset={}, orderId={}",
                partition, offset, event.getOrderId());

        try {
            // 주문 완료 이벤트 처리: 상품 랭킹 업데이트
            for (OrderCompletedEvent.OrderItemSnapshot item : event.getOrderItems()) {
                rankingService.incrementProductOrder(item.getProductId(), item.getQty());
                log.info("상품 랭킹 업데이트: productId={}, qty={}", item.getProductId(), item.getQty());
            }

            // 수동 커밋: 메시지 처리가 성공적으로 완료되었음을 Kafka에 알림
            acknowledgment.acknowledge();

            log.info("Kafka 메시지 처리 완료 및 커밋: orderId={}, partition={}, offset={}",
                    event.getOrderId(), partition, offset);

        } catch (Exception e) {
            log.error("Kafka 메시지 처리 실패: orderId={}, partition={}, offset={}, error={}",
                    event.getOrderId(), partition, offset, e.getMessage(), e);

            // 메시지 처리 실패 시 커밋하지 않음 -> 재처리됨
            // 필요시 DLQ(Dead Letter Queue)로 이동 로직 추가 가능
            throw new RuntimeException("Kafka 메시지 처리 실패", e);
        }
    }

    /**
     * 데이터 플랫폼 토픽 소비 (예시)
     *
     * 주문 정보를 외부 데이터 플랫폼으로 전송하기 위한 Consumer
     */
    @KafkaListener(
            topics = "data-platform",
            groupId = "${spring.kafka.consumer.group-id}",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void consumeDataPlatformEvent(
            @Payload OrderCompletedEvent event,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {

        log.info("데이터 플랫폼 메시지 수신: partition={}, offset={}, orderId={}",
                partition, offset, event.getOrderId());

        try {
            // TODO: 외부 데이터 플랫폼 API 호출
            // dataPlatformClient.sendOrderInfo(event);

            log.info("데이터 플랫폼 전송 성공 (Mock): orderId={}", event.getOrderId());

            acknowledgment.acknowledge();

        } catch (Exception e) {
            log.error("데이터 플랫폼 전송 실패: orderId={}, error={}", event.getOrderId(), e.getMessage(), e);
            throw new RuntimeException("데이터 플랫폼 전송 실패", e);
        }
    }
}