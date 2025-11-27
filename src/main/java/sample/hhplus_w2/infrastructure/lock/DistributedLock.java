package sample.hhplus_w2.infrastructure.lock;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.UUID;

/**
 * Redis 기반 분산락 구현
 *
 * 핵심 개념:
 * - Redis의 SETNX (SET if Not eXists) 명령어를 활용한 원자적 락 획득
 * - TTL을 통한 데드락 방지
 * - 락 소유자 식별을 통한 안전한 락 해제
 *
 * 주의사항:
 * - 트랜잭션과 락의 순서: 락 획득 → 트랜잭션 시작 → 트랜잭션 종료 → 락 해제
 * - 락 타임아웃은 비즈니스 로직 실행 시간보다 충분히 길어야 함
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DistributedLock {

    private final RedisTemplate<String, Object> redisTemplate;

    private static final String LOCK_PREFIX = "lock:";
    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(3);
    private static final Duration DEFAULT_LEASE_TIME = Duration.ofSeconds(10);

    /**
     * 분산락 획득
     *
     * @param key 락 키 (예: "product:stock:123")
     * @param timeout 락 획득 대기 시간
     * @param leaseTime 락 보유 시간 (TTL)
     * @return 락 식별자 (락 해제 시 필요)
     */
    public String tryLock(String key, Duration timeout, Duration leaseTime) {
        String lockKey = LOCK_PREFIX + key;
        String lockValue = UUID.randomUUID().toString();

        long startTime = System.currentTimeMillis();
        long timeoutMillis = timeout.toMillis();

        try {
            while (System.currentTimeMillis() - startTime < timeoutMillis) {
                // SETNX: SET if Not eXists (원자적 연산)
                Boolean acquired = redisTemplate.opsForValue()
                    .setIfAbsent(lockKey, lockValue, leaseTime);

                if (Boolean.TRUE.equals(acquired)) {
                    log.debug("분산락 획득 성공: key={}, lockValue={}", lockKey, lockValue);
                    return lockValue;
                }

                // 락 획득 실패 시 잠시 대기 후 재시도
                Thread.sleep(50);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("분산락 획득 중 인터럽트 발생: key={}", lockKey, e);
        }

        log.warn("분산락 획득 실패 (타임아웃): key={}, timeout={}ms", lockKey, timeoutMillis);
        return null;
    }

    /**
     * 분산락 획득 (기본 타임아웃 사용)
     */
    public String tryLock(String key) {
        return tryLock(key, DEFAULT_TIMEOUT, DEFAULT_LEASE_TIME);
    }

    /**
     * 분산락 해제
     *
     * @param key 락 키
     * @param lockValue 락 획득 시 받은 식별자
     */
    public void unlock(String key, String lockValue) {
        if (lockValue == null) {
            return;
        }

        String lockKey = LOCK_PREFIX + key;

        try {
            // 락 소유자 확인 후 해제 (원자적 연산)
            String currentValue = (String) redisTemplate.opsForValue().get(lockKey);

            if (lockValue.equals(currentValue)) {
                redisTemplate.delete(lockKey);
                log.debug("분산락 해제 성공: key={}, lockValue={}", lockKey, lockValue);
            } else {
                log.warn("분산락 해제 실패 (소유자 불일치): key={}, lockValue={}, currentValue={}",
                    lockKey, lockValue, currentValue);
            }
        } catch (Exception e) {
            log.error("분산락 해제 중 오류 발생: key={}, lockValue={}", lockKey, lockValue, e);
        }
    }

    /**
     * 분산락으로 보호된 작업 실행
     *
     * @param key 락 키
     * @param task 실행할 작업
     * @return 작업 실행 결과
     */
    public <T> T executeWithLock(String key, LockTask<T> task) {
        return executeWithLock(key, DEFAULT_TIMEOUT, DEFAULT_LEASE_TIME, task);
    }

    /**
     * 분산락으로 보호된 작업 실행 (타임아웃 지정)
     */
    public <T> T executeWithLock(String key, Duration timeout, Duration leaseTime, LockTask<T> task) {
        String lockValue = tryLock(key, timeout, leaseTime);

        if (lockValue == null) {
            throw new IllegalStateException("분산락 획득 실패: " + key);
        }

        try {
            return task.execute();
        } finally {
            unlock(key, lockValue);
        }
    }

    @FunctionalInterface
    public interface LockTask<T> {
        T execute();
    }
}
