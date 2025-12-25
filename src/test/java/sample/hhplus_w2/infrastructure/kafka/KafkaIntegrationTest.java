package sample.hhplus_w2.infrastructure.kafka;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import sample.hhplus_w2.domain.order.event.OrderCompletedEvent;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Kafka Producer/Consumer 통합 테스트
 *
 * @EmbeddedKafka를 사용하여 실제 Kafka 없이 테스트 가능
 */
@SpringBootTest
@ActiveProfiles("test")
@DirtiesContext
@EmbeddedKafka(
        partitions = 3,
        topics = {"order-completed", "data-platform"},
        brokerProperties = {
                "listeners=PLAINTEXT://localhost:9093",
                "port=9093"
        }
)
class KafkaIntegrationTest {

    @Autowired
    private KafkaProducerService kafkaProducerService;

    @Test
    @DisplayName("Kafka Producer - 주문 완료 이벤트 발행 성공")
    void testPublishOrderCompletedEvent() throws Exception {
        // given
        OrderCompletedEvent event = OrderCompletedEvent.of(
                1L,
                100L,
                List.of(
                        new OrderCompletedEvent.OrderItemSnapshot(1L, 2),
                        new OrderCompletedEvent.OrderItemSnapshot(2L, 1)
                )
        );

        // when
        CompletableFuture<SendResult<String, Object>> future =
                kafkaProducerService.publishOrderCompletedEvent(event);

        SendResult<String, Object> result = future.get(10, TimeUnit.SECONDS);

        // then
        assertThat(result).isNotNull();
        assertThat(result.getRecordMetadata().topic()).isEqualTo("order-completed");
        assertThat(result.getRecordMetadata().partition()).isBetween(0, 2); // 3개 파티션 중 하나
        assertThat(result.getProducerRecord().key()).isEqualTo("1"); // orderId
    }

    @Test
    @DisplayName("Kafka Producer - 범용 메시지 발행 성공")
    void testPublishGenericMessage() throws Exception {
        // given
        String topic = "data-platform";
        String key = "test-key";
        OrderCompletedEvent event = OrderCompletedEvent.of(
                2L,
                200L,
                List.of(new OrderCompletedEvent.OrderItemSnapshot(3L, 5))
        );

        // when
        CompletableFuture<SendResult<String, Object>> future =
                kafkaProducerService.publish(topic, key, event);

        SendResult<String, Object> result = future.get(10, TimeUnit.SECONDS);

        // then
        assertThat(result).isNotNull();
        assertThat(result.getRecordMetadata().topic()).isEqualTo("data-platform");
        assertThat(result.getProducerRecord().key()).isEqualTo(key);
    }

    @Test
    @DisplayName("Kafka - 동일한 키는 동일한 파티션으로 전송됨")
    void testMessagePartitioning() throws Exception {
        // given
        String key = "same-key";
        OrderCompletedEvent event1 = OrderCompletedEvent.of(1L, 100L, List.of());
        OrderCompletedEvent event2 = OrderCompletedEvent.of(2L, 200L, List.of());

        // when
        CompletableFuture<SendResult<String, Object>> future1 =
                kafkaProducerService.publish("order-completed", key, event1);
        CompletableFuture<SendResult<String, Object>> future2 =
                kafkaProducerService.publish("order-completed", key, event2);

        SendResult<String, Object> result1 = future1.get(10, TimeUnit.SECONDS);
        SendResult<String, Object> result2 = future2.get(10, TimeUnit.SECONDS);

        // then
        assertThat(result1.getRecordMetadata().partition())
                .isEqualTo(result2.getRecordMetadata().partition());
    }
}