package sample.hhplus_w2.domain.outbox;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Outbox 패턴을 위한 이벤트 저장 엔티티
 *
 * 목적:
 * - 이벤트를 DB에 영속화하여 손실 방지
 * - 트랜잭션 커밋 후에도 이벤트 발행 보장 (at-least-once)
 * - 실패한 이벤트의 재시도 지원
 */
@Entity
@Table(name = "outbox_event",
        indexes = {
                @Index(name = "idx_status_retry", columnList = "status,retryCount"),
                @Index(name = "idx_created_at", columnList = "createdAt")
        })
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OutboxEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 이벤트 타입 (예: "OrderCompleted", "CouponIssued")
     */
    @Column(nullable = false, length = 100)
    private String eventType;

    /**
     * 집합 ID (주문 ID, 쿠폰 ID 등)
     */
    @Column(nullable = false, length = 50)
    private String aggregateId;

    /**
     * 이벤트 페이로드 (JSON 형태)
     */
    @Column(columnDefinition = "TEXT", nullable = false)
    private String payload;

    /**
     * 이벤트 상태
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OutboxStatus status;

    /**
     * 재시도 횟수
     */
    @Column(nullable = false)
    private Integer retryCount;

    /**
     * 발행 완료 시각
     */
    @Column
    private LocalDateTime publishedAt;

    /**
     * 생성 시각
     */
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /**
     * Outbox 이벤트 생성
     */
    public static OutboxEvent create(String eventType, String aggregateId, String payload) {
        OutboxEvent event = new OutboxEvent();
        event.eventType = eventType;
        event.aggregateId = aggregateId;
        event.payload = payload;
        event.status = OutboxStatus.PENDING;
        event.retryCount = 0;
        event.createdAt = LocalDateTime.now();
        return event;
    }

    /**
     * 발행 완료 처리
     */
    public void markAsPublished() {
        this.status = OutboxStatus.PUBLISHED;
        this.publishedAt = LocalDateTime.now();
    }

    /**
     * 재시도 횟수 증가
     */
    public void incrementRetryCount() {
        this.retryCount++;
    }

    /**
     * DLQ로 이동 (최대 재시도 초과)
     */
    public void moveToDLQ() {
        this.status = OutboxStatus.FAILED;
    }

    /**
     * 재시도 가능 여부 확인
     *
     * @param maxRetries 최대 재시도 횟수
     * @return 재시도 가능 여부
     */
    public boolean canRetry(int maxRetries) {
        return this.status == OutboxStatus.PENDING && this.retryCount < maxRetries;
    }
}
