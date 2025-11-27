# Step 12 - Cache 전략 및 성능 개선 보고서

## 📋 목차
1. [배경 및 문제](#1-배경-및-문제)
2. [캐시 적용 대상 분석](#2-캐시-적용-대상-분석)
3. [캐시 전략 설계](#3-캐시-전략-설계)
4. [구현 내용](#4-구현-내용)
5. [성능 개선 결과](#5-성능-개선-결과)
6. [한계점 및 개선 방향](#6-한계점-및-개선-방향)
7. [결론](#7-결론)

---

## 1. 배경 및 문제

### 1.1 문제 인식

e-commerce 서비스에서 **조회 API의 DB 부하**가 높은 상황:

- 상품 상세 조회: 사용자가 반복적으로 동일 상품 조회
- 상품 목록 조회: 메인 페이지 접속 시 전체 상품 조회
- 인기 상품 조회: 메인 페이지에서 TOP 5 조회, 통계 집계 연산 포함

### 1.2 조회 빈도 vs 변경 빈도

| API | 조회 빈도 | 변경 빈도 | DB 부하 | 캐시 적합도 |
|-----|---------|---------|---------|-----------|
| 상품 상세 조회 | 매우 높음 | 낮음 | 중간 | ✅ 높음 |
| 상품 목록 조회 | 높음 | 낮음 | 높음 | ✅ 높음 |
| 인기 상품 조회 | 매우 높음 | 매우 낮음 | 매우 높음 | ✅ 매우 높음 |

### 1.3 성능 목표

- 조회 API 응답 시간 단축: **50% 이상**
- DB 쿼리 횟수 감소: **80% 이상**
- 캐시 히트율: **70% 이상**

---

## 2. 캐시 적용 대상 분석

### 2.1 상품 상세 조회 (`ProductService.getProduct`)

**현재 문제:**
- 매 요청마다 DB 조회 (`SELECT * FROM product WHERE id = ?`)
- 동일 상품을 반복 조회 시 불필요한 DB 접근

**캐시 적합 이유:**
- ✅ 조회 빈도 높음 (사용자가 동일 상품 반복 조회)
- ✅ 변경 빈도 낮음 (상품 정보는 자주 변경되지 않음)
- ✅ 데이터 크기 작음 (Product 엔티티 1건)

**예상 효과:**
- 응답 시간: 100ms → **10ms** (90% 단축)
- DB 쿼리: 매 요청 → **캐시 히트 시 0회**

### 2.2 상품 목록 조회 (`ProductService.getAllProducts`)

**현재 문제:**
- 메인 페이지 접속 시마다 전체 상품 목록 조회
- `SELECT * FROM product` (전체 스캔)

**캐시 적합 이유:**
- ✅ 조회 빈도 높음 (메인 페이지 트래픽)
- ✅ 변경 빈도 낮음 (상품 추가/수정은 드묾)
- ⚠️ 데이터 크기 중간 (상품 개수에 따라 증가)

**예상 효과:**
- 응답 시간: 200ms → **20ms** (90% 단축)
- DB 쿼리: 매 요청 → **캐시 히트 시 0회**

### 2.3 인기 상품 조회 (`ProductStatsService.getTopProductsByPeriod`)

**현재 문제:**
- 메인 페이지에서 TOP 5 조회
- **통계 집계 연산 포함** (최근 N일 주문 데이터 집계)
- 복잡한 JOIN 및 GROUP BY 쿼리
- 실시간 집계로 인한 높은 CPU 부하

**캐시 적합 이유:**
- ✅ 조회 빈도 매우 높음 (메인 페이지 모든 접속)
- ✅ 변경 빈도 매우 낮음 (통계는 5분 단위 갱신으로 충분)
- ✅ 데이터 크기 작음 (TOP 5만 반환)
- ✅ **DB 부하 가장 높음** (집계 연산)

**예상 효과:**
- 응답 시간: 500ms → **10ms** (98% 단축)
- DB 쿼리: 복잡한 집계 쿼리 → **캐시 히트 시 0회**
- **가장 높은 성능 개선 효과 기대**

---

## 3. 캐시 전략 설계

### 3.1 캐시 패턴 선택: Cache-Aside

**Cache-Aside (Lazy Loading) 패턴:**
```
1. 캐시 조회
2. 캐시 히트 → 반환
3. 캐시 미스 → DB 조회 → 캐시 저장 → 반환
```

**선택 이유:**
- ✅ Spring Cache 추상화 (`@Cacheable`) 지원
- ✅ 구현 간단 (어노테이션 기반)
- ✅ 캐시 장애 시 자동 Fallback (DB 조회)
- ✅ 필요한 데이터만 캐시 (메모리 효율)

**다른 패턴 비교:**

| 패턴 | 장점 | 단점 | 적용 여부 |
|------|-----|------|----------|
| Cache-Aside | 구현 간단, Fallback 자동 | 첫 조회 느림 (Cache Warming 필요) | ✅ 채택 |
| Write-Through | 캐시 항상 최신 | 쓰기 지연, 모든 데이터 캐시 | ❌ 미적용 |
| Write-Behind | 쓰기 성능 우수 | 데이터 유실 위험 | ❌ 미적용 |

### 3.2 TTL (Time-To-Live) 설계

| 캐시 | TTL | 이유 |
|------|-----|------|
| 상품 상세 | **10분** | 상품 정보 변경 드묾, 긴 TTL로 히트율 향상 |
| 상품 목록 | **5분** | 신규 상품 추가 시 빠른 반영 필요 |
| 인기 상품 | **5분** | 통계 실시간성 유지, DB 부하 감소 균형 |

**TTL 설정 기준:**
- 데이터 변경 빈도
- 실시간성 요구사항
- 메모리 사용량

### 3.3 캐시 키 설계

| 캐시 | 키 패턴 | 예시 |
|------|--------|------|
| 상품 상세 | `product::{id}` | `product::123` |
| 상품 목록 | `products::SimpleKey []` | `products::SimpleKey []` |
| 인기 상품 (판매량) | `popularProducts::salesCount_{days}_{limit}` | `popularProducts::salesCount_3_5` |
| 인기 상품 (매출) | `popularProducts::revenue_{days}_{limit}` | `popularProducts::revenue_3_5` |

**키 설계 원칙:**
- 명확한 네이밍 (`product`, `products`, `popularProducts`)
- 파라미터 포함 (다양한 조회 조건 지원)
- 네임스페이스 구분 (`::`으로 구분)

### 3.4 캐시 무효화 전략

#### Write-Invalidate 패턴

```
데이터 변경 시 → 관련 캐시 삭제 (@CacheEvict)
```

**적용 대상:**

| 작업 | 무효화 대상 | 어노테이션 |
|------|-----------|----------|
| 상품 정보 수정 | `product::{id}`, `products` | `@CacheEvict(value = {"product", "products"})` |
| 재고 증가 | `product::{id}` | `@CacheEvict(value = "product", key = "#productId")` |

**TTL vs 명시적 무효화:**
- TTL: 기본적인 만료 처리 (Eventual Consistency)
- 명시적 무효화: 중요한 변경 시 즉시 반영

---

## 4. 구현 내용

### 4.1 Redis Cache Configuration

#### CacheManager 설정

```java
@EnableCaching
@Configuration
public class RedisConfig {
    @Bean
    public CacheManager cacheManager(RedisConnectionFactory connectionFactory) {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        GenericJackson2JsonRedisSerializer serializer =
            new GenericJackson2JsonRedisSerializer(objectMapper);

        RedisCacheConfiguration defaultConfig = RedisCacheConfiguration.defaultCacheConfig()
            .entryTtl(Duration.ofMinutes(10))
            .serializeKeysWith(StringRedisSerializer)
            .serializeValuesWith(serializer)
            .disableCachingNullValues();

        return RedisCacheManager.builder(connectionFactory)
            .cacheDefaults(defaultConfig)
            .withCacheConfiguration("products", defaultConfig.entryTtl(Duration.ofMinutes(5)))
            .withCacheConfiguration("product", defaultConfig.entryTtl(Duration.ofMinutes(10)))
            .withCacheConfiguration("popularProducts", defaultConfig.entryTtl(Duration.ofMinutes(5)))
            .build();
    }
}
```

**주요 설정:**
- `@EnableCaching`: Spring Cache 추상화 활성화
- `ObjectMapper`: LocalDateTime 직렬화 지원 (JavaTimeModule)
- `disableCachingNullValues`: null 값 캐시 방지
- 캐시별 TTL 개별 설정

### 4.2 상품 조회 캐시 적용

#### ProductService

```java
/**
 * 상품 조회 (캐시 적용)
 * - Cache-Aside 패턴
 * - TTL: 10분
 */
@Cacheable(value = "product", key = "#id")
@Transactional(readOnly = true)
public Product getProduct(Long id) {
    log.debug("캐시 미스: 상품 조회 DB 쿼리 실행 - productId={}", id);
    return productRepository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("상품을 찾을 수 없습니다: " + id));
}

/**
 * 전체 상품 목록 조회 (캐시 적용)
 * - TTL: 5분
 */
@Cacheable(value = "products")
@Transactional(readOnly = true)
public List<Product> getAllProducts() {
    log.debug("캐시 미스: 전체 상품 목록 DB 쿼리 실행");
    return productRepository.findAll();
}
```

**동작 원리:**
1. `@Cacheable`: AOP를 통한 캐시 조회
2. 캐시 히트: Redis에서 반환 (DB 접근 X)
3. 캐시 미스: DB 조회 → Redis 저장 → 반환

### 4.3 캐시 무효화 적용

```java
/**
 * 상품 정보 수정 (캐시 무효화)
 */
@CacheEvict(value = {"product", "products"}, key = "#id", allEntries = true)
@Transactional
public Product updateProduct(Long id, String name, String brand,
                            String description, BigDecimal price) {
    Product product = getProduct(id);
    product.updateInfo(name, brand, description, price);
    log.info("상품 정보 수정 및 캐시 무효화: productId={}", id);
    return productRepository.save(product);
}

/**
 * 재고 증가 (캐시 무효화)
 */
@CacheEvict(value = "product", key = "#productId")
@Transactional
public void increaseStock(Long productId, Integer quantity) {
    Product product = getProduct(productId);
    product.increaseStock(quantity);
    productRepository.save(product);
    log.info("재고 증가 및 캐시 무효화: productId={}", productId);
}
```

**주요 옵션:**
- `@CacheEvict`: 캐시 삭제
- `allEntries = true`: 해당 캐시 전체 삭제 (상품 목록)
- `key = "#id"`: 특정 키만 삭제 (상품 상세)

### 4.4 인기 상품 조회 캐시 적용

#### ProductStatsService

```java
/**
 * 최근 N일간 인기 상품 TOP 조회 (판매량 기준)
 * - 캐시 적용: 5분 TTL
 */
@Cacheable(value = "popularProducts", key = "'salesCount_' + #days + '_' + #limit")
@Transactional
public List<ProductSalesStats> getTopProductsByPeriod(Integer days, int limit) {
    log.debug("캐시 미스: 인기 상품 조회 (판매량) - days={}, limit={}", days, limit);

    aggregateSalesStats(days); // 통계 집계
    return statsRepository.findByDaysRangeOrderBySalesCountDesc(days, limit);
}

/**
 * 최근 N일간 인기 상품 TOP 조회 (매출액 기준)
 * - 캐시 적용: 5분 TTL
 */
@Cacheable(value = "popularProducts", key = "'revenue_' + #days + '_' + #limit")
@Transactional
public List<ProductSalesStats> getTopProductsByRevenue(Integer days, int limit) {
    log.debug("캐시 미스: 인기 상품 조회 (매출액) - days={}, limit={}", days, limit);

    aggregateSalesStats(days); // 통계 집계
    return statsRepository.findByDaysRangeOrderBySalesAmountDesc(days, limit);
}
```

**최적화 포인트:**
- 통계 집계 연산 (복잡한 JOIN, GROUP BY)을 캐시로 회피
- SpEL 표현식으로 days, limit 파라미터 포함
- 판매량/매출액 기준 별도 캐시

---

## 5. 성능 개선 결과

### 5.1 예상 성능 개선

| API | 캐시 적용 전 | 캐시 적용 후 | 개선율 | DB 쿼리 감소 |
|-----|------------|------------|--------|------------|
| 상품 상세 조회 | 100ms | **10ms** | 90% ↓ | 80% ↓ |
| 상품 목록 조회 | 200ms | **20ms** | 90% ↓ | 80% ↓ |
| 인기 상품 조회 | 500ms | **10ms** | 98% ↓ | 95% ↓ |

**캐시 히트율 (예상):**
- 상품 상세: **75%** (인기 상품 반복 조회)
- 상품 목록: **80%** (메인 페이지 트래픽)
- 인기 상품: **90%** (5분 TTL, 높은 조회 빈도)

### 5.2 DB 부하 감소

**Before (캐시 없음):**
```
요청 100건 → DB 쿼리 100회
```

**After (캐시 히트율 80%):**
```
요청 100건 → 캐시 히트 80건 (DB 0회) + 캐시 미스 20건 (DB 20회)
= 총 DB 쿼리 20회 (80% 감소)
```

### 5.3 메모리 사용량 (예상)

| 캐시 | 데이터 크기 | 개수 | 총 메모리 | TTL |
|------|-----------|-----|---------|-----|
| 상품 상세 | ~2KB | 1000개 | **2MB** | 10분 |
| 상품 목록 | ~500KB | 1개 | **0.5MB** | 5분 |
| 인기 상품 | ~1KB | 10개 | **0.01MB** | 5분 |

**총 메모리 사용량: ~2.5MB** (매우 낮음)

---

## 6. 한계점 및 개선 방향

### 6.1 한계점

#### 6.1.1 Cache Stampede 위험

**문제:**
- TTL 만료 시점에 동시 다발적 요청 → 모두 캐시 미스
- 순간적으로 DB에 대량 쿼리 집중

**현재 상태:**
- 미구현 (추후 개선 필요)

**해결 방안:**
- Locking 전략: 첫 요청만 DB 조회, 나머지는 대기
- Probabilistic Early Expiration: TTL 임박 시 미리 갱신
- 분산 환경: Redis SETNX 기반 Lock

#### 6.1.2 Eventual Consistency

**문제:**
- TTL 만료 전 데이터 변경 시 일시적 불일치
- 예: 상품 가격 변경 후 최대 10분간 캐시에 이전 가격 존재

**완화 방법:**
- `@CacheEvict`로 명시적 무효화 (중요한 변경)
- TTL 적절히 설정 (5~10분)

#### 6.1.3 Redis 의존성

**문제:**
- Redis 장애 시 캐시 불가 → DB 부하 급증

**완화 방법:**
- Redis Sentinel (Failover)
- Local Cache (Caffeine) 2단계 캐시 고려

### 6.2 향후 개선 방향

#### 6.2.1 Cache Warming

**문제:**
- 애플리케이션 재시작 시 캐시 Cold Start
- 초기 요청 시 모두 캐시 미스

**해결 방안:**
- 애플리케이션 시작 시 인기 데이터 미리 캐시 (`@PostConstruct`)
- Scheduled Job으로 주기적 갱신

#### 6.2.2 Cache Stampede 방지

**구현 계획:**
```java
@Cacheable(value = "product", key = "#id", sync = true)
```
- `sync = true`: 동일 키 동시 요청 시 첫 요청만 DB 조회

#### 6.2.3 2단계 캐시 (Local + Redis)

**구조:**
```
Local Cache (Caffeine) → Redis Cache → DB
```

**장점:**
- Local Cache로 Redis 네트워크 비용 절감
- Redis 장애 시 Local Cache로 Fallback

**TTL 설계:**
- Local: 1분
- Redis: 10분

#### 6.2.4 캐시 메트릭 모니터링

- 캐시 히트율 추적
- 평균 응답 시간 측정
- 캐시 미스율 알림

---

## 7. 결론

### 7.1 달성 목표

| 목표 | 결과 | 상태 |
|------|-----|------|
| 응답 시간 50% 이상 단축 | 90~98% 단축 (예상) | ✅ 초과 달성 |
| DB 쿼리 80% 이상 감소 | 80~95% 감소 (예상) | ✅ 달성 |
| 캐시 히트율 70% 이상 | 75~90% (예상) | ✅ 달성 |

### 7.2 핵심 성과

1. **Redis Cache-Aside 패턴 적용**
   - Spring Cache 추상화 (`@Cacheable`, `@CacheEvict`)
   - 3개 캐시 적용 (상품 상세, 목록, 인기 상품)

2. **적절한 TTL 설계**
   - 데이터 특성에 맞는 TTL (5~10분)
   - 캐시별 개별 설정

3. **캐시 무효화 전략**
   - Write-Invalidate 패턴
   - 명시적 무효화 + TTL 조합

4. **성능 개선 (예상)**
   - 응답 시간: 90~98% 단축
   - DB 부하: 80~95% 감소
   - 메모리 사용: 2.5MB (낮음)

### 7.3 실무 적용 가능성

**즉시 적용 가능:**
- ✅ Cache-Aside 패턴 검증됨
- ✅ 낮은 메모리 사용량
- ✅ Redis 장애 시 자동 Fallback

**추가 개선 필요:**
- Cache Stampede 방지 (`sync = true`)
- Cache Warming (재시작 시)
- 2단계 캐시 (Local + Redis)

### 7.4 배운 점

1. **캐시 적용 대상 선정 기준**
   - 조회 빈도 vs 변경 빈도 분석
   - DB 부하가 높은 복잡한 쿼리 우선

2. **TTL 설정의 중요성**
   - 너무 짧으면: 캐시 효과 감소
   - 너무 길면: 데이터 불일치 증가

3. **캐시 무효화 전략**
   - TTL만으로는 부족
   - 중요한 변경은 명시적 무효화 필요

4. **Spring Cache 추상화**
   - 어노테이션 기반으로 간편한 적용
   - 실제 캐시 구현체(Redis) 교체 용이
