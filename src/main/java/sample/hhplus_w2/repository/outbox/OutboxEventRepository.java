package sample.hhplus_w2.repository.outbox;

import sample.hhplus_w2.domain.outbox.OutboxEvent;
import sample.hhplus_w2.domain.outbox.OutboxStatus;

import java.util.List;
import java.util.Optional;

public interface OutboxEventRepository {

    OutboxEvent save(OutboxEvent event);

    Optional<OutboxEvent> findById(Long id);

    List<OutboxEvent> findPendingEvents(int maxRetryCount);

    List<OutboxEvent> findByStatusAndRetryCountLessThan(OutboxStatus status, Integer maxRetryCount);
}
