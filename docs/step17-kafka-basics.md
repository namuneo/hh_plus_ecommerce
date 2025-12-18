# Step 17: Kafka 기초 학습 및 활용

## 1. Kafka 핵심 개념

### 1.1 Apache Kafka란?

Apache Kafka는 **분산 스트리밍 플랫폼**으로, 대용량의 실시간 데이터 스트림을 안정적으로 처리하기 위해 LinkedIn에서 개발되어 Apache 재단에 오픈소스로 기증된 메시지 큐 시스템입니다.

**핵심 특징:**
- **높은 처리량 (High Throughput)**: 초당 수백만 건의 메시지 처리 가능
- **확장성 (Scalability)**: 브로커, 파티션 추가로 수평 확장 가능
- **내구성 (Durability)**: 디스크에 메시지 저장, 장애 시에도 데이터 보존
- **분산 처리 (Distributed)**: 여러 서버에 분산되어 고가용성 보장
- **순서 보장 (Ordering)**: 파티션 내에서 메시지 순서 보장

---

## 2. Kafka 구성 요소

### 2.1 Broker (브로커)

**정의**: Kafka 서버. 메시지를 저장하고 전달하는 역할을 수행하는 물리적/논리적 서버 인스턴스

**특징:**
- Kafka 클러스터는 하나 이상의 브로커로 구성
- 각 브로커는 고유한 ID를 가짐
- 브로커 간 데이터 복제를 통해 고가용성 제공

**역할:**
- Topic의 Partition을 저장
- Producer로부터 메시지 수신
- Consumer에게 메시지 전달
- Partition Leader/Follower 관리

**예시:**
```
Kafka Cluster
├─ Broker 1 (ID: 1)
│  ├─ Topic A - Partition 0 (Leader)
│  └─ Topic B - Partition 1 (Follower)
├─ Broker 2 (ID: 2)
│  ├─ Topic A - Partition 1 (Leader)
│  └─ Topic B - Partition 0 (Follower)
└─ Broker 3 (ID: 3)
   ├─ Topic A - Partition 2 (Leader)
   └─ Topic B - Partition 2 (Follower)
```

---

### 2.2 Topic (토픽)

**정의**: 메시지가 저장되는 논리적인 카테고리. 데이터베이스의 테이블과 유사한 개념

**특징:**
- 고유한 이름으로 식별
- 여러 파티션으로 구성
- 메시지는 Topic에 발행되고 Topic으로부터 소비됨

**명명 규칙 예시:**
```
order-created        # 주문 생성 이벤트
order-completed      # 주문 완료 이벤트
coupon-issued        # 쿠폰 발급 이벤트
payment-processed    # 결제 처리 이벤트
```

**토픽 설정:**
- `retention.ms`: 메시지 보관 시간 (기본: 7일)
- `cleanup.policy`: 메시지 삭제 정책 (delete, compact)
- `replication.factor`: 복제본 개수 (최소 2 이상 권장)

---

### 2.3 Partition (파티션)

**정의**: Topic을 물리적으로 나눈 단위. 병렬 처리와 확장성의 핵심

**특징:**
- 각 파티션은 순서가 보장되는 불변(immutable) 로그
- 파티션 번호는 0부터 시작 (0-indexed)
- 각 메시지는 파티션 내에서 고유한 Offset을 가짐

**Offset**: 파티션 내 메시지의 위치를 나타내는 일련번호
```
Partition 0:  [0] [1] [2] [3] [4] [5] ...
Partition 1:  [0] [1] [2] [3] ...
Partition 2:  [0] [1] [2] [3] [4] [5] [6] [7] ...
```

**파티션 수와 성능:**
- 파티션 수 ↑ = 병렬 처리 ↑ = 처리량 ↑
- 파티션 수 > Consumer 수: 일부 Consumer가 여러 파티션 처리
- 파티션 수 < Consumer 수: 일부 Consumer는 유휴 상태

**파티션 분배 전략:**
1. **Round-robin**: 메시지 키가 없을 때 순차적으로 분배
2. **Key-based**: 메시지 키의 해시값을 기준으로 분배 (동일 키 → 동일 파티션)
3. **Custom**: 사용자 정의 파티셔너

---

### 2.4 Producer (프로듀서)

**정의**: Kafka에 메시지를 발행하는 애플리케이션 또는 클라이언트

**주요 동작:**
1. 메시지 생성
2. Topic 지정
3. 메시지 키 설정 (선택적)
4. 파티션 선택 (키 기반 또는 라운드 로빈)
5. Broker에 메시지 전송

**Producer 설정:**
```properties
# 브로커 주소
bootstrap.servers=localhost:9092

# 메시지 직렬화
key.serializer=org.apache.kafka.common.serialization.StringSerializer
value.serializer=org.apache.kafka.common.serialization.StringSerializer

# Ack 설정 (신뢰성)
acks=all  # all, 1, 0
# all: 모든 복제본 확인 (가장 안전, 느림)
# 1: Leader만 확인 (균형)
# 0: 확인 안 함 (빠름, 손실 가능)

# 재시도
retries=3
retry.backoff.ms=1000

# 배치 처리
batch.size=16384
linger.ms=10

# 압축
compression.type=snappy
```

**메시지 키의 역할:**
- **동일한 키 → 동일한 파티션**: 순서 보장 필요 시 사용
- 예: `userId`, `orderId`, `couponId` 등

---

### 2.5 Consumer (컨슈머)

**정의**: Kafka로부터 메시지를 읽어오는 애플리케이션 또는 클라이언트

**Consumer Group**:
- 여러 Consumer가 하나의 그룹을 형성
- 동일 그룹 내 Consumer들은 파티션을 나눠서 처리
- 각 파티션은 그룹 내 하나의 Consumer에게만 할당

**예시:**
```
Topic: order-created (3 Partitions)

Consumer Group A:
  Consumer A-1 → Partition 0
  Consumer A-2 → Partition 1
  Consumer A-3 → Partition 2

Consumer Group B:
  Consumer B-1 → Partition 0, 1, 2
```

**Consumer 설정:**
```properties
# 브로커 주소
bootstrap.servers=localhost:9092

# Consumer Group ID
group.id=order-consumer-group

# 메시지 역직렬화
key.deserializer=org.apache.kafka.common.serialization.StringDeserializer
value.deserializer=org.apache.kafka.common.serialization.StringDeserializer

# Offset 커밋 전략
enable.auto.commit=false
auto.commit.interval.ms=5000

# Offset 초기 위치
auto.offset.reset=earliest  # earliest, latest, none
# earliest: 가장 오래된 메시지부터
# latest: 가장 최신 메시지부터
# none: Consumer Group이 없으면 예외 발생

# 최대 폴링 레코드 수
max.poll.records=500
```

**Offset Commit:**
- **자동 커밋**: `enable.auto.commit=true` (간단하지만 중복 처리 가능)
- **수동 커밋**: `enable.auto.commit=false` (정확하지만 복잡)
  - `commitSync()`: 동기 커밋 (블로킹)
  - `commitAsync()`: 비동기 커밋 (Non-blocking)

---

### 2.6 ZooKeeper (주키퍼) vs KRaft

**ZooKeeper (전통적 방식):**
- Kafka 클러스터의 메타데이터 관리
- Broker 상태 추적, 리더 선출
- Kafka 3.x 이전까지 필수 의존성

**KRaft (Kafka Raft - 새로운 방식):**
- ZooKeeper 없이 Kafka 자체적으로 메타데이터 관리
- Kafka 3.3 이상에서 프로덕션 사용 가능
- 더 간단한 아키텍처, 빠른 리더 선출

**본 프로젝트**: Docker Compose로 ZooKeeper + Kafka 구성 사용

---

## 3. Kafka 데이터 흐름

### 3.1 메시지 발행 흐름

```
[Producer]
    ↓ (1) 메시지 생성
[Serializer]
    ↓ (2) 직렬화 (Object → Byte Array)
[Partitioner]
    ↓ (3) 파티션 선택 (키 기반 또는 라운드 로빈)
[Kafka Broker]
    ↓ (4) Leader Partition에 저장
[Replication]
    ↓ (5) Follower Partition에 복제
[ACK]
    ↓ (6) Producer에게 응답 (acks 설정에 따라)
[Producer]
    ✅ 완료 또는 재시도
```

### 3.2 메시지 소비 흐름

```
[Consumer]
    ↓ (1) Subscribe Topic
[Kafka Broker]
    ↓ (2) Partition 할당 (Rebalancing)
[Consumer]
    ↓ (3) Poll (Fetch) 메시지
[Deserializer]
    ↓ (4) 역직렬화 (Byte Array → Object)
[Business Logic]
    ↓ (5) 메시지 처리
[Offset Commit]
    ↓ (6) Offset 커밋 (처리 완료 표시)
[Kafka Broker]
    ✅ Offset 저장 (__consumer_offsets Topic)
```

---

## 4. Producer, Partition, Consumer 수에 따른 데이터 흐름

### 시나리오 1: Producer 1, Partition 3, Consumer 1

```
Producer 1
    ↓
Topic: order-created (3 Partitions)
    ├─ Partition 0: [M1, M4, M7]
    ├─ Partition 1: [M2, M5, M8]
    └─ Partition 2: [M3, M6, M9]
    ↓
Consumer Group A
    └─ Consumer 1 → 모든 파티션 처리 (0, 1, 2)

특징:
- 병렬 발행 가능 (Producer는 각 파티션에 비동기로 발행)
- Consumer는 순차 처리 (단일 Consumer가 모든 파티션 처리)
- 처리 속도: 느림 (Consumer가 병목)
```

### 시나리오 2: Producer 3, Partition 3, Consumer 3

```
Producer 1 ─┐
Producer 2 ─┼─→ Topic: order-created (3 Partitions)
Producer 3 ─┘       ├─ Partition 0: [M1, M4, M7]
                    ├─ Partition 1: [M2, M5, M8]
                    └─ Partition 2: [M3, M6, M9]
                    ↓
Consumer Group A
    ├─ Consumer 1 → Partition 0
    ├─ Consumer 2 → Partition 1
    └─ Consumer 3 → Partition 2

특징:
- Producer 병렬 발행 (3배 빠름)
- Consumer 병렬 처리 (3배 빠름)
- 최적의 균형 (Partition 수 = Consumer 수)
- 처리 속도: 빠름
```

### 시나리오 3: Producer 1, Partition 1, Consumer 3

```
Producer 1
    ↓
Topic: order-queue (1 Partition)
    └─ Partition 0: [M1, M2, M3, M4, M5, M6]
    ↓
Consumer Group A
    ├─ Consumer 1 → Partition 0 (활성)
    ├─ Consumer 2 → (유휴)
    └─ Consumer 3 → (유휴)

특징:
- 순서 보장 (모든 메시지가 하나의 파티션에 순차 저장)
- Consumer 2, 3은 유휴 상태 (파티션 수 < Consumer 수)
- 처리 속도: 느림 (순차 처리)
- 사용 케이스: 대기열 순서 보장 필요 시
```

### 시나리오 4: Producer 5, Partition 10, Consumer 3

```
Producer 1 ─┐
Producer 2 ─┤
Producer 3 ─┼─→ Topic: high-throughput (10 Partitions)
Producer 4 ─┤       ├─ Partition 0-9
Producer 5 ─┘
                    ↓
Consumer Group A
    ├─ Consumer 1 → Partition 0, 1, 2, 3
    ├─ Consumer 2 → Partition 4, 5, 6
    └─ Consumer 3 → Partition 7, 8, 9

특징:
- 매우 높은 처리량 (10개 파티션)
- Consumer는 여러 파티션 처리
- 파티션 수 > Consumer 수: 일부 Consumer가 더 많은 파티션 처리
- 처리 속도: 매우 빠름 (대용량 트래픽 처리 가능)
```

---

## 5. Kafka vs 전통적 메시지 큐 (RabbitMQ, ActiveMQ)

| 항목 | Kafka | RabbitMQ/ActiveMQ |
|------|-------|-------------------|
| **메시지 저장** | 디스크에 영구 저장 (로그 기반) | 메모리에 임시 저장 (소비 후 삭제) |
| **처리량** | 초당 수백만 건 (매우 높음) | 초당 수만 건 (낮음~중간) |
| **순서 보장** | 파티션 내에서 보장 | 큐 내에서 보장 |
| **소비 방식** | Pull (Consumer가 당김) | Push (Broker가 밀어줌) |
| **다중 소비** | 가능 (여러 Consumer Group) | 제한적 (Topic/Fanout 패턴) |
| **메시지 재처리** | 가능 (Offset 이동) | 불가능 (소비 후 삭제) |
| **사용 케이스** | 이벤트 스트리밍, 로그 수집, 대용량 처리 | 작업 큐, RPC, 간단한 메시징 |

---

## 6. Kafka 사용 시 주의사항

### 6.1 Rebalancing (리밸런싱)

**정의**: Consumer Group 내 Consumer가 추가/제거/장애 발생 시 파티션을 재할당하는 과정

**문제점**:
- 리밸런싱 중에는 메시지 처리 중단 (Stop-the-World)
- 대규모 그룹에서는 수십 초 소요 가능

**완화 방법**:
- `max.poll.interval.ms` 증가 (기본: 5분)
- `session.timeout.ms` 적절히 설정
- Consumer 수를 안정적으로 유지

### 6.2 메시지 중복 처리

**발생 원인**:
- Offset 커밋 전 Consumer 장애 → 동일 메시지 재처리
- `at-least-once` 전송 보장 (최소 1회 전달)

**해결 방안**:
- **멱등성 처리**: Consumer 로직을 멱등하게 설계
- **Idempotency Key**: 메시지 ID를 저장하여 중복 체크
- **Transactional Outbox**: 처리 상태를 DB에 저장

### 6.3 순서 보장 제한

**보장 범위**: 동일 파티션 내에서만 순서 보장

**문제 시나리오**:
```
User A의 주문:
  M1 (주문 생성) → Partition 0
  M2 (결제 완료) → Partition 1  # 다른 파티션!

Consumer가 M2를 먼저 처리할 수 있음 (순서 깨짐)
```

**해결 방안**:
- 순서가 중요한 메시지는 동일한 키 사용
- 예: `key = userId` → 동일 사용자의 모든 메시지는 같은 파티션

---

## 7. Kafka 설치 및 실행 (Docker Compose)

### 7.1 docker-compose.yml

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
      KAFKA_TRANSACTION_STATE_LOG_MIN_ISR: 1
      KAFKA_TRANSACTION_STATE_LOG_REPLICATION_FACTOR: 1
```

### 7.2 실행 및 확인

```bash
# Kafka 시작
docker-compose up -d

# 상태 확인
docker-compose ps

# 로그 확인
docker-compose logs -f kafka

# Topic 생성
docker exec -it kafka kafka-topics --create \
  --bootstrap-server localhost:9092 \
  --topic order-created \
  --partitions 3 \
  --replication-factor 1

# Topic 목록 확인
docker exec -it kafka kafka-topics --list \
  --bootstrap-server localhost:9092

# 메시지 발행 (테스트)
docker exec -it kafka kafka-console-producer \
  --bootstrap-server localhost:9092 \
  --topic order-created

# 메시지 소비 (테스트)
docker exec -it kafka kafka-console-consumer \
  --bootstrap-server localhost:9092 \
  --topic order-created \
  --from-beginning
```

---

## 8. Spring Kafka 통합

### 8.1 의존성 추가

```gradle
dependencies {
    implementation 'org.springframework.kafka:spring-kafka'
    testImplementation 'org.springframework.kafka:spring-kafka-test'
}
```

### 8.2 설정

```yaml
spring:
  kafka:
    bootstrap-servers: localhost:9092
    producer:
      key-serializer: org.apache.kafka.common.serialization.StringSerializer
      value-serializer: org.springframework.kafka.support.serializer.JsonSerializer
      acks: all
    consumer:
      group-id: ecommerce-consumer-group
      key-deserializer: org.apache.kafka.common.serialization.StringDeserializer
      value-deserializer: org.springframework.kafka.support.serializer.JsonDeserializer
      auto-offset-reset: earliest
      enable-auto-commit: false
      properties:
        spring.json.trusted.packages: sample.hhplus_w2.domain.order.event
```

### 8.3 Producer 예시

```java
@Service
@RequiredArgsConstructor
public class OrderEventProducer {

    private final KafkaTemplate<String, OrderCompletedEvent> kafkaTemplate;

    public void publishOrderCompleted(OrderCompletedEvent event) {
        String key = String.valueOf(event.getOrderId());
        kafkaTemplate.send("order-completed", key, event);
    }
}
```

### 8.4 Consumer 예시

```java
@Service
@Slf4j
public class OrderEventConsumer {

    @KafkaListener(topics = "order-completed", groupId = "ecommerce-consumer-group")
    public void consumeOrderCompleted(OrderCompletedEvent event) {
        log.info("주문 완료 이벤트 수신: orderId={}", event.getOrderId());
        // 비즈니스 로직 처리
    }
}
```

---

## 9. 결론

### Kafka의 강점
- ✅ 대용량 데이터 처리에 최적화
- ✅ 수평 확장이 용이 (파티션 추가)
- ✅ 메시지 영구 저장 및 재처리 가능
- ✅ 여러 Consumer가 독립적으로 소비 가능

### 이커머스 적용 케이스
1. **주문 완료 이벤트**: 데이터 플랫폼, 통계, 알림 등 여러 서비스에 전달
2. **재고 처리**: 주문과 재고 처리를 비동기로 분리
3. **쿠폰 발급**: 대량 발급 요청을 Kafka로 버퍼링
4. **대기열 관리**: 순차 처리가 필요한 대기열 구현

### 다음 단계 (Step 18)
- 선착순 쿠폰 발급을 Kafka 기반으로 재설계
- 대기열 처리를 Kafka Partition 활용
- 파티션 전략 및 Consumer 병렬 처리 최적화
