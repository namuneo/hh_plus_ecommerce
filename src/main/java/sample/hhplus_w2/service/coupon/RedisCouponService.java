package sample.hhplus_w2.service.coupon;

import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.stereotype.Service;

/**
 * Redis 기반 선착순 쿠폰 발급 서비스
 *
 * 핵심 개념:
 * - Redis Set: O(1) 중복 체크 및 발급 관리
 * - Atomicity: Redis 명령어의 원자성 보장
 * - 비동기: 즉시 발급 → 스케줄러로 DB 동기화
 *
 * 장점:
 * - 빠른 응답 속도 (Redis 메모리 연산)
 * - 정확한 선착순 보장
 * - DB 부하 분산 (배치 동기화)
 */
@Slf4j
@Service
public class RedisCouponService {

    private static final String COUPON_ISSUED_KEY_PREFIX = "coupon:issued:";  // 발급된 유저 Set
    private static final String COUPON_COUNT_KEY_PREFIX = "coupon:count:";    // 발급 수량 카운터

    private final RedisTemplate<String, Object> redisTemplate;

    public RedisCouponService(RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * 쿠폰 발급 시도 (비동기)
     *
     * Redis Set을 사용한 선착순 발급:
     * 1. SISMEMBER: 중복 발급 체크 (O(1))
     * 2. INCR: 발급 수량 증가 (Atomic)
     * 3. SADD: 발급 유저 추가 (O(1))
     *
     * @param couponId 쿠폰 ID
     * @param userId 사용자 ID
     * @param maxIssuable 최대 발급 가능 수량
     * @return 발급 성공 여부
     */
    public CouponIssueResult issueCouponAsync(Long couponId, Long userId, Integer maxIssuable) {
        String issuedKey = COUPON_ISSUED_KEY_PREFIX + couponId;
        String countKey = COUPON_COUNT_KEY_PREFIX + couponId;

        SetOperations<String, Object> setOps = redisTemplate.opsForSet();

        // 1. 중복 발급 체크 (SISMEMBER)
        Boolean isAlreadyIssued = setOps.isMember(issuedKey, userId.toString());
        if (Boolean.TRUE.equals(isAlreadyIssued)) {
            log.debug("쿠폰 중복 발급 시도: couponId={}, userId={}", couponId, userId);
            return CouponIssueResult.alreadyIssued();
        }

        // 2. 발급 수량 증가 (INCR - Atomic)
        Long currentCount = redisTemplate.opsForValue().increment(countKey);

        if (currentCount == null || currentCount > maxIssuable) {
            log.debug("쿠폰 수량 초과: couponId={}, currentCount={}, maxIssuable={}",
                    couponId, currentCount, maxIssuable);
            return CouponIssueResult.soldOut();
        }

        // 3. 발급 유저 추가 (SADD)
        setOps.add(issuedKey, userId.toString());

        log.info("쿠폰 발급 성공 (Redis): couponId={}, userId={}, count={}/{}",
                couponId, userId, currentCount, maxIssuable);

        return CouponIssueResult.success(currentCount.intValue());
    }

    /**
     * 쿠폰 발급 여부 확인
     *
     * @param couponId 쿠폰 ID
     * @param userId 사용자 ID
     * @return 발급 여부
     */
    public boolean isIssuedToUser(Long couponId, Long userId) {
        String issuedKey = COUPON_ISSUED_KEY_PREFIX + couponId;
        SetOperations<String, Object> setOps = redisTemplate.opsForSet();

        Boolean isMember = setOps.isMember(issuedKey, userId.toString());
        return Boolean.TRUE.equals(isMember);
    }

    /**
     * 현재 발급 수량 조회
     *
     * @param couponId 쿠폰 ID
     * @return 발급 수량
     */
    public Integer getCurrentIssuedCount(Long couponId) {
        String countKey = COUPON_COUNT_KEY_PREFIX + couponId;
        Object count = redisTemplate.opsForValue().get(countKey);

        if (count instanceof Integer) {
            return (Integer) count;
        } else if (count instanceof String) {
            return Integer.parseInt((String) count);
        }

        return 0;
    }

    /**
     * 발급된 모든 유저 ID 조회
     *
     * 스케줄러에서 DB 동기화 시 사용
     *
     * @param couponId 쿠폰 ID
     * @return 발급된 유저 ID Set
     */
    public java.util.Set<Long> getIssuedUserIds(Long couponId) {
        String issuedKey = COUPON_ISSUED_KEY_PREFIX + couponId;
        SetOperations<String, Object> setOps = redisTemplate.opsForSet();

        java.util.Set<Object> members = setOps.members(issuedKey);
        if (members == null || members.isEmpty()) {
            return java.util.Collections.emptySet();
        }

        return members.stream()
                .map(obj -> Long.parseLong(obj.toString()))
                .collect(java.util.stream.Collectors.toSet());
    }

    /**
     * 쿠폰 발급 데이터 초기화
     *
     * 테스트 또는 쿠폰 재오픈 시 사용
     *
     * @param couponId 쿠폰 ID
     */
    public void resetCouponIssue(Long couponId) {
        String issuedKey = COUPON_ISSUED_KEY_PREFIX + couponId;
        String countKey = COUPON_COUNT_KEY_PREFIX + couponId;

        redisTemplate.delete(issuedKey);
        redisTemplate.delete(countKey);

        log.info("쿠폰 발급 데이터 초기화: couponId={}", couponId);
    }

    /**
     * 특정 유저의 발급 기록 삭제
     *
     * DB 동기화 완료 후 사용 (선택적)
     *
     * @param couponId 쿠폰 ID
     * @param userId 사용자 ID
     */
    public void removeIssuedUser(Long couponId, Long userId) {
        String issuedKey = COUPON_ISSUED_KEY_PREFIX + couponId;
        SetOperations<String, Object> setOps = redisTemplate.opsForSet();

        setOps.remove(issuedKey, userId.toString());
    }

    /**
     * 쿠폰 발급 결과 DTO
     */
    public static class CouponIssueResult {
        private final boolean success;
        private final String failureReason;
        private final Integer issuedCount;

        private CouponIssueResult(boolean success, String failureReason, Integer issuedCount) {
            this.success = success;
            this.failureReason = failureReason;
            this.issuedCount = issuedCount;
        }

        public static CouponIssueResult success(Integer issuedCount) {
            return new CouponIssueResult(true, null, issuedCount);
        }

        public static CouponIssueResult alreadyIssued() {
            return new CouponIssueResult(false, "ALREADY_ISSUED", null);
        }

        public static CouponIssueResult soldOut() {
            return new CouponIssueResult(false, "SOLD_OUT", null);
        }

        public boolean isSuccess() {
            return success;
        }

        public String getFailureReason() {
            return failureReason;
        }

        public Integer getIssuedCount() {
            return issuedCount;
        }
    }
}
