## :pushpin: PR 제목 규칙
[STEP05 또는 STEP06] 이름

---
## ⚠️ **중요: 이번 과제는 DB를 사용하지 않습니다**
> 모든 데이터는 **인메모리(Map, Array, Set 등)**로 관리해야 합니다.  
> 실제 DB 연동은 다음 챕터(데이터베이스 설계)에서 진행합니다.

---
## 📋 **과제 체크리스트**

### ✅ **STEP 5: 레이어드 아키텍처 기본 구현** (필수)
- [x] **도메인 모델 구현**: Entity, Value Object가 정의되었는가?
- [x] **유스케이스 구현**: API 명세가 유스케이스로 구현되었는가?
- [x] **레이어드 아키텍처**: 4계층(Presentation, Application, Domain, Infrastructure)으로 분리되었는가?
- [x] **재고 관리**: 재고 조회/차감/복구 로직이 구현되었는가?
- [x] **주문/결제**: 주문 생성 및 결제 프로세스가 구현되었는가?
- [x] **선착순 쿠폰**: 쿠폰 발급/사용/만료 로직이 구현되었는가?
- [x] **단위 테스트**: 테스트 커버리지 70% 이상 달성했는가?

### 🔥 **STEP 6: 동시성 제어 및 고급 기능** (심화)
- [x] **동시성 제어**: 선착순 쿠폰 발급의 Race Condition이 방지되었는가?
- [x] **통합 테스트**: 동시성 시나리오를 검증하는 테스트가 작성되었는가?
- [x] **인기 상품 집계**: 조회수/판매량 기반 순위 계산이 구현되었는가?
- [x] **문서화**: README.md에 동시성 제어 분석이 작성되었는가?

### 🏗️ **아키텍처 설계**
- [x] **의존성 방향**: Domain ← Application ← Infrastructure 방향이 지켜졌는가?
- [x] **책임 분리**: 각 계층의 책임이 명확히 분리되었는가?
- [x] **테스트 가능성**: Mock/Stub을 활용한 테스트가 가능한 구조인가?
- [x] **인메모리 저장소**: DB 없이 모든 데이터가 인메모리로 관리되는가?
- [x] **Repository 패턴**: 인터페이스와 인메모리 구현체가 분리되었는가?

---
## 🔗 **주요 구현 커밋**

### STEP 5
- 도메인 모델 구현: `91015ab`
- 인메모리 Repository 구현: `c52445e`
- 비즈니스 로직 Service 구현: `0cc3c42`
- REST API Controller 구현: `cf05d8f`
- 단위 테스트 작성: `c73ae10`

### STEP 6
- 동시성 제어 구현: `0cc3c42` (Service에 synchronized, 낙관적 락 포함)
- 동시성 테스트 작성: `6512118`
- 인기 상품 집계 구현: `0cc3c42` (ProductStatsService 포함)
- 동시성 제어 문서화: `d674ae2` 

---
## 💬 **리뷰 요청 사항**

### 질문/고민 포인트
1. 쿠폰 발급에 `synchronized` 키워드를 사용했는데, 다중 인스턴스 환경에서는 Redis 분산 락 등이 필요할까요?
2. 낙관적 락의 재시도 로직이 적절한지 확인 부탁드립니다. (최대 10회 재시도, 10ms 대기)

### 특별히 리뷰받고 싶은 부분
- 도메인 모델의 책임 분리가 적절한지
- 동시성 테스트의 assertion 기준이 현실적인지 (성공률 70~90%)

---
## 📊 **테스트 및 품질**

| 항목 | 결과 |
|------|------|
| 전체 테스트 | 102개 (모두 통과) |
| 단위 테스트 | 90개 (도메인 + 서비스 + Repository) |
| 동시성 테스트 | 12개 (쿠폰 4개 + 재고 5개 + 주문 3개) |
| 빌드 상태 | ✅ BUILD SUCCESSFUL |

---
## 🔒 **동시성 제어 방식** (STEP 6 필수)

**선택한 방식:**
- [x] Mutex/Lock (쿠폰 발급 - `synchronized`)
- [ ] Semaphore
- [ ] Atomic Operations
- [ ] Queue 기반
- [x] 기타: 낙관적 락 (재고 차감 - version 필드)

**구현 이유:**
- **쿠폰 발급 (`synchronized`)**:
  - 선착순 요구사항에 부합하는 간단하고 확실한 방법
  - 단일 인스턴스 환경에서 100% 정확한 수량 제어 보장
  - 코드가 직관적이고 이해하기 쉬움

- **재고 차감 (낙관적 락)**:
  - DB 락을 잡지 않아 읽기 성능 우수
  - version 필드로 동시 수정 즉시 감지
  - 재시도 로직으로 충돌 해결 (최대 10회, 10ms 대기)
  - 이커머스 환경에서 동일 상품 동시 주문 빈도는 상대적으로 낮음

**참고 문서:**
- README.md의 "🔒 동시성 제어 전략" 섹션 (290+ 라인) 참조

---
## 🎯 **아키텍처 설계**

### 디렉토리 구조
```
src/main/java/sample/hhplus_w2/
├── controller/           # Presentation Layer - REST API
│   ├── cart/
│   ├── coupon/
│   ├── order/
│   ├── product/
│   └── stats/
├── service/             # Application Layer - 비즈니스 로직
│   ├── cart/
│   ├── coupon/
│   ├── order/
│   ├── product/
│   └── stats/
├── domain/              # Domain Layer - 엔티티, 도메인 로직
│   ├── cart/           (Cart, CartItem, CartStatus)
│   ├── coupon/         (Coupon, CouponUser, CouponStatus, CouponType)
│   ├── order/          (Order, OrderItem, OrderHistory, OrderStatus)
│   ├── product/        (Product - 재고 차감 로직 포함)
│   ├── stats/          (ProductSalesStats)
│   └── user/           (User)
└── repository/          # Infrastructure Layer - 데이터 저장소
    ├── cart/impl/      (CartRepositoryImpl - ConcurrentHashMap)
    ├── coupon/impl/    (CouponRepositoryImpl - ConcurrentHashMap)
    ├── order/impl/     (OrderRepositoryImpl - ConcurrentHashMap)
    ├── product/impl/   (ProductRepositoryImpl - ConcurrentHashMap)
    └── stats/impl/     (ProductSalesStatsRepositoryImpl - ConcurrentHashMap)
```

### 주요 설계 결정
- **선택한 아키텍처**: 레이어드 아키텍처 (4계층)
- **데이터 저장 방식**: 인메모리 (ConcurrentHashMap + AtomicLong)
- **선택 이유**:
  - 계층별 책임 분리로 유지보수성 향상
  - Repository 인터페이스로 추후 JPA 전환 용이
  - 도메인 로직을 엔티티에 집중 (예: `Product.decreaseStock()`, `Coupon.issue()`)
- **트레이드오프**:
  - 인메모리 저장소: 애플리케이션 재시작 시 데이터 손실
  - `synchronized`: 단일 인스턴스 한정 (다중 인스턴스에서는 분산 락 필요)
  - 낙관적 락: 충돌 시 재시도 필요 (클라이언트 경험 저하 가능)

---
## 📝 **회고**

### ✨ 잘한 점
- 도메인 모델에 비즈니스 로직을 집중시켜 응집도 높은 설계 달성
- 동시성 테스트를 통해 Race Condition 방지 검증
- README에 290+ 라인의 상세한 기술 분석 문서 작성
- 102개의 테스트로 안정성 확보

### 😓 어려웠던 점
- 동시성 테스트의 적절한 assertion 기준 설정 (100% vs 70~90% 성공률)
- 낙관적 락의 재시도 횟수와 대기 시간 튜닝
- synchronized와 낙관적 락 중 어떤 상황에 어떤 방식이 적합한지 판단

### 🚀 다음에 시도할 것
- Redis 분산 락을 활용한 다중 인스턴스 환경 대응
- 실제 DB 연동 후 JPA Pessimistic Lock과 비교
- 인기 상품 집계를 스케줄러로 주기적 갱신하는 방식으로 개선

---
## 📚 **참고 자료**
<!-- 학습에 도움이 된 자료가 있다면 공유해주세요 -->
- 

---
## ✋ **체크리스트 (제출 전 확인)**

- [x] DB 관련 라이브러리를 사용하지 않았는가? (Spring Data JPA는 있지만 실제 DB 연결 없음)
- [x] 모든 Repository가 인메모리로 구현되었는가?
- [x] build.gradle에 DB 드라이버가 없는가? (H2, MySQL 등 실제 DB 드라이버 미포함)
- [x] 환경변수에 DB 연결 정보가 없는가?
- [x] 모든 테스트가 통과하는가? (102개 테스트 모두 통과)
- [x] 빌드가 성공하는가? (./gradlew clean build 성공)