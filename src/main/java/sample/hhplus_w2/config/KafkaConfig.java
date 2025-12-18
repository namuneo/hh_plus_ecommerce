package sample.hhplus_w2.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import sample.hhplus_w2.domain.order.event.OrderCompletedEvent;

import java.util.HashMap;
import java.util.Map;

/**
 * Kafka 설정
 *
 * Topic 생성 및 Kafka 기본 설정
 */
@EnableKafka
@Configuration
public class KafkaConfig {

    private static final String ORDER_COMPLETED_TOPIC = "order-completed";
    private static final String DATA_PLATFORM_TOPIC = "data-platform";

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    @Value("${spring.kafka.consumer.group-id}")
    private String groupId;

    private static final String COUPON_ISSUE_REQUEST_TOPIC = "coupon-issue-request";
    private static final String COUPON_ISSUE_RESULT_TOPIC = "coupon-issue-result";

    /**
     * 주문 완료 토픽
     *
     * - partitions: 3 (병렬 처리)
     * - replicas: 1 (단일 브로커 환경)
     */
    @Bean
    public NewTopic orderCompletedTopic() {
        return TopicBuilder.name(ORDER_COMPLETED_TOPIC)
                .partitions(3)
                .replicas(1)
                .build();
    }

    /**
     * 데이터 플랫폼 토픽
     *
     * - partitions: 3 (병렬 처리)
     * - replicas: 1 (단일 브로커 환경)
     */
    @Bean
    public NewTopic dataPlatformTopic() {
        return TopicBuilder.name(DATA_PLATFORM_TOPIC)
                .partitions(3)
                .replicas(1)
                .build();
    }

    /**
     * 쿠폰 발급 요청 토픽
     *
     * - partitions: 5 (병렬 처리 - 높은 처리량)
     * - replicas: 1 (단일 브로커 환경)
     */
    @Bean
    public NewTopic couponIssueRequestTopic() {
        return TopicBuilder.name(COUPON_ISSUE_REQUEST_TOPIC)
                .partitions(5)
                .replicas(1)
                .build();
    }

    /**
     * 쿠폰 발급 결과 토픽
     *
     * - partitions: 3 (병렬 처리)
     * - replicas: 1 (단일 브로커 환경)
     */
    @Bean
    public NewTopic couponIssueResultTopic() {
        return TopicBuilder.name(COUPON_ISSUE_RESULT_TOPIC)
                .partitions(3)
                .replicas(1)
                .build();
    }

    /**
     * Consumer Factory 설정
     */
    @Bean
    public ConsumerFactory<String, OrderCompletedEvent> consumerFactory() {
        Map<String, Object> config = new HashMap<>();
        config.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        config.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
        config.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        config.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, JsonDeserializer.class);
        config.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        config.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
        config.put(JsonDeserializer.TRUSTED_PACKAGES, "*");
        config.put(ConsumerConfig.ISOLATION_LEVEL_CONFIG, "read_committed");

        return new DefaultKafkaConsumerFactory<>(
                config,
                new StringDeserializer(),
                new JsonDeserializer<>(OrderCompletedEvent.class, false)
        );
    }

    /**
     * Kafka Listener Container Factory 설정
     *
     * - 수동 커밋 모드 (MANUAL)
     * - 동시성: 3 (파티션 수와 동일)
     */
    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, OrderCompletedEvent> kafkaListenerContainerFactory() {
        ConcurrentKafkaListenerContainerFactory<String, OrderCompletedEvent> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory());
        factory.setConcurrency(3); // 파티션 수와 동일하게 설정
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL); // 수동 커밋
        return factory;
    }
}