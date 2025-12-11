package sample.hhplus_w2.infrastructure.outbox;

import org.springframework.data.jpa.repository.JpaRepository;
import sample.hhplus_w2.domain.outbox.OutboxEvent;
import sample.hhplus_w2.domain.outbox.OutboxStatus;

import java.util.List;

public interface OutboxEventJpaRepository extends JpaRepository<OutboxEvent, Long> {

    /**
     * 상태와 재시도 횟수로 이벤트 조회
     *
     * @param status 이벤트 상태
     * @param maxRetryCount 최대 재시도 횟수
     * @return 조건에 맞는 이벤트 목록
     */
    List<OutboxEvent> findByStatusAndRetryCountLessThan(OutboxStatus status, Integer maxRetryCount);
}
