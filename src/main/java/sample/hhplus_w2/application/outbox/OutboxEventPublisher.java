package sample.hhplus_w2.application.outbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sample.hhplus_w2.domain.order.event.OrderCompletedEvent;
import sample.hhplus_w2.domain.outbox.OutboxEvent;
import sample.hhplus_w2.infrastructure.kafka.KafkaProducerService;
import sample.hhplus_w2.repository.outbox.OutboxEventRepository;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Outbox 패턴 이벤트 발행 서비스
 *
 * 동작 방식:
 * 1. 주기적으로 PENDING 상태의 Outbox 이벤트를 조회
 * 2. 각 이벤트를 Kafka로 발행
 * 3. 성공 시 PUBLISHED 상태로 변경
 * 4. 실패 시 재시도 횟수 증가, 최대 재시도 초과 시 DLQ로 이동
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OutboxEventPublisher {

    private static final int MAX_RETRIES = 5;

    private final OutboxEventRepository outboxEventRepository;
    private final KafkaProducerService kafkaProducerService;
    private final ObjectMapper objectMapper;

    /**
     * 대기 중인 Outbox 이벤트 발행
     * 5초마다 실행
     */
    @Scheduled(fixedDelay = 5000)
    public void publishPendingEvents() {
        List<OutboxEvent> pendingEvents = outboxEventRepository.findPendingEvents(MAX_RETRIES);

        if (pendingEvents.isEmpty()) {
            return;
        }

        log.info("Outbox 이벤트 발행 시작: {} 건", pendingEvents.size());

        for (OutboxEvent event : pendingEvents) {
            try {
                publishEvent(event);
            } catch (Exception e) {
                log.error("Outbox 이벤트 처리 실패: eventId={}, eventType={}, error={}",
                        event.getId(), event.getEventType(), e.getMessage(), e);
            }
        }
    }

    /**
     * 단일 이벤트 발행 처리
     */
    @Transactional
    protected void publishEvent(OutboxEvent outboxEvent) {
        try {
            // 이벤트 타입에 따라 적절한 도메인 이벤트로 역직렬화
            Object domainEvent = deserializeEvent(outboxEvent);

            // Kafka로 메시지 발행
            CompletableFuture<?> future = publishToKafka(outboxEvent.getEventType(), domainEvent);

            // Kafka 발행 성공 대기 (동기 방식으로 처리)
            future.get();

            // 발행 성공 처리
            outboxEvent.markAsPublished();
            outboxEventRepository.save(outboxEvent);

            log.info("Outbox 이벤트 Kafka 발행 성공: eventId={}, eventType={}",
                    outboxEvent.getId(), outboxEvent.getEventType());

        } catch (Exception e) {
            // 재시도 횟수 증가
            outboxEvent.incrementRetryCount();

            // 최대 재시도 횟수 초과 시 DLQ로 이동
            if (outboxEvent.getRetryCount() >= MAX_RETRIES) {
                outboxEvent.moveToDLQ();
                log.error("Outbox 이벤트 최대 재시도 초과, DLQ 이동: eventId={}, eventType={}",
                        outboxEvent.getId(), outboxEvent.getEventType());
            } else {
                log.warn("Outbox 이벤트 Kafka 발행 실패, 재시도 예정: eventId={}, retry={}/{}",
                        outboxEvent.getId(), outboxEvent.getRetryCount(), MAX_RETRIES);
            }

            outboxEventRepository.save(outboxEvent);
            throw new RuntimeException("Kafka 메시지 발행 실패", e);
        }
    }

    /**
     * 이벤트 타입에 따라 Kafka로 발행
     */
    private CompletableFuture<?> publishToKafka(String eventType, Object domainEvent) {
        return switch (eventType) {
            case "OrderCompleted" -> kafkaProducerService.publishOrderCompletedEvent((OrderCompletedEvent) domainEvent);
            // 추가 이벤트 타입은 여기에 등록
            default -> throw new IllegalArgumentException("지원하지 않는 이벤트 타입: " + eventType);
        };
    }

    /**
     * 이벤트 타입에 따라 적절한 도메인 이벤트로 역직렬화
     */
    private Object deserializeEvent(OutboxEvent outboxEvent) {
        try {
            String eventType = outboxEvent.getEventType();
            String payload = outboxEvent.getPayload();

            return switch (eventType) {
                case "OrderCompleted" -> objectMapper.readValue(payload, OrderCompletedEvent.class);
                // 추가 이벤트 타입은 여기에 등록
                default -> throw new IllegalArgumentException("지원하지 않는 이벤트 타입: " + eventType);
            };
        } catch (Exception e) {
            throw new RuntimeException("이벤트 역직렬화 실패: " + outboxEvent.getEventType(), e);
        }
    }
}
