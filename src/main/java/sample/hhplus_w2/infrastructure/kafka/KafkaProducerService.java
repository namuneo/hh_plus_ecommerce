package sample.hhplus_w2.infrastructure.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Service;
import sample.hhplus_w2.domain.coupon.event.CouponIssueRequestEvent;
import sample.hhplus_w2.domain.coupon.event.CouponIssueResultEvent;
import sample.hhplus_w2.domain.order.event.OrderCompletedEvent;

import java.util.concurrent.CompletableFuture;

/**
 * Kafka Producer 서비스
 *
 * 주문 완료 이벤트를 Kafka로 발행하는 역할
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KafkaProducerService {

    private static final String ORDER_COMPLETED_TOPIC = "order-completed";
    private static final String COUPON_ISSUE_REQUEST_TOPIC = "coupon-issue-request";
    private static final String COUPON_ISSUE_RESULT_TOPIC = "coupon-issue-result";

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final ObjectMapper objectMapper;

    /**
     * 주문 완료 이벤트를 Kafka로 발행
     *
     * @param event 주문 완료 이벤트
     * @return 발행 성공 여부를 담은 CompletableFuture
     */
    public CompletableFuture<SendResult<String, Object>> publishOrderCompletedEvent(OrderCompletedEvent event) {
        String key = String.valueOf(event.getOrderId());

        log.info("Kafka 메시지 발행 시작: topic={}, key={}, orderId={}",
                ORDER_COMPLETED_TOPIC, key, event.getOrderId());

        CompletableFuture<SendResult<String, Object>> future = kafkaTemplate.send(ORDER_COMPLETED_TOPIC, key, event);

        future.whenComplete((result, ex) -> {
            if (ex == null) {
                log.info("Kafka 메시지 발행 성공: topic={}, partition={}, offset={}, key={}",
                        result.getRecordMetadata().topic(),
                        result.getRecordMetadata().partition(),
                        result.getRecordMetadata().offset(),
                        key);
            } else {
                log.error("Kafka 메시지 발행 실패: topic={}, key={}, error={}",
                        ORDER_COMPLETED_TOPIC, key, ex.getMessage(), ex);
            }
        });

        return future;
    }

    /**
     * 쿠폰 발급 요청 이벤트 발행
     */
    public CompletableFuture<SendResult<String, Object>> publishCouponIssueRequest(CouponIssueRequestEvent event) {
        String key = String.valueOf(event.getCouponId());

        log.info("쿠폰 발급 요청 발행: requestId={}, couponId={}, userId={}",
                event.getRequestId(), event.getCouponId(), event.getUserId());

        CompletableFuture<SendResult<String, Object>> future =
                kafkaTemplate.send(COUPON_ISSUE_REQUEST_TOPIC, key, event);

        future.whenComplete((result, ex) -> {
            if (ex == null) {
                log.info("쿠폰 발급 요청 발행 성공: partition={}, offset={}",
                        result.getRecordMetadata().partition(),
                        result.getRecordMetadata().offset());
            } else {
                log.error("쿠폰 발급 요청 발행 실패: requestId={}, error={}",
                        event.getRequestId(), ex.getMessage(), ex);
            }
        });

        return future;
    }

    /**
     * 쿠폰 발급 결과 이벤트 발행
     */
    public CompletableFuture<SendResult<String, Object>> publishCouponIssueResult(CouponIssueResultEvent event) {
        String key = String.valueOf(event.getUserId());

        log.info("쿠폰 발급 결과 발행: requestId={}, status={}",
                event.getRequestId(), event.getStatus());

        CompletableFuture<SendResult<String, Object>> future =
                kafkaTemplate.send(COUPON_ISSUE_RESULT_TOPIC, key, event);

        future.whenComplete((result, ex) -> {
            if (ex == null) {
                log.info("쿠폰 발급 결과 발행 성공: partition={}, offset={}",
                        result.getRecordMetadata().partition(),
                        result.getRecordMetadata().offset());
            } else {
                log.error("쿠폰 발급 결과 발행 실패: requestId={}, error={}",
                        event.getRequestId(), ex.getMessage(), ex);
            }
        });

        return future;
    }

    /**
     * 범용 메시지 발행 메서드
     *
     * @param topic 토픽 이름
     * @param key 메시지 키
     * @param payload 메시지 페이로드
     * @return 발행 성공 여부를 담은 CompletableFuture
     */
    public CompletableFuture<SendResult<String, Object>> publish(String topic, String key, Object payload) {
        log.info("Kafka 메시지 발행 시작: topic={}, key={}", topic, key);

        CompletableFuture<SendResult<String, Object>> future = kafkaTemplate.send(topic, key, payload);

        future.whenComplete((result, ex) -> {
            if (ex == null) {
                log.info("Kafka 메시지 발행 성공: topic={}, partition={}, offset={}, key={}",
                        result.getRecordMetadata().topic(),
                        result.getRecordMetadata().partition(),
                        result.getRecordMetadata().offset(),
                        key);
            } else {
                log.error("Kafka 메시지 발행 실패: topic={}, key={}, error={}",
                        topic, key, ex.getMessage(), ex);
            }
        });

        return future;
    }
}