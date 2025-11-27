package sample.hhplus_w2.infrastructure.lock;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 분산락 통합 테스트
 * - TestContainer를 활용한 Redis 환경 구성
 * - 동시성 제어 검증
 */
@SpringBootTest
@Testcontainers
class DistributedLockIntegrationTest {

    @Container
    static GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:7.2-alpine"))
        .withExposedPorts(6379)
        .withReuse(true);

    @DynamicPropertySource
    static void registerRedisProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
    }

    @Autowired
    private DistributedLock distributedLock;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @AfterEach
    void tearDown() {
        // Redis 데이터 정리
        redisTemplate.getConnectionFactory().getConnection().serverCommands().flushAll();
    }

    @Test
    @DisplayName("분산락 획득 및 해제 성공")
    void lockAndUnlock_Success() {
        // given
        String key = "test:lock:1";

        // when
        String lockValue = distributedLock.tryLock(key);

        // then
        assertThat(lockValue).isNotNull();

        // 락 해제
        distributedLock.unlock(key, lockValue);
    }

    @Test
    @DisplayName("동시에 여러 스레드가 락 획득 시도 - 순차 처리")
    void concurrentLockAttempts_Sequential() throws InterruptedException {
        // given
        String key = "test:lock:concurrent";
        int threadCount = 10;
        AtomicInteger counter = new AtomicInteger(0);
        CountDownLatch latch = new CountDownLatch(threadCount);
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);

        // when
        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    distributedLock.executeWithLock(
                        key,
                        Duration.ofSeconds(3),
                        Duration.ofSeconds(5),
                        () -> {
                            counter.incrementAndGet();
                            return null;
                        }
                    );
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        executor.shutdown();

        // then
        assertThat(counter.get()).isEqualTo(threadCount);
    }

    @Test
    @DisplayName("락 타임아웃 - 획득 실패")
    void lockTimeout_Failure() throws InterruptedException {
        // given
        String key = "test:lock:timeout";
        String lockValue = distributedLock.tryLock(key, Duration.ofSeconds(10), Duration.ofSeconds(20));

        // when
        String secondLockValue = distributedLock.tryLock(key, Duration.ofSeconds(1), Duration.ofSeconds(5));

        // then
        assertThat(lockValue).isNotNull();
        assertThat(secondLockValue).isNull(); // 타임아웃으로 획득 실패

        // cleanup
        distributedLock.unlock(key, lockValue);
    }

    @Test
    @DisplayName("락 자동 만료 (TTL) - 데드락 방지")
    void lockExpiration_DeadlockPrevention() throws InterruptedException {
        // given
        String key = "test:lock:expiration";
        String lockValue = distributedLock.tryLock(key, Duration.ofSeconds(3), Duration.ofSeconds(1));

        // when
        Thread.sleep(1500); // TTL 경과 대기

        // then
        String newLockValue = distributedLock.tryLock(key, Duration.ofSeconds(1), Duration.ofSeconds(5));
        assertThat(newLockValue).isNotNull(); // TTL 경과 후 새 락 획득 가능

        // cleanup
        distributedLock.unlock(key, newLockValue);
    }

    @Test
    @DisplayName("동시에 100개 요청 - 순차 처리 확인")
    void highConcurrency_100Requests() throws InterruptedException {
        // given
        String key = "test:lock:high-concurrency";
        int threadCount = 100;
        AtomicInteger successCount = new AtomicInteger(0);
        CountDownLatch latch = new CountDownLatch(threadCount);
        ExecutorService executor = Executors.newFixedThreadPool(50);

        // when
        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    distributedLock.executeWithLock(
                        key,
                        Duration.ofSeconds(10),
                        Duration.ofSeconds(10),
                        () -> {
                            successCount.incrementAndGet();
                            return null;
                        }
                    );
                } catch (Exception e) {
                    // 락 획득 실패는 예외 발생
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        executor.shutdown();

        // then
        assertThat(successCount.get()).isEqualTo(threadCount);
    }

    @Test
    @DisplayName("서로 다른 키는 독립적으로 락 획득 가능")
    void differentKeys_IndependentLocks() throws InterruptedException {
        // given
        String key1 = "test:lock:key1";
        String key2 = "test:lock:key2";
        List<String> results = new ArrayList<>();
        CountDownLatch latch = new CountDownLatch(2);

        // when
        Thread t1 = new Thread(() -> {
            distributedLock.executeWithLock(key1, () -> {
                results.add("key1");
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                return null;
            });
            latch.countDown();
        });

        Thread t2 = new Thread(() -> {
            distributedLock.executeWithLock(key2, () -> {
                results.add("key2");
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                return null;
            });
            latch.countDown();
        });

        t1.start();
        t2.start();
        latch.await();

        // then
        assertThat(results).hasSize(2);
        assertThat(results).contains("key1", "key2");
    }
}
