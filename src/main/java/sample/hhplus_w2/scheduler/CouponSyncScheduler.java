package sample.hhplus_w2.scheduler;

import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import sample.hhplus_w2.domain.coupon.Coupon;
import sample.hhplus_w2.domain.coupon.CouponUser;
import sample.hhplus_w2.repository.coupon.CouponRepository;
import sample.hhplus_w2.repository.coupon.CouponUserRepository;
import sample.hhplus_w2.service.coupon.RedisCouponService;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

/**
 * Redis → DB 쿠폰 발급 동기화 스케줄러
 *
 * 목적:
 * - Redis에 임시 저장된 쿠폰 발급 데이터를 DB에 영구 저장
 * - 실시간 응답성 유지하면서 데이터 내구성 확보
 *
 * 실행 주기:
 * - 10초마다 실행 (설정 가능)
 * - 활성 쿠폰만 동기화
 *
 * 동기화 전략:
 * 1. Redis에서 발급된 유저 목록 조회
 * 2. DB에 없는 유저만 INSERT
 * 3. 쿠폰 발급 수량 업데이트
 */
@Slf4j
@Component
public class CouponSyncScheduler {

    private final RedisCouponService redisCouponService;
    private final CouponRepository couponRepository;
    private final CouponUserRepository couponUserRepository;

    public CouponSyncScheduler(RedisCouponService redisCouponService,
                               CouponRepository couponRepository,
                               CouponUserRepository couponUserRepository) {
        this.redisCouponService = redisCouponService;
        this.couponRepository = couponRepository;
        this.couponUserRepository = couponUserRepository;
    }

    /**
     * 쿠폰 발급 데이터 동기화
     *
     * 10초마다 실행 (fixedDelay)
     * - 이전 실행 완료 후 10초 대기
     * - 실행 시간이 길어져도 중복 실행 방지
     */
    @Scheduled(fixedDelay = 10000, initialDelay = 10000)
    @Transactional
    public void syncCouponIssuance() {
        log.debug("쿠폰 발급 동기화 시작");

        try {
            // 활성 쿠폰 목록 조회 (PUBLISHED 상태)
            List<Coupon> activeCoupons = couponRepository.findAll().stream()
                    .filter(Coupon::canIssue)
                    .toList();

            int totalSynced = 0;

            for (Coupon coupon : activeCoupons) {
                int synced = syncSingleCoupon(coupon);
                totalSynced += synced;
            }

            if (totalSynced > 0) {
                log.info("쿠폰 발급 동기화 완료: {} 건", totalSynced);
            }

        } catch (Exception e) {
            log.error("쿠폰 발급 동기화 실패", e);
        }
    }

    /**
     * 단일 쿠폰 동기화
     *
     * @param coupon 쿠폰
     * @return 동기화된 발급 건수
     */
    private int syncSingleCoupon(Coupon coupon) {
        Long couponId = coupon.getId();

        // Redis에서 발급된 유저 목록 조회
        Set<Long> redisUserIds = redisCouponService.getIssuedUserIds(couponId);

        if (redisUserIds.isEmpty()) {
            return 0;
        }

        int syncedCount = 0;

        for (Long userId : redisUserIds) {
            // DB에 이미 있는지 확인
            boolean existsInDb = couponUserRepository.findByCouponIdAndUserId(couponId, userId).isPresent();

            if (!existsInDb) {
                // DB에 저장
                CouponUser couponUser = CouponUser.issue(couponId, userId);
                couponUserRepository.save(couponUser);

                syncedCount++;
                log.debug("쿠폰 발급 동기화: couponId={}, userId={}", couponId, userId);
            }
        }

        // 쿠폰 발급 수량 업데이트
        if (syncedCount > 0) {
            Integer redisCount = redisCouponService.getCurrentIssuedCount(couponId);
            int currentDbCount = couponUserRepository.findByCouponId(couponId).size();

            // Redis 카운트와 DB 카운트가 다르면 로그 (정합성 체크)
            if (!redisCount.equals(currentDbCount)) {
                log.warn("쿠폰 발급 수량 불일치: couponId={}, Redis={}, DB={}",
                        couponId, redisCount, currentDbCount);
            }

            log.info("쿠폰 동기화 완료: couponId={}, 동기화 건수={}, 총 발급={}/{}",
                    couponId, syncedCount, currentDbCount, coupon.getTotalIssuable());
        }

        return syncedCount;
    }

    /**
     * 수동 동기화 트리거 (관리자용)
     *
     * 필요 시 컨트롤러에서 호출 가능
     */
    public void triggerManualSync() {
        log.info("수동 쿠폰 동기화 시작");
        syncCouponIssuance();
    }
}
