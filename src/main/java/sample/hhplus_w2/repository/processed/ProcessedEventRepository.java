package sample.hhplus_w2.repository.processed;

import org.springframework.data.jpa.repository.JpaRepository;
import sample.hhplus_w2.domain.processed.ProcessedEvent;

/**
 * 처리된 이벤트 Repository
 */
public interface ProcessedEventRepository extends JpaRepository<ProcessedEvent, Long> {

    /**
     * requestId로 처리 이력 존재 여부 확인
     */
    boolean existsByRequestId(String requestId);
}