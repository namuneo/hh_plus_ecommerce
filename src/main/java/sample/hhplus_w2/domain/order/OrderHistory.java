package sample.hhplus_w2.domain.order;

import jakarta.persistence.*;
import lombok.Getter;

import java.time.LocalDateTime;

/**
 * OrderHistory 도메인 엔티티
 * 주문 상태 변경 이력 추적
 */
@Entity
@Table(name = "order_history")
@Getter
public class OrderHistory {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_id", nullable = false)
    private Long orderId;

    @Enumerated(EnumType.STRING)
    @Column(name = "from_status", length = 20)
    private OrderStatus fromStatus;  // 변경 전 상태 (null 가능 - 최초 생성)

    @Enumerated(EnumType.STRING)
    @Column(name = "to_status", nullable = false, length = 20)
    private OrderStatus toStatus;    // 변경 후 상태

    @Column(length = 500)
    private String reason;           // 변경 사유

    @Enumerated(EnumType.STRING)
    @Column(name = "actor_type", nullable = false, length = 20)
    private ActorType actorType;     // 변경 주체 (SYSTEM, USER, ADMIN)

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    protected OrderHistory() {
    }

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }

    /**
     * 주문 상태 이력 생성
     */
    public static OrderHistory create(Long orderId, OrderStatus fromStatus, OrderStatus toStatus,
                                      String reason, ActorType actorType) {
        OrderHistory history = new OrderHistory();
        history.orderId = orderId;
        history.fromStatus = fromStatus;
        history.toStatus = toStatus;
        history.reason = reason;
        history.actorType = actorType != null ? actorType : ActorType.SYSTEM;
        history.createdAt = LocalDateTime.now();
        return history;
    }

    /**
     * 주문 생성 이력 (fromStatus = null)
     */
    public static OrderHistory createForNew(Long orderId, OrderStatus initialStatus) {
        return create(orderId, null, initialStatus, "주문 생성", ActorType.SYSTEM);
    }

    /**
     * ID 설정 (Repository에서 사용)
     */
    public void assignId(Long id) {
        if (this.id != null) {
            throw new IllegalStateException("ID는 이미 할당되었습니다.");
        }
        this.id = id;
    }
}