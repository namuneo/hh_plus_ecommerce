package sample.hhplus_w2.domain.order;

/**
 * 주문 상태 변경 주체
 */
public enum ActorType {
    SYSTEM,      // 시스템 (자동)
    USER,        // 사용자
    ADMIN        // 관리자
}