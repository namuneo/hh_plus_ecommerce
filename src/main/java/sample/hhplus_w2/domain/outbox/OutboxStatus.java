package sample.hhplus_w2.domain.outbox;

/**
 * Outbox 이벤트 상태
 */
public enum OutboxStatus {
    PENDING,    // 발행 대기
    PUBLISHED,  // 발행 완료
    FAILED      // 발행 실패 (최대 재시도 초과, DLQ 이동)
}