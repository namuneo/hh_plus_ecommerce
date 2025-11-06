package sample.hhplus_w2.domain.order;

/**
 * 주문 상태
 */
public enum OrderStatus {
    PENDING,     // 결제 대기
    PAID,        // 결제 완료
    CANCELLED,   // 취소
    EXPIRED      // 만료 (결제 시간 초과)
}