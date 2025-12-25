package sample.hhplus_w2.infrastructure.kafka;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sample.hhplus_w2.domain.coupon.CouponUser;
import sample.hhplus_w2.domain.coupon.event.CouponIssueRequestEvent;
import sample.hhplus_w2.domain.coupon.event.CouponIssueResultEvent;
import sample.hhplus_w2.domain.processed.ProcessStatus;
import sample.hhplus_w2.domain.processed.ProcessedEvent;
import sample.hhplus_w2.repository.processed.ProcessedEventRepository;
import sample.hhplus_w2.service.coupon.CouponService;

/**
 * 쿠폰 발급 Kafka Consumer
 *
 * 선착순 쿠폰 발급 요청을 병렬 처리
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CouponKafkaConsumerService {

    private final CouponService couponService;
    private final ProcessedEventRepository processedEventRepository;
    private final KafkaProducerService kafkaProducerService;

    /**
     * 쿠폰 발급 요청 소비
     *
     * 5개 파티션 → 5개 Consumer 병렬 처리
     * Idempotent Consumer 패턴 적용 (중복 방지)
     */
    @KafkaListener(
            topics = "coupon-issue-request",
            groupId = "${spring.kafka.consumer.group-id}",
            concurrency = "5"  // 5개 파티션 = 5개 Consumer 스레드
    )
    @Transactional
    public void consumeCouponIssueRequest(
            @Payload CouponIssueRequestEvent event,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment ack) {

        String requestId = event.getRequestId();

        log.info("쿠폰 발급 요청 수신: partition={}, offset={}, requestId={}, couponId={}, userId={}",
                partition, offset, requestId, event.getCouponId(), event.getUserId());

        try {
            // 1. 중복 체크 (Idempotent Consumer)
            if (processedEventRepository.existsByRequestId(requestId)) {
                log.warn("중복 요청 감지, 무시: requestId={}", requestId);
                ack.acknowledge();
                return;
            }

            // 2. 쿠폰 발급 처리
            CouponUser issuedCoupon = couponService.issueCoupon(
                    event.getCouponId(),
                    event.getUserId());

            // 3. 처리 이력 저장 (동일 트랜잭션)
            processedEventRepository.save(
                    ProcessedEvent.of(requestId, "COUPON_ISSUE", ProcessStatus.SUCCESS));

            // 4. 성공 결과 이벤트 발행
            kafkaProducerService.publishCouponIssueResult(
                    CouponIssueResultEvent.success(event));

            // 5. 수동 커밋
            ack.acknowledge();

            log.info("쿠폰 발급 성공: partition={}, requestId={}, couponId={}, userId={}",
                    partition, requestId, event.getCouponId(), event.getUserId());

        } catch (Exception e) {
            log.error("쿠폰 발급 실패: partition={}, requestId={}, error={}",
                    partition, requestId, e.getMessage(), e);

            // 실패 이력 저장
            try {
                processedEventRepository.save(
                        ProcessedEvent.of(requestId, "COUPON_ISSUE", ProcessStatus.FAILED)
                                .withError(e.getMessage()));

                // 실패 결과 이벤트 발행
                kafkaProducerService.publishCouponIssueResult(
                        CouponIssueResultEvent.failure(event, e.getMessage()));

                // 실패했지만 재처리하지 않도록 커밋
                ack.acknowledge();

            } catch (Exception ex) {
                log.error("실패 이력 저장 중 오류: requestId={}", requestId, ex);
                // 재처리를 위해 커밋하지 않음
                throw new RuntimeException("쿠폰 발급 처리 실패", e);
            }
        }
    }
}