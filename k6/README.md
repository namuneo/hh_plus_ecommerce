# k6 Load Testing Suite

E-commerce 시스템의 성능 및 안정성 검증을 위한 k6 부하 테스트 스크립트 모음입니다.

## 📋 목차

- [사전 준비](#사전-준비)
- [테스트 시나리오](#테스트-시나리오)
- [실행 방법](#실행-방법)
- [결과 분석](#결과-분석)
- [디렉토리 구조](#디렉토리-구조)

## 🛠️ 사전 준비

### 1. k6 설치

**macOS:**
```bash
brew install k6
```

**Linux:**
```bash
sudo gpg -k
sudo gpg --no-default-keyring --keyring /usr/share/keyrings/k6-archive-keyring.gpg --keyserver hkp://keyserver.ubuntu.com:80 --recv-keys C5AD17C747E3415A3642D57D77C6C491D6AC1D69
echo "deb [signed-by=/usr/share/keyrings/k6-archive-keyring.gpg] https://dl.k6.io/deb stable main" | sudo tee /etc/apt/sources.list.d/k6.list
sudo apt-get update
sudo apt-get install k6
```

**Windows:**
```bash
choco install k6
```

**또는 Docker 사용:**
```bash
docker pull grafana/k6:latest
```

### 2. 애플리케이션 실행

테스트 전에 애플리케이션이 실행 중이어야 합니다:

```bash
./gradlew bootRun
```

또는 Docker Compose로 전체 환경 실행:
```bash
docker-compose up -d
```

## 🎯 테스트 시나리오

### 1. Spike Test - 쿠폰 발급 (`spike-test-coupon.js`)

**목적:** 순간적인 대량 트래픽에 대한 시스템 대응력 검증

**시나리오:**
- 10초 동안 0 → 10,000 VUs로 급증
- 10초간 10,000 VUs 유지
- 10초 동안 10,000 → 0 VUs로 감소

**성공 기준:**
- p95 응답시간 < 500ms
- p99 응답시간 < 1000ms
- 에러율 < 1% (수량 소진 제외)

**실행:**
```bash
k6 run scenarios/spike-test-coupon.js
```

### 2. Stress Test - 주문 결제 (`stress-test-order.js`)

**목적:** 시스템의 한계점(Breaking Point) 발견

**시나리오:**
- 10분 동안 50 → 500 VUs로 점진적 증가
- 각 단계: 50 → 100 → 200 → 300 → 500 VUs

**성공 기준:**
- p95 응답시간 < 1000ms
- p99 응답시간 < 2000ms
- 에러율 < 5%

**실행:**
```bash
k6 run scenarios/stress-test-order.js
```

### 3. Load Test - 상품 조회 (`load-test-products.js`)

**목적:** 일반적인 운영 부하에서 성능 검증

**시나리오:**
- 5분간 10,000 RPS 유지
- 조회 패턴:
  - 상품 목록 (40%)
  - 상품 상세 (30%)
  - 인기 상품 랭킹 (20%)
  - 상품 검색 (10%)

**성공 기준:**
- p95 응답시간 < 100ms (캐시)
- p99 응답시간 < 300ms
- 에러율 < 0.1%

**실행:**
```bash
k6 run scenarios/load-test-products.js
```

### 4. Soak Test - 사용자 여정 (`soak-test-journey.js`)

**목적:** 장시간 운영 시 안정성 검증 (메모리 누수, 리소스 고갈 등)

**시나리오:**
- 2시간 동안 100 VUs 일정 유지
- 전체 구매 여정 반복:
  1. 상품 검색/목록 조회
  2. 상품 상세 조회 (2-3개)
  3. 쿠폰 조회 및 발급 (선택)
  4. 주문 생성
  5. 결제 처리

**성공 기준:**
- 메모리 사용량 일정 유지
- p95 응답시간 < 1000ms
- 에러율 < 1%

**실행:**
```bash
k6 run scenarios/soak-test-journey.js
```

## 🚀 실행 방법

### 기본 실행

```bash
# 특정 시나리오 실행
k6 run scenarios/spike-test-coupon.js

# 커스텀 BASE_URL 지정
k6 run -e BASE_URL=http://localhost:8080 scenarios/spike-test-coupon.js

# VUs 수 오버라이드
k6 run --vus 200 scenarios/load-test-products.js
```

### 결과를 JSON으로 저장

```bash
k6 run --out json=results/spike-test-result.json scenarios/spike-test-coupon.js
```

### InfluxDB + Grafana 연동

```bash
k6 run --out influxdb=http://localhost:8086/k6 scenarios/spike-test-coupon.js
```

### 클라우드 모드 (k6 Cloud)

```bash
k6 cloud scenarios/spike-test-coupon.js
```

### Docker로 실행

```bash
docker run --rm -i \
  -v $(pwd):/scripts \
  --network host \
  grafana/k6:latest \
  run /scripts/scenarios/spike-test-coupon.js
```

## 📊 결과 분석

### 1. 콘솔 출력

k6는 테스트 실행 중 실시간 메트릭을 출력합니다:

```
scenarios: (100.00%) 1 scenario, 10000 max VUs, 30s max duration
✓ status is 200 or 202
✓ response time < 1000ms

checks.........................: 98.50% ✓ 9850      ✗ 150
data_received..................: 15 MB  500 kB/s
data_sent......................: 5 MB   167 kB/s
http_req_duration..............: avg=250ms min=10ms med=200ms max=1500ms p(95)=450ms p(99)=800ms
http_req_failed................: 1.50%  ✓ 150       ✗ 9850
http_reqs......................: 10000  333.33/s
```

### 2. 주요 메트릭 해석

| 메트릭 | 설명 | 목표 |
|--------|------|------|
| `http_req_duration` | 요청 응답 시간 | p95 < 500ms |
| `http_req_failed` | 실패한 요청 비율 | < 1% |
| `http_reqs` | 초당 요청 수 (TPS) | >= 1000 |
| `vus` | 가상 사용자 수 | 시나리오별 상이 |
| `checks` | 검증 성공률 | > 95% |

### 3. Custom Metrics

각 스크립트는 비즈니스 특화 메트릭을 제공합니다:

**Spike Test (Coupon):**
- `issued_coupons`: 성공적으로 발급된 쿠폰 수
- `sold_out_errors`: 수량 소진 에러 (정상)
- `duplicate_errors`: 중복 발급 방지 (정상)

**Stress Test (Order):**
- `orders_created`: 생성된 주문 수
- `orders_paid`: 결제 완료 수
- `stock_errors`: 재고 부족 에러

**Load Test (Products):**
- `cache_hits`: 캐시 적중 수
- `cache_misses`: 캐시 미스 수
- `product_list_calls`: 목록 조회 수
- `ranking_calls`: 랭킹 조회 수

**Soak Test (Journey):**
- `journey_completed`: 완료된 여정 수
- `journey_failed`: 실패한 여정 수
- `journey_duration`: 전체 여정 소요 시간

### 4. 병목 지점 식별

다음 지표를 모니터링하여 병목 지점을 식별합니다:

**애플리케이션:**
- JVM Heap 사용률 (목표: < 80%)
- GC 빈도 및 소요 시간
- Thread Pool 사용률

**데이터베이스:**
- Connection Pool 사용률 (목표: < 80%)
- Slow Query (> 1초)
- CPU/Memory 사용률

**Redis:**
- 응답 시간 (p95 < 10ms)
- Connection 수
- 메모리 사용률

**Kafka:**
- Consumer Lag
- Message 처리량
- Partition 분산 상태

## 📁 디렉토리 구조

```
k6/
├── README.md                           # 이 문서
├── utils/
│   └── fixtures.js                     # 공통 랜덤 데이터 생성 함수
├── scenarios/
│   ├── spike-test-coupon.js           # Spike Test - 쿠폰 발급
│   ├── stress-test-order.js           # Stress Test - 주문 결제
│   ├── load-test-products.js          # Load Test - 상품 조회
│   └── soak-test-journey.js           # Soak Test - 사용자 여정
└── results/                            # 테스트 결과 저장 (gitignore)
    ├── spike-test-result.json
    ├── stress-test-result.json
    └── ...
```

## 🔍 문제 해결

### Connection Refused 에러

```bash
# 애플리케이션이 실행 중인지 확인
curl http://localhost:8080/actuator/health

# BASE_URL 환경변수 설정
k6 run -e BASE_URL=http://localhost:8080 scenarios/spike-test-coupon.js
```

### VU 부족 경고

```bash
# maxVUs를 증가시켜 실행
k6 run --vus 1000 --max-vus 2000 scenarios/load-test-products.js
```

### 메모리 부족

```bash
# Docker로 실행 시 메모리 제한 증가
docker run --rm -i \
  -v $(pwd):/scripts \
  --network host \
  -m 4g \
  grafana/k6:latest \
  run /scripts/scenarios/spike-test-coupon.js
```

## 📚 참고 자료

- [k6 Documentation](https://k6.io/docs/)
- [k6 Examples](https://k6.io/docs/examples/)
- [Performance Testing Guide](https://k6.io/docs/testing-guides/)
- [Metrics Reference](https://k6.io/docs/using-k6/metrics/)

## 📝 참고 사항

### No Artificial Delays

checkPoint.md 요구사항에 따라:
- ❌ `sleep()` 사용하지 않음 (Soak Test의 자연스러운 think time 제외)
- ✅ 실제 사용자 패턴 시뮬레이션
- ✅ Realistic fixture data 사용

### Idempotency

모든 POST 요청은 `Idempotency-Key` 헤더를 포함:
- 중복 요청 방지
- 재시도 안전성 보장
- `requestId`로 고유성 보장

### Performance Targets

| API | p95 | p99 | TPS |
|-----|-----|-----|-----|
| 쿠폰 발급 | < 500ms | < 1000ms | 1000+ |
| 주문 결제 | < 1000ms | < 2000ms | 500+ |
| 상품 조회 (캐시) | < 100ms | < 200ms | 10,000+ |
| 상품 조회 (DB) | < 200ms | < 300ms | 1000+ |