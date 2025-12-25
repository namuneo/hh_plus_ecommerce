# Step 17: Kafka 기초 학습 및 활용 - 구현 보고서

## 1. 구현 개요

Step 17에서는 Kafka의 기본 개념을 학습하고, 로컬 환경에 Kafka를 구성한 뒤, Spring Kafka를 활용하여 주문 완료 이벤트를 비동기 메시지로 발행/소비하는 시스템을 구현했습니다.

### 핵심 구현 목표
- ✅ Kafka 구성 요소 학습 및 문서화
- ✅ Docker Compose를 통한 로컬 Kafka 환경 구성
- ✅ Spring Kafka Producer/Consumer 구현
- ✅ 기존 Outbox 패턴과 Kafka 통합
- ✅ 주문 완료 후 Kafka 메시지 발행 (After Commit)
- ✅ 통합 테스트 작성

---

## 2. 아키텍처 설계

### 2.1 전체 흐름도

```
[주문 서비스]
    │
    ├── (1) 주문 결제 처리 (Transaction 1)
    │   ├── 재고 차감
    │   ├── 결제 정보 저장
    │   ├── 주문 상태 변경 (PAID)
    │   └── OutboxEvent 저장 (동일 트랜잭션)
    │
    └── (2) Kafka 메시지 발행 (Scheduled Worker)
        │
        ├── OutboxEventPublisher (5초마다 실행)
        │   ├── PENDING 상태 Outbox 이벤트 조회
        │   ├── Kafka로 메시지 발행
        │   └── Outbox 상태 → PUBLISHED
        │
        └── [Kafka Topic: order-completed]
            │
            ├── Partition 0
            ├── Partition 1
            └── Partition 2
                │
                └── (3) Kafka Consumer
                    ├── Consumer 1 (상품 랭킹 업데이트)
                    ├── Consumer 2 (데이터 플랫폼 전송)
                    └── Consumer 3 (기타 비즈니스 로직)
```

### 2.2 Transactional Outbox + Kafka 패턴

**왜 Outbox 패턴이 필요한가?**

```java
@Transactional
fun 주문_결제() {
    유저_포인트_차감();
    결제_정보_저장();
    주문_상태_변경();

    // 여기서 Kafka 발행하면?
    kafkaPublisher.publish();  // ❌ 문제 발생!

    // 트랜잭션 커밋 실패 시, 이미 Kafka에 메시지가 발행됨
    // → 존재하지 않는 주문 ID로 이벤트가 발행됨 (데이터 불일치)
}
```

**Outbox 패턴을 사용한 해결 방안:**

```java
@Transactional
fun 주문_결제() {
    유저_포인트_차감();
    결제_정보_저장();
    주문_상태_변경();

    // ✅ Outbox 저장 (동일 트랜잭션)
    outboxEventRepository.save(outboxEvent);
}

// ✅ 별도 Worker 프로세스
@Scheduled(fixedDelay = 5000)
fun publishPendingEvents() {
    List<OutboxEvent> pendingEvents = outboxEventRepository.findPendingEvents();
    for (event in pendingEvents) {
        kafkaPublisher.publish(event);  // Kafka 발행
        event.markAsPublished();
    }
}
```

**장점:**
1. **트랜잭션 원자성 보장**: 주문 데이터와 Outbox 이벤트가 함께 커밋되거나 함께 롤백됨
2. **메시지 발행 보장**: Outbox에 저장된 이벤트는 반드시 Kafka로 발행됨 (재시도 메커니즘)
3. **장애 복구**: 서버 장애 시에도 Outbox 테이블을 통해 미발행 이벤트 재발행 가능

---

## 3. 핵심 구현 내용

### 3.1 Kafka 환경 구성

#### Docker Compose 설정

**파일:** `docker-compose-kafka.yml`

```yaml
version: '3.8'

services:
  zookeeper:
    image: confluentinc/cp-zookeeper:7.5.0
    container_name: zookeeper
    environment:
      ZOOKEEPER_CLIENT_PORT: 2181
      ZOOKEEPER_TICK_TIME: 2000
    ports:
      - "2181:2181"

  kafka:
    image: confluentinc/cp-kafka:7.5.0
    container_name: kafka
    depends_on:
      - zookeeper
    ports:
      - "9092:9092"
    environment:
      KAFKA_BROKER_ID: 1
      KAFKA_ZOOKEEPER_CONNECT: zookeeper:2181
      KAFKA_ADVERTISED_LISTENERS: PLAINTEXT://localhost:9092
      KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR: 1
      KAFKA_AUTO_CREATE_TOPICS_ENABLE: 'true'
```

**실행 방법:**
```bash
docker-compose -f docker-compose-kafka.yml up -d
```

---

### 3.2 Spring Kafka 설정

#### application.yml 설정

```yaml
spring:
  kafka:
    bootstrap-servers: localhost:9092

    producer:
      key-serializer: org.apache.kafka.common.serialization.StringSerializer
      value-serializer: org.springframework.kafka.support.serializer.JsonSerializer
      acks: all  # 모든 replica가 메시지를 받았음을 확인
      retries: 3
      properties:
        enable.idempotence: true  # 중복 방지
        max.in.flight.requests.per.connection: 5

    consumer:
      group-id: ecommerce-consumer-group
      key-deserializer: org.apache.kafka.common.serialization.StringDeserializer
      value-deserializer: org.springframework.kafka.support.serializer.JsonDeserializer
      auto-offset-reset: earliest  # 처음부터 메시지 읽기
      enable-auto-commit: false  # 수동 커밋
      properties:
        spring.json.trusted.packages: "*"
        isolation.level: read_committed  # 커밋된 메시지만 읽기

    listener:
      ack-mode: manual  # 수동 커밋 모드
```

**핵심 설정 설명:**
- `acks: all`: Producer가 메시지를 발행할 때, 모든 replica가 메시지를 받았음을 확인 (최고 수준의 안정성)
- `enable.idempotence: true`: 네트워크 재전송 시에도 중복 메시지 방지
- `enable-auto-commit: false`: Consumer가 메시지 처리 완료 후 수동으로 커밋 (메시지 처리 보장)
- `isolation.level: read_committed`: 트랜잭션이 커밋된 메시지만 읽기

---

### 3.3 Kafka Producer 구현

**파일:** `src/main/java/sample/hhplus_w2/infrastructure/kafka/KafkaProducerService.java`

```java
@Slf4j
@Service
@RequiredArgsConstructor
public class KafkaProducerService {

    private static final String ORDER_COMPLETED_TOPIC = "order-completed";
    private final KafkaTemplate<String, Object> kafkaTemplate;

    /**
     * 주문 완료 이벤트를 Kafka로 발행
     */
    public CompletableFuture<SendResult<String, Object>> publishOrderCompletedEvent(
            OrderCompletedEvent event) {
        String key = String.valueOf(event.getOrderId());

        log.info("Kafka 메시지 발행 시작: topic={}, key={}, orderId={}",
                ORDER_COMPLETED_TOPIC, key, event.getOrderId());

        CompletableFuture<SendResult<String, Object>> future =
                kafkaTemplate.send(ORDER_COMPLETED_TOPIC, key, event);

        future.whenComplete((result, ex) -> {
            if (ex == null) {
                log.info("Kafka 메시지 발행 성공: partition={}, offset={}",
                        result.getRecordMetadata().partition(),
                        result.getRecordMetadata().offset());
            } else {
                log.error("Kafka 메시지 발행 실패: error={}", ex.getMessage());
            }
        });

        return future;
    }
}
```

**핵심 포인트:**
- 메시지 키로 `orderId` 사용 → 동일 주문의 이벤트는 동일 파티션으로 전송 (순서 보장)
- CompletableFuture 반환 → 비동기 처리
- 로깅을 통한 발행 성공/실패 추적

---

### 3.4 Kafka Consumer 구현

**파일:** `src/main/java/sample/hhplus_w2/infrastructure/kafka/KafkaConsumerService.java`

```java
@Slf4j
@Service
@RequiredArgsConstructor
public class KafkaConsumerService {

    private final ProductRankingService rankingService;

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

        log.info("Kafka 메시지 수신: partition={}, offset={}, orderId={}",
                partition, offset, event.getOrderId());

        try {
            // 주문 완료 이벤트 처리: 상품 랭킹 업데이트
            for (OrderCompletedEvent.OrderItemSnapshot item : event.getOrderItems()) {
                rankingService.incrementProductOrder(item.getProductId(), item.getQty());
            }

            // 수동 커밋: 메시지 처리가 성공적으로 완료되었음을 Kafka에 알림
            acknowledgment.acknowledge();

            log.info("Kafka 메시지 처리 완료: orderId={}", event.getOrderId());

        } catch (Exception e) {
            log.error("Kafka 메시지 처리 실패: orderId={}, error={}",
                    event.getOrderId(), e.getMessage());
            // 메시지 처리 실패 시 커밋하지 않음 → 재처리됨
            throw new RuntimeException("Kafka 메시지 처리 실패", e);
        }
    }
}
```

**핵심 포인트:**
- `@KafkaListener`: 특정 토픽의 메시지를 자동으로 소비
- **수동 커밋 (Acknowledgment)**: 메시지 처리가 성공적으로 완료된 후에만 커밋
- 처리 실패 시 커밋하지 않음 → 메시지 재처리
- 파티션 및 오프셋 정보 로깅 → 추적성 확보

---

### 3.5 Outbox 패턴과 Kafka 통합

**파일:** `src/main/java/sample/hhplus_w2/application/outbox/OutboxEventPublisher.java`

```java
@Slf4j
@Service
@RequiredArgsConstructor
public class OutboxEventPublisher {

    private static final int MAX_RETRIES = 5;
    private final OutboxEventRepository outboxEventRepository;
    private final KafkaProducerService kafkaProducerService;

    /**
     * 대기 중인 Outbox 이벤트를 Kafka로 발행
     * 5초마다 실행
     */
    @Scheduled(fixedDelay = 5000)
    public void publishPendingEvents() {
        List<OutboxEvent> pendingEvents = outboxEventRepository.findPendingEvents(MAX_RETRIES);

        for (OutboxEvent event : pendingEvents) {
            try {
                publishEvent(event);
            } catch (Exception e) {
                log.error("Outbox 이벤트 처리 실패: eventId={}", event.getId(), e);
            }
        }
    }

    @Transactional
    protected void publishEvent(OutboxEvent outboxEvent) {
        try {
            Object domainEvent = deserializeEvent(outboxEvent);

            // Kafka로 메시지 발행
            CompletableFuture<?> future = publishToKafka(outboxEvent.getEventType(), domainEvent);
            future.get(); // 동기 대기

            // 발행 성공 처리
            outboxEvent.markAsPublished();
            outboxEventRepository.save(outboxEvent);

        } catch (Exception e) {
            outboxEvent.incrementRetryCount();

            if (outboxEvent.getRetryCount() >= MAX_RETRIES) {
                outboxEvent.moveToDLQ();
                log.error("최대 재시도 초과, DLQ 이동: eventId={}", outboxEvent.getId());
            }

            outboxEventRepository.save(outboxEvent);
            throw new RuntimeException("Kafka 메시지 발행 실패", e);
        }
    }

    private CompletableFuture<?> publishToKafka(String eventType, Object domainEvent) {
        return switch (eventType) {
            case "OrderCompleted" -> kafkaProducerService.publishOrderCompletedEvent(
                    (OrderCompletedEvent) domainEvent);
            default -> throw new IllegalArgumentException("지원하지 않는 이벤트 타입: " + eventType);
        };
    }
}
```

**핵심 포인트:**
1. **Scheduled Worker**: 5초마다 PENDING 상태의 Outbox 이벤트 조회
2. **재시도 메커니즘**: 최대 5회 재시도, 실패 시 DLQ로 이동
3. **동기 발행**: `future.get()`으로 Kafka 발행 성공 확인
4. **상태 관리**: PENDING → PUBLISHED / FAILED / DLQ

---

## 4. Topic 및 Partition 설계

**파일:** `src/main/java/sample/hhplus_w2/config/KafkaConfig.java`

```java
@EnableKafka
@Configuration
public class KafkaConfig {

    @Bean
    public NewTopic orderCompletedTopic() {
        return TopicBuilder.name("order-completed")
                .partitions(3)  // 3개 파티션 (병렬 처리)
                .replicas(1)    // 단일 브로커 환경
                .build();
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, OrderCompletedEvent>
            kafkaListenerContainerFactory() {
        ConcurrentKafkaListenerContainerFactory<String, OrderCompletedEvent> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory());
        factory.setConcurrency(3);  // 파티션 수와 동일
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL);
        return factory;
    }
}
```

**설계 이유:**
- **3개 파티션**: 3개의 Consumer 인스턴스가 병렬로 메시지 처리 가능
- **Concurrency 3**: 각 파티션당 1개의 Consumer 스레드 할당
- **수동 커밋**: 메시지 처리 완료 후 명시적으로 커밋 (at-least-once 보장)

---

## 5. 테스트 전략

### 5.1 Kafka 통합 테스트

**파일:** `src/test/java/sample/hhplus_w2/infrastructure/kafka/KafkaIntegrationTest.java`

```java
@SpringBootTest
@ActiveProfiles("test")
@DirtiesContext
@EmbeddedKafka(
        partitions = 3,
        topics = {"order-completed"},
        brokerProperties = {"listeners=PLAINTEXT://localhost:9093", "port=9093"}
)
class KafkaIntegrationTest {

    @Test
    @DisplayName("Kafka Producer - 주문 완료 이벤트 발행 성공")
    void testPublishOrderCompletedEvent() throws Exception {
        // given
        OrderCompletedEvent event = OrderCompletedEvent.of(1L, 100L, List.of(...));

        // when
        CompletableFuture<SendResult<String, Object>> future =
                kafkaProducerService.publishOrderCompletedEvent(event);
        SendResult<String, Object> result = future.get(10, TimeUnit.SECONDS);

        // then
        assertThat(result.getRecordMetadata().topic()).isEqualTo("order-completed");
        assertThat(result.getProducerRecord().key()).isEqualTo("1");
    }

    @Test
    @DisplayName("Kafka - 동일한 키는 동일한 파티션으로 전송됨")
    void testMessagePartitioning() throws Exception {
        // given
        String key = "same-key";

        // when
        SendResult<String, Object> result1 = kafkaProducerService.publish("order-completed", key, event1).get();
        SendResult<String, Object> result2 = kafkaProducerService.publish("order-completed", key, event2).get();

        // then
        assertThat(result1.getRecordMetadata().partition())
                .isEqualTo(result2.getRecordMetadata().partition());
    }
}
```

**테스트 결과:**
```
✅ Kafka Producer - 주문 완료 이벤트 발행 성공
✅ Kafka Producer - 범용 메시지 발행 성공
✅ Kafka - 동일한 키는 동일한 파티션으로 전송됨

BUILD SUCCESSFUL
```

---

## 6. 구현 결과 및 성과

### 6.1 달성 목표

| 목표 | 상태 | 비고 |
|------|------|------|
| Kafka 기초 개념 학습 및 문서화 | ✅ | `docs/step17-kafka-basics.md` 작성 |
| Docker Compose로 Kafka 환경 구성 | ✅ | ZooKeeper + Kafka 구성 완료 |
| Spring Kafka 의존성 추가 및 설정 | ✅ | Producer/Consumer 설정 완료 |
| Kafka Producer 구현 | ✅ | KafkaProducerService 구현 |
| Kafka Consumer 구현 | ✅ | KafkaConsumerService 구현 |
| Outbox 패턴과 Kafka 통합 | ✅ | OutboxEventPublisher 수정 |
| 주문 완료 후 Kafka 메시지 발행 | ✅ | After Commit 방식 적용 |
| 통합 테스트 작성 | ✅ | KafkaIntegrationTest 작성 및 성공 |

### 6.2 핵심 성과

1. **메시지 발행 보장**
   - Outbox 패턴으로 트랜잭션 원자성 보장
   - 재시도 메커니즘으로 일시적 장애 대응
   - DLQ를 통한 실패 이벤트 추적

2. **비동기 처리 분리**
   - 주문 처리와 랭킹 업데이트 트랜잭션 분리
   - 주문 서비스의 성능 향상 (랭킹 업데이트 지연이 주문에 영향 없음)

3. **확장 가능한 아키텍처**
   - 3개 파티션으로 병렬 처리 지원
   - Consumer Group을 통한 수평 확장 가능

4. **안정성**
   - 수동 커밋으로 at-least-once 보장
   - Idempotence 설정으로 중복 메시지 방지
   - `acks: all`로 메시지 유실 방지

---

## 7. 개선 사항 및 Next Steps

### 7.1 현재 시스템의 한계

1. **Consumer 중복 처리 방지 부재**
   - 현재는 at-least-once만 보장
   - 동일 메시지 재처리 시 중복 처리 가능성

2. **DLQ 처리 로직 미구현**
   - 최대 재시도 초과 시 DLQ로 이동만 함
   - DLQ 이벤트에 대한 모니터링 및 복구 로직 필요

3. **단일 브로커 환경**
   - Replica가 1개 (고가용성 부족)
   - 프로덕션 환경에서는 최소 3개 Broker 필요

### 7.2 Step 18에서 개선할 사항

1. **중복 처리 방지 (Idempotent Consumer)**
   - Consumer 측에서 처리 이력 테이블 구축
   - 메시지 ID 기반 중복 체크

2. **Kafka를 활용한 비즈니스 프로세스 개선**
   - 선착순 쿠폰 발급: Kafka 기반 순차 처리
   - 콘서트 대기열: Kafka Queue 기반 공정한 대기열

3. **파티션 전략 최적화**
   - 쿠폰 발급: 파티션 1개 (순차 보장)
   - 랭킹 업데이트: 파티션 3개 (병렬 처리)

---

## 8. 참고 자료

- **Kafka 공식 문서**: https://kafka.apache.org/documentation/
- **Spring Kafka 문서**: https://spring.io/projects/spring-kafka
- **Transactional Outbox 패턴**: https://microservices.io/patterns/data/transactional-outbox.html

---

## 9. 결론

Step 17에서는 Kafka의 기본 개념을 학습하고, Spring Kafka를 활용하여 주문 완료 이벤트를 비동기 메시지로 발행/소비하는 시스템을 성공적으로 구현했습니다.

Outbox 패턴과 Kafka를 통합함으로써:
- 트랜잭션 원자성 보장
- 메시지 발행 신뢰성 확보
- 비동기 처리를 통한 성능 향상
- 확장 가능한 아키텍처 구축

을 달성했습니다.

Step 18에서는 Kafka의 파티션 전략, 순차/병렬 처리 전략을 활용하여 선착순 쿠폰 발급과 콘서트 대기열 시스템을 개선할 예정입니다.
