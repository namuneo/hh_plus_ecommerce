package sample.hhplus_w2.service.coupon;

import jakarta.persistence.OptimisticLockException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sample.hhplus_w2.domain.coupon.Coupon;
import sample.hhplus_w2.domain.coupon.CouponStatus;
import sample.hhplus_w2.domain.coupon.CouponUser;
import sample.hhplus_w2.infrastructure.lock.DistributedLock;
import sample.hhplus_w2.repository.coupon.CouponRepository;
import sample.hhplus_w2.repository.coupon.CouponUserRepository;

import java.time.Duration;
import java.util.List;

@Slf4j
@Service
public class CouponService {

    private final CouponRepository couponRepository;
    private final CouponUserRepository couponUserRepository;
    private final DistributedLock distributedLock;
    private final RedisCouponService redisCouponService;

    public CouponService(CouponRepository couponRepository,
                         CouponUserRepository couponUserRepository,
                         DistributedLock distributedLock,
                         RedisCouponService redisCouponService) {
        this.couponRepository = couponRepository;
        this.couponUserRepository = couponUserRepository;
        this.distributedLock = distributedLock;
        this.redisCouponService = redisCouponService;
    }

    @Transactional
    public Coupon createCoupon(Coupon coupon) {
        return couponRepository.save(coupon);
    }

    @Transactional(readOnly = true)
    public Coupon getCoupon(Long id) {
        return couponRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("쿠폰을 찾을 수 없습니다: " + id));
    }

    @Transactional(readOnly = true)
    public Coupon getCouponByCode(String code) {
        return couponRepository.findByCode(code)
                .orElseThrow(() -> new IllegalArgumentException("쿠폰 코드를 찾을 수 없습니다: " + code));
    }

    @Transactional(readOnly = true)
    public List<Coupon> getPublishedCoupons() {
        return couponRepository.findByStatus(CouponStatus.PUBLISHED);
    }

    /**
     * 쿠폰 발급 (낙관적 락 사용)
     * OptimisticLockException 발생 시 예외 전파하여 재시도 가능
     */
    @Transactional
    public CouponUser issueCoupon(Long couponId, Long userId) {
        try {
            Coupon coupon = getCoupon(couponId);

            if (couponUserRepository.findByCouponIdAndUserId(couponId, userId).isPresent()) {
                throw new IllegalStateException("이미 발급받은 쿠폰입니다.");
            }

            if (!coupon.canIssue()) {
                throw new IllegalStateException("발급 불가능한 쿠폰입니다.");
            }

            boolean issued = coupon.issue();
            if (!issued) {
                throw new IllegalStateException("쿠폰이 모두 소진되었습니다.");
            }

            couponRepository.save(coupon);

            CouponUser couponUser = CouponUser.issue(couponId, userId);
            return couponUserRepository.save(couponUser);
        } catch (OptimisticLockException | ObjectOptimisticLockingFailureException e) {
            // 낙관적 락 충돌 - 다른 트랜잭션이 먼저 수정함
            throw new IllegalStateException("쿠폰 발급 중 충돌이 발생했습니다. 다시 시도해주세요.");
        }
    }

    @Transactional(readOnly = true)
    public List<CouponUser> getUserCoupons(Long userId) {
        return couponUserRepository.findByUserId(userId);
    }

    @Transactional
    public void publishCoupon(Long couponId) {
        Coupon coupon = getCoupon(couponId);
        coupon.publish();
        couponRepository.save(coupon);
    }

    /**
     * 쿠폰 발급 (분산락 사용)
     *
     * 분산 환경에서의 선착순 쿠폰 발급:
     * 1. 분산락 획득 (Redis SETNX) - 쿠폰 ID 기준
     * 2. 중복 발급 체크 및 수량 확인
     * 3. 트랜잭션 시작 및 쿠폰 발급
     * 4. 트랜잭션 커밋
     * 5. 분산락 해제
     *
     * 락 키 설계:
     * - "coupon:issue:{couponId}" : 쿠폰별 락
     * - 동일 쿠폰에 대한 동시 발급 요청을 직렬화
     *
     * @param couponId 쿠폰 ID
     * @param userId 사용자 ID
     * @return 발급된 CouponUser
     */
    public CouponUser issueCouponWithDistributedLock(Long couponId, Long userId) {
        String lockKey = "coupon:issue:" + couponId;

        // 분산락으로 보호된 쿠폰 발급
        return distributedLock.executeWithLock(
            lockKey,
            Duration.ofSeconds(5),  // 락 획득 대기 시간
            Duration.ofSeconds(10), // 락 보유 시간
            () -> issueCouponInTransaction(couponId, userId)
        );
    }

    /**
     * 트랜잭션 내부에서 쿠폰 발급
     * - 분산락이 이미 획득된 상태에서 실행됨
     */
    @Transactional
    public CouponUser issueCouponInTransaction(Long couponId, Long userId) {
        Coupon coupon = getCoupon(couponId);

        // 중복 발급 체크
        if (couponUserRepository.findByCouponIdAndUserId(couponId, userId).isPresent()) {
            throw new IllegalStateException("이미 발급받은 쿠폰입니다.");
        }

        // 발급 가능 여부 확인
        if (!coupon.canIssue()) {
            throw new IllegalStateException("발급 불가능한 쿠폰입니다.");
        }

        // 쿠폰 발급
        boolean issued = coupon.issue();
        if (!issued) {
            throw new IllegalStateException("쿠폰이 모두 소진되었습니다.");
        }

        couponRepository.save(coupon);

        CouponUser couponUser = CouponUser.issue(couponId, userId);
        CouponUser saved = couponUserRepository.save(couponUser);

        log.info("쿠폰 발급 완료: couponId={}, userId={}, issued={}/{}",
            couponId, userId, coupon.getIssued(), coupon.getTotalIssuable());

        return saved;
    }

    /**
     * 쿠폰 발급 (Redis 기반 비동기)
     *
     * Step 14: Redis Set 기반 선착순 쿠폰 발급
     * - Redis에 즉시 발급 기록 (빠른 응답)
     * - 스케줄러가 주기적으로 DB 동기화
     *
     * 장점:
     * - 빠른 응답 속도 (Redis 메모리 연산)
     * - DB 부하 감소 (배치 처리)
     * - 정확한 선착순 보장
     *
     * @param couponId 쿠폰 ID
     * @param userId 사용자 ID
     * @return 발급 결과
     */
    public RedisCouponService.CouponIssueResult issueCouponAsync(Long couponId, Long userId) {
        // 1. 쿠폰 기본 정보 조회 (DB)
        Coupon coupon = getCoupon(couponId);

        if (!coupon.canIssue()) {
            throw new IllegalStateException("발급 불가능한 쿠폰입니다.");
        }

        // 2. Redis에서 빠르게 발급 처리
        RedisCouponService.CouponIssueResult result =
                redisCouponService.issueCouponAsync(couponId, userId, coupon.getTotalIssuable());

        if (result.isSuccess()) {
            log.info("쿠폰 발급 성공 (비동기): couponId={}, userId={}, count={}/{}",
                    couponId, userId, result.getIssuedCount(), coupon.getTotalIssuable());
        } else {
            log.debug("쿠폰 발급 실패 (비동기): couponId={}, userId={}, reason={}",
                    couponId, userId, result.getFailureReason());
        }

        return result;
    }

    /**
     * Redis에서 발급 여부 확인
     *
     * @param couponId 쿠폰 ID
     * @param userId 사용자 ID
     * @return 발급 여부
     */
    public boolean isIssuedInRedis(Long couponId, Long userId) {
        return redisCouponService.isIssuedToUser(couponId, userId);
    }
}
