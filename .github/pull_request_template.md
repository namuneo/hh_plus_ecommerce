## [STEP11] 김성준 - e-commerce

---

## 🎯 과제 개요

**Step 11 - Distributed Lock**: Redis 기반 분산락 구현 및 동시성 제어 ✅

---

## ✅ 핵심 체크리스트 (pull_request_template.md 기준)

### 1️⃣ 분산락 적용 (3개)

- [x] **적절한 곳에 분산락이 사용되었는가?**
  - ✅ 재고 차감 (`ProductService.decreaseStockWithDistributedLock`)
  - ✅ 쿠폰 발급 (`CouponService.issueCouponWithDistributedLock`)

- [x] **트랜잭션 순서와 락 순서가 보장되었는가?**
  - ✅ 락 획득 → 트랜잭션 시작 → 트랜잭션 종료 → 락 해제
  - ✅ 분산락 외부에서 트랜잭션 관리

### 2️⃣ 통합 테스트 (4개)

- [x] **infrastructure 레이어를 포함하는 통합 테스트가 작성되었는가?**
  - ✅ `DistributedLockIntegrationTest` (6개 테스트)
  - ✅ Redis TestContainer 기반

- [x] **핵심 기능에 대한 흐름이 테스트에서 검증되었는가?**
  - ✅ `ProductServiceDistributedLockTest` (5개 테스트)
  - ✅ `CouponServiceDistributedLockTest` (5개 테스트)

- [x] **동시성을 검증할 수 있는 테스트코드로 작성되었는가?**
  - ✅ ExecutorService + CountDownLatch 활용
  - ✅ 50~200개 동시 요청 시나리오

- [x] **Test Container가 적용되었는가?**
  - ✅ Redis 7.2-alpine TestContainer
  - ✅ DynamicPropertySource로 포트 동적 할당

### 3️⃣ Cache 적용 (Step 12에서 진행 예정)

- [ ] 적절하게 Key 적용이 되었는가? → Step 12

---

## 📊 P/F 기준 체크리스트 (passFail.md)

### STEP 11 - Distributed Lock

#### ✅ Transaction의 범위와 Redis 기반의 분산락 활용 이해

- [x] **분산락에 대한 이해와 DB Tx과 혼용할 때 주의할 점을 이해하였는지**
  - 분산락 외부에서 트랜잭션 시작
  - 트랜잭션 종료 후 분산락 해제
  - 락 타임아웃 > 트랜잭션 실행 시간 설정

- [x] **적절하게 분산락이 적용되는 범위에 대해 구현을 진행하였는지**
  - 재고 차감: `product:stock:{productId}` 키
  - 쿠폰 발급: `coupon:issue:{couponId}` 키
  - 적절한 락 범위 (상품/쿠폰 단위)

---

## 🔧 구현 내용

### 1. Redis 설정 및 분산락 구현

#### RedisConfig
```java
@Configuration
public class RedisConfig {
    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory connectionFactory) {
        // Key: String Serializer
        // Value: JSON Serializer
    }
}
```

#### DistributedLock
```java
@Component
public class DistributedLock {
    // Redis SETNX 기반 분산락
    // - 락 획득: setIfAbsent() 원자적 연산
    // - TTL 설정: 데드락 방지
    // - 락 해제: 소유자 확인 후 delete
}
```

**핵심 메서드:**
- `String tryLock(String key, Duration timeout, Duration leaseTime)`
- `void unlock(String key, String lockValue)`
- `<T> T executeWithLock(String key, LockTask<T> task)`

**주요 특징:**
- SETNX (SET if Not eXists) 원자적 연산
- UUID 기반 락 소유자 식별
- TTL 기반 자동 만료 (데드락 방지)
- Spin lock 방식 (50ms 간격 재시도)

### 2. 재고 차감 분산락 적용

**ProductService:**
```java
public void decreaseStockWithDistributedLock(Long productId, Integer quantity) {
    String lockKey = "product:stock:" + productId;

    distributedLock.executeWithLock(
        lockKey,
        Duration.ofSeconds(5),  // 락 획득 대기 시간
        Duration.ofSeconds(10), // 락 보유 시간
        () -> {
            decreaseStockInTransaction(productId, quantity);
            return null;
        }
    );
}

@Transactional
public void decreaseStockInTransaction(Long productId, Integer quantity) {
    // 트랜잭션 내부에서 재고 차감
}
```

**락 키 설계:**
- `product:stock:{productId}` : 상품별 락
- 동일 상품에 대한 동시 차감 요청을 직렬화

**트랜잭션 순서:**
1. 분산락 획득 (Redis SETNX)
2. 트랜잭션 시작 (`@Transactional`)
3. 재고 차감 및 저장
4. 트랜잭션 커밋
5. 분산락 해제

### 3. 쿠폰 발급 분산락 적용

**CouponService:**
```java
public CouponUser issueCouponWithDistributedLock(Long couponId, Long userId) {
    String lockKey = "coupon:issue:" + couponId;

    return distributedLock.executeWithLock(
        lockKey,
        Duration.ofSeconds(5),
        Duration.ofSeconds(10),
        () -> issueCouponInTransaction(couponId, userId)
    );
}

@Transactional
public CouponUser issueCouponInTransaction(Long couponId, Long userId) {
    // 중복 발급 체크 + 수량 확인 + 쿠폰 발급
}
```

**락 키 설계:**
- `coupon:issue:{couponId}` : 쿠폰별 락
- 선착순 쿠폰 발급 순서 보장

### 4. TestContainer 기반 통합 테스트

#### DistributedLockIntegrationTest
```java
@SpringBootTest
@Testcontainers
class DistributedLockIntegrationTest {
    @Container
    static GenericContainer<?> redis = new GenericContainer<>(
        DockerImageName.parse("redis:7.2-alpine")
    ).withExposedPorts(6379);

    @DynamicPropertySource
    static void registerRedisProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
    }
}
```

**테스트 시나리오:**
- ✅ 분산락 획득 및 해제 성공
- ✅ 동시 10개 스레드 순차 처리
- ✅ 락 타임아웃 - 획득 실패
- ✅ 락 자동 만료 (TTL) - 데드락 방지
- ✅ 높은 동시성 (100개 요청)
- ✅ 서로 다른 키는 독립적으로 락 획득

#### ProductServiceDistributedLockTest
- ✅ 동시 50명×2개 구매 - 재고 정확성 (100→0)
- ✅ 재고 부족 시 예외 발생 (60명 요청, 50명 성공)
- ✅ 100개 재고, 100명×1개 - 정확히 0개 남음
- ✅ 순차 처리로 Race Condition 방지
- ✅ 높은 동시성 (200개 요청)

#### CouponServiceDistributedLockTest
- ✅ 선착순 10개 쿠폰, 50명 요청 - 정확히 10명 발급
- ✅ 선착순 50개 쿠폰, 50명 요청 - 모두 성공
- ✅ Race Condition 방지 - 초과 발급 없음
- ✅ 중복 발급 방지 (같은 유저 5번 시도)
- ✅ 높은 동시성 (200명 요청)

---

## 📈 테스트 결과

### 분산락 기본 동작 검증

| 테스트 | 결과 | 검증 내용 |
|-------|------|---------|
| 락 획득/해제 | ✅ | Redis SETNX 정상 동작 |
| 동시 10개 스레드 | ✅ | 순차 처리 확인 |
| 락 타임아웃 | ✅ | 획득 실패 처리 |
| 락 자동 만료 (TTL) | ✅ | 데드락 방지 |
| 높은 동시성 (100개) | ✅ | 성능 및 정확성 |
| 독립적 락 (다른 키) | ✅ | 키 격리 확인 |

### 재고 차감 동시성 제어

| 테스트 시나리오 | 초기 재고 | 요청 | 결과 재고 | 상태 |
|---------------|---------|-----|---------|------|
| 50명×2개 구매 | 100 | 50×2 | 0 | ✅ |
| 60명×2개 (초과) | 100 | 60×2 | 0~20 | ✅ |
| 100명×1개 | 100 | 100×1 | 0 | ✅ |
| 10명×5개 | 100 | 10×5 | 50 | ✅ |
| 200명×5개 | 1000 | 200×5 | 0 | ✅ |

**핵심 검증:**
- ✅ 음수 재고 발생 없음
- ✅ 정확한 재고 차감
- ✅ Race Condition 방지

### 쿠폰 발급 동시성 제어

| 테스트 시나리오 | 총 수량 | 요청 | 발급 수 | 상태 |
|---------------|--------|-----|--------|------|
| 선착순 10개 | 10 | 50명 | 10개 | ✅ |
| 선착순 50개 | 50 | 50명 | 50개 | ✅ |
| 선착순 10개 | 10 | 100명 | 10개 | ✅ |
| 중복 발급 | 10 | 1명×5회 | 1개 | ✅ |
| 선착순 100개 | 100 | 200명 | 100개 | ✅ |

**핵심 검증:**
- ✅ 초과 발급 없음
- ✅ 정확한 선착순 처리
- ✅ 중복 발급 방지

---

## 🎯 핵심 기술 및 설계

### 1. Redis SETNX 기반 분산락

**원리:**
```java
Boolean acquired = redisTemplate.opsForValue()
    .setIfAbsent(lockKey, lockValue, leaseTime);
```

- SETNX: SET if Not eXists (원자적 연산)
- 키가 존재하지 않을 때만 설정 성공
- TTL 함께 설정으로 데드락 방지

**장점:**
- ✅ 분산 환경에서 동시성 제어 가능
- ✅ DB 락에 비해 낮은 부하
- ✅ Scale-out 환경 지원

**단점:**
- ⚠️ Redis 장애 시 락 획득 불가
- ⚠️ 단일 Redis 인스턴스 (향후 Cluster 고려)

### 2. 트랜잭션과 락의 순서

**올바른 순서:**
```
1. 분산락 획득 (Redis SETNX)
2. 트랜잭션 시작 (@Transactional)
3. 비즈니스 로직 실행
4. 트랜잭션 커밋
5. 분산락 해제
```

**잘못된 순서 (트랜잭션 내부 락):**
```
1. 트랜잭션 시작
2. 분산락 획득 ❌
3. 비즈니스 로직
4. 분산락 해제
5. 트랜잭션 커밋 ❌
```

**문제점:**
- 트랜잭션이 커밋되기 전에 락 해제
- 다른 스레드가 락 획득 후 아직 커밋되지 않은 데이터 조회
- Dirty Read 발생 가능

### 3. 락 키 설계

| 도메인 | 락 키 | 범위 | 이유 |
|-------|------|------|------|
| 재고 차감 | `product:stock:{productId}` | 상품별 | 동일 상품 동시 차감 방지 |
| 쿠폰 발급 | `coupon:issue:{couponId}` | 쿠폰별 | 선착순 순서 보장 |

**고려사항:**
- ✅ 최소 락 범위 (상품/쿠폰 단위)
- ✅ 다른 상품/쿠폰은 독립적으로 처리
- ✅ Hot Key 문제 완화

### 4. 타임아웃 설정

| 설정 | 값 | 이유 |
|------|---|------|
| 락 획득 대기 시간 | 5초 | 충분한 재시도 기회 |
| 락 보유 시간 (TTL) | 10초 | 트랜잭션 실행 시간 + 버퍼 |
| 재시도 간격 | 50ms | Spin lock 적절한 간격 |

**원칙:**
- 락 보유 시간 > 트랜잭션 실행 시간
- TTL 경과 시 자동 해제 (데드락 방지)

---

## 🚀 향후 개선 방향

### 1. Redisson 도입 검토

**현재 (직접 구현):**
- RedisTemplate + SETNX
- Spin lock 방식 (50ms 재시도)

**Redisson 장점:**
- Pub/Sub 기반 효율적인 대기
- Redlock 알고리즘 지원 (분산 환경)
- Watch Dog (자동 TTL 연장)

### 2. Redis Cluster

**현재:**
- 단일 Redis 인스턴스

**향후:**
- Redis Cluster (HA)
- Sentinel (자동 Failover)

### 3. 성능 최적화

- [ ] 락 충돌률 모니터링
- [ ] 락 획득 실패율 메트릭
- [ ] 락 보유 시간 최적화

### 4. 관찰 가능성

- [ ] 분산 추적 (OpenTelemetry)
- [ ] 락 획득/해제 로깅
- [ ] 성능 메트릭 수집

---

## ⚠️ 알려진 제한사항

### 1. Redis 의존성

**문제:**
- Redis 장애 시 락 획득 불가
- 서비스 전체 중단 가능성

**해결 방안:**
- Redis Sentinel (Failover)
- Fallback 로직 (DB 락)

### 2. 단일 Redis 인스턴스

**문제:**
- Split-brain 시나리오 대응 불가
- Redis 재시작 시 모든 락 초기화

**해결 방안:**
- Redisson Redlock 알고리즘
- 다중 Redis 인스턴스

### 3. TestContainer Docker 요구

**문제:**
- Docker 미실행 시 테스트 실패

**해결 방안:**
- CI/CD 환경에서 Docker 실행
- 로컬 개발: `docker compose up -d`

---

## 📋 주요 구현 커밋

| 커밋 메시지 | SHA | 설명 |
|-----------|-----|------|
| Step11: Redis 분산락 구현 및 통합 테스트 작성 | `fb86af3` | 분산락 구현, 재고/쿠폰 적용, TestContainer 테스트 |

---

## ✍️ 간단 회고 (3줄 이내)

- **잘한 점**: RedisTemplate 기반으로 분산락을 직접 구현하여 SETNX, TTL, 락 소유자 식별 등 핵심 개념을 이해했고, 트랜잭션과 락의 순서를 명확히 하여 Dirty Read를 방지했으며, TestContainer로 16개 통합 테스트를 작성하여 동시성 제어를 검증했습니다.
- **어려웠던 점**: 트랜잭션과 분산락의 순서 설정이 어려웠는데, 분산락 외부에서 트랜잭션을 시작해야 다른 스레드의 Dirty Read를 방지할 수 있음을 이해했습니다.
- **다음 시도**: Redisson 도입으로 Pub/Sub 기반 효율적인 락 대기, Redis Cluster로 HA 구성, 락 충돌률 및 성능 메트릭 모니터링 추가

---

## 🎉 결론

**Step 11 - Distributed Lock 완료**

- ✅ Redis SETNX 기반 분산락 구현
- ✅ 재고 차감 / 쿠폰 발급 동시성 제어
- ✅ 트랜잭션과 락의 순서 보장
- ✅ TestContainer 기반 통합 테스트 (16개)
- ✅ passFail.md 모든 필수 항목 충족

**핵심 성과:**
- 분산 환경에서 동시성 제어 구현
- 데드락 방지 (TTL 기반 자동 해제)
- 재고/쿠폰 데이터 정합성 100% 보장
- 체계적인 통합 테스트 (동시성 검증)

**실무 적용 가능성:**
- Scale-out 환경에서 즉시 사용 가능
- Redis Cluster 전환 용이
- Redisson 도입으로 추가 최적화 가능
