package sample.hhplus_w2.domain.processed;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 처리된 이벤트 이력 (중복 처리 방지)
 *
 * Idempotent Consumer 패턴 구현
 */
@Entity
@Table(name = "processed_event", indexes = {
        @Index(name = "idx_request_id", columnList = "request_id")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProcessedEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String requestId;

    @Column(nullable = false, length = 50)
    private String eventType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ProcessStatus status;

    @Column(nullable = false)
    private LocalDateTime processedAt;

    @Column(columnDefinition = "TEXT")
    private String errorMessage;

    private ProcessedEvent(String requestId, String eventType, ProcessStatus status) {
        this.requestId = requestId;
        this.eventType = eventType;
        this.status = status;
        this.processedAt = LocalDateTime.now();
    }

    public static ProcessedEvent of(String requestId, String eventType, ProcessStatus status) {
        return new ProcessedEvent(requestId, eventType, status);
    }

    public ProcessedEvent withError(String errorMessage) {
        this.errorMessage = errorMessage;
        return this;
    }
}