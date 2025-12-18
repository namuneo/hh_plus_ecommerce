package sample.hhplus_w2.repository.outbox.impl;

import org.springframework.stereotype.Repository;
import sample.hhplus_w2.domain.outbox.OutboxEvent;
import sample.hhplus_w2.domain.outbox.OutboxStatus;
import sample.hhplus_w2.infrastructure.outbox.OutboxEventJpaRepository;
import sample.hhplus_w2.repository.outbox.OutboxEventRepository;

import java.util.List;
import java.util.Optional;

@Repository
public class OutboxEventRepositoryImpl implements OutboxEventRepository {

    private final OutboxEventJpaRepository jpaRepository;

    public OutboxEventRepositoryImpl(OutboxEventJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public OutboxEvent save(OutboxEvent event) {
        return jpaRepository.save(event);
    }

    @Override
    public Optional<OutboxEvent> findById(Long id) {
        return jpaRepository.findById(id);
    }

    @Override
    public List<OutboxEvent> findPendingEvents(int maxRetryCount) {
        return jpaRepository.findByStatusAndRetryCountLessThan(OutboxStatus.PENDING, maxRetryCount);
    }

    @Override
    public List<OutboxEvent> findByStatusAndRetryCountLessThan(OutboxStatus status, Integer maxRetryCount) {
        return jpaRepository.findByStatusAndRetryCountLessThan(status, maxRetryCount);
    }
}
