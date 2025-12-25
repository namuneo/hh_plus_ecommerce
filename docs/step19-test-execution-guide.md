# Step 19: 부하 테스트 실행 가이드

## 실행 환경 요구사항

### 1. 필수 소프트웨어

| 소프트웨어 | 버전 | 설치 방법 |
|-----------|------|----------|
| **k6** | >= 1.4.0 | `brew install k6` (macOS) |
| **Docker** | >= 20.10 | [Docker Desktop](https://www.docker.com/products/docker-desktop/) |
| **Java** | 17 | GraalVM JDK 17 또는 OpenJDK 17 |
| **Gradle** | 8.x | Wrapper 포함 (`./gradlew`) |

### 2. 하드웨어 권장사항

| 항목 | 최소 | 권장 |
|------|------|------|
| **CPU** | 4 cores | 8+ cores |
| **RAM** | 8 GB | 16+ GB |
| **Disk** | 20 GB free | 50+ GB free |

---

## 사전 준비

### Step 1: k6 설치

```bash
# macOS
brew install k6

# Linux
sudo gpg -k
sudo gpg --no-default-keyring --keyring /usr/share/keyrings/k6-archive-keyring.gpg \
  --keyserver hkp://keyserver.ubuntu.com:80 \
  --recv-keys C5AD17C747E3415A3642D57D77C6C491D6AC1D69
echo "deb [signed-by=/usr/share/keyrings/k6-archive-keyring.gpg] https://dl.k6.io/deb stable main" | \
  sudo tee /etc/apt/sources.list.d/k6.list
sudo apt-get update
sudo apt-get install k6

# Windows
choco install k6

# 설치 확인
k6 version
```

### Step 2: Docker 환경 실행

먼저 환경변수 파일을 설정합니다:

```bash
# .env.example을 복사하여 .env 파일 생성
cp .env.example .env

# .env 파일을 열어서 실제 패스워드로 수정
# 예: vim .env 또는 nano .env
# MYSQL_ROOT_PASSWORD=your_secure_root_password
# MYSQL_PASSWORD=your_secure_password
```

그 다음 Docker Compose를 실행합니다:

```bash
# MySQL, Redis, Kafka 실행
docker compose -f docker-compose-full.yml up -d

# 서비스 상태 확인
docker compose -f docker-compose-full.yml ps

# 서비스가 모두 healthy 상태인지 확인
docker compose -f docker-compose-full.yml ps --filter "health=healthy"
```

**예상 출력:**
```
NAME                IMAGE                           STATUS
hhplus_kafka        confluentinc/cp-kafka:7.5.0     Up (healthy)
hhplus_mysql        mysql:8.0                       Up (healthy)
hhplus_redis        redis:7-alpine                  Up (healthy)
hhplus_zookeeper    confluentinc/cp-zookeeper:7.5.0 Up
```

### Step 3: 애플리케이션 빌드 및 실행

```bash
# 1. 빌드 (테스트 제외)
./gradlew clean build -x test

# 2. 애플리케이션 실행
./gradlew bootRun

# 3. 별도 터미널에서 Health Check
curl http://localhost:8080/actuator/health
```

**예상 응답:**
```json
{
  "status": "UP"
}
```

### Step 4: 테스트 데이터 준비 (Optional)

부하 테스트를 위해 기본 데이터를 준비합니다:

```bash
# API로 기본 데이터 생성
curl -X POST http://localhost:8080/api/test-data/init

# 또는 SQL 스크립트 실행 (.env에 설정한 패스워드 사용)
docker exec -i hhplus_mysql mysql -u${MYSQL_USER:-hhplus} -p${MYSQL_PASSWORD} hhplus_ecommerce < test-data.sql
```

---

## 부하 테스트 실행

### 방법 1: 자동 실행 스크립트 사용 (권장)

```bash
cd k6
./run-all-tests.sh
```

**실행 흐름:**
1. Health check 자동 수행
2. Spike Test (30초)
3. Cool down (5초)
4. Load Test (5분)
5. Cool down (5초)
6. Stress Test (10분)
7. Cool down (5초)
8. Soak Test (2시간, 선택)

**결과 저장:**
- `k6/results/spike-test_{timestamp}.json`
- `k6/results/load-test_{timestamp}.json`
- `k6/results/stress-test_{timestamp}.json`
- `k6/results/soak-test_{timestamp}.json`

### 방법 2: 개별 테스트 실행

#### Spike Test - 쿠폰 발급

```bash
k6 run k6/scenarios/spike-test-coupon.js
```

**기대 결과:**
- Duration: 30초
- VUs: 0 → 10,000 → 0
- p95 < 500ms
- p99 < 1000ms

**예상 메트릭:**
```
scenarios: (100.00%) 1 scenario, 10000 max VUs, 40s max duration
  ✓ status is 200 or 202
  ✓ response time < 1000ms

checks.........................: 98.50% ✓ 9850      ✗ 150
http_req_duration..............: avg=250ms min=10ms med=200ms max=1500ms p(95)=450ms p(99)=800ms
issued_coupons.................: 950    (31.67/s)
sold_out_errors................: 50     (1.67/s)
duplicate_errors...............: 0      (0.00/s)
```

#### Load Test - 상품 조회

```bash
k6 run k6/scenarios/load-test-products.js
```

**기대 결과:**
- Duration: 5분
- Target RPS: 10,000
- p95 < 100ms (캐시)
- p99 < 300ms

**예상 메트릭:**
```
scenarios: (100.00%) 1 scenario, 1000 max VUs, 5m30s max duration
  ✓ status is 200
  ✓ response time < 200ms

http_reqs......................: 3,000,000 (10,000/s)
http_req_duration..............: avg=50ms min=5ms med=45ms max=300ms p(95)=85ms p(99)=150ms
product_list_calls.............: 1,200,000 (4,000/s)
product_detail_calls...........: 900,000   (3,000/s)
ranking_calls..................: 600,000   (2,000/s)
search_calls...................: 300,000   (1,000/s)
cache_hits.....................: 2,700,000 (90%)
cache_misses...................: 300,000   (10%)
```

#### Stress Test - 주문 결제

```bash
k6 run k6/scenarios/stress-test-order.js
```

**기대 결과:**
- Duration: 10분
- VUs: 50 → 500
- Breaking Point 식별

**예상 메트릭:**
```
scenarios: (100.00%) 1 scenario, 500 max VUs, 10m30s max duration
  ✓ order created
  ✓ payment successful

orders_created.................: 12,500 (20.83/s)
orders_paid....................: 12,000 (20.00/s)
orders_failed..................: 500    (0.83/s)
stock_errors...................: 300    (0.50/s)
payment_response_time..........: avg=500ms p(95)=950ms p(99)=1800ms

# Breaking Point 분석
- 300 VUs: p95=800ms, 에러율 < 1%  ← 안정
- 400 VUs: p95=1200ms, 에러율 2%  ← 경고
- 500 VUs: p95=2000ms, 에러율 5%  ← 한계
```

#### Soak Test - 사용자 여정

```bash
k6 run k6/scenarios/soak-test-journey.js
```

**기대 결과:**
- Duration: 2시간
- VUs: 100 (constant)
- 메모리 누수 탐지

**예상 메트릭:**
```
scenarios: (100.00%) 1 scenario, 100 VUs, 2h0m30s max duration
  ✓ journey completed

journey_completed..............: 5,000 (0.69/s)
journey_failed.................: 50    (0.007/s)
journey_duration...............: avg=3500ms p(95)=4800ms p(99)=9500ms

# 시간대별 메모리 사용량 (모니터링 필요)
0-30분: Heap 60%, GC 5회/분
30-60분: Heap 62%, GC 5회/분  ← 안정
60-90분: Heap 63%, GC 5회/분
90-120분: Heap 64%, GC 5회/분

# 메모리 누수 없음 확인
```

---

## 실시간 모니터링

### 1. 애플리케이션 메트릭

**JVM Metrics (Actuator):**
```bash
# Heap 사용량
curl http://localhost:8080/actuator/metrics/jvm.memory.used

# GC 통계
curl http://localhost:8080/actuator/metrics/jvm.gc.pause

# Thread Pool
curl http://localhost:8080/actuator/metrics/tomcat.threads.busy
```

### 2. 데이터베이스 모니터링

```bash
# Connection Pool (환경변수 필요)
docker exec -i hhplus_mysql mysql -u${MYSQL_USER:-hhplus} -p${MYSQL_PASSWORD} -e "SHOW PROCESSLIST;"

# Slow Query Log (환경변수 필요)
docker exec -i hhplus_mysql mysql -u${MYSQL_USER:-hhplus} -p${MYSQL_PASSWORD} -e "SELECT * FROM mysql.slow_log LIMIT 10;"
```

### 3. Redis 모니터링

```bash
# Redis 연결 및 명령 통계
docker exec -it hhplus_redis redis-cli INFO stats

# 메모리 사용량
docker exec -it hhplus_redis redis-cli INFO memory
```

### 4. Kafka 모니터링

```bash
# Consumer Lag 확인
docker exec -it hhplus_kafka kafka-consumer-groups \
  --bootstrap-server localhost:9092 \
  --describe \
  --group coupon-consumer-group

# Topic 메시지 수
docker exec -it hhplus_kafka kafka-run-class kafka.tools.GetOffsetShell \
  --broker-list localhost:9092 \
  --topic coupon-issue-request
```

---

## 결과 분석

### 1. k6 Summary 출력

테스트 완료 후 자동으로 출력되는 요약:

```
     ✓ status is 200
     ✓ response time < 1000ms

     checks.........................: 98.50% ✓ 98500    ✗ 1500
     data_received..................: 150 MB 5.0 MB/s
     data_sent......................: 50 MB  1.7 MB/s
     http_req_blocked...............: avg=1ms    min=0ms   med=0ms   max=50ms  p(95)=2ms   p(99)=5ms
     http_req_connecting............: avg=0.5ms  min=0ms   med=0ms   max=30ms  p(95)=1ms   p(99)=3ms
     http_req_duration..............: avg=250ms  min=10ms  med=200ms max=1500ms p(95)=450ms p(99)=800ms
       { expected_response:true }...: avg=240ms  min=10ms  med=195ms max=1400ms p(95)=440ms p(99)=780ms
     http_req_failed................: 1.50%  ✓ 1500     ✗ 98500
     http_req_receiving.............: avg=1ms    min=0ms   med=1ms   max=10ms  p(95)=2ms   p(99)=5ms
     http_req_sending...............: avg=0.5ms  min=0ms   med=0ms   max=5ms   p(95)=1ms   p(99)=2ms
     http_req_tls_handshaking.......: avg=0ms    min=0ms   med=0ms   max=0ms   p(95)=0ms   p(99)=0ms
     http_req_waiting...............: avg=248ms  min=9ms   med=198ms max=1490ms p(95)=448ms p(99)=798ms
     http_reqs......................: 100000 3333.33/s
     iteration_duration.............: avg=252ms  min=11ms  med=201ms max=1505ms p(95)=455ms p(99)=810ms
     iterations.....................: 100000 3333.33/s
     vus............................: 0      min=0      max=10000
     vus_max........................: 10000  min=10000  max=10000
```

### 2. JSON 결과 파일 분석

```bash
# jq로 p95, p99 추출
cat k6/results/spike-test_{timestamp}.json | \
  jq '.metrics.http_req_duration | {p95: .values["p(95)"], p99: .values["p(99)"]}'

# 에러율 확인
cat k6/results/spike-test_{timestamp}.json | \
  jq '.metrics.http_req_failed.values.rate'

# Custom Metric 확인
cat k6/results/spike-test_{timestamp}.json | \
  jq '.metrics.issued_coupons.values.count'
```

### 3. 병목 지점 식별

| 증상 | 가능한 원인 | 확인 방법 |
|------|-----------|----------|
| **p95 > 500ms** | Redis 분산락 폴링 | Redis MONITOR 확인 |
| **Connection timeout** | DB Pool 부족 | HikariCP 메트릭 확인 |
| **OOM Error** | 메모리 누수 | Heap Dump 분석 |
| **High GC Pause** | Old Gen 누적 | GC 로그 확인 |
| **Stock 에러 증가** | Optimistic Lock 충돌 | DB UPDATE 실패 로그 |

---

## 트러블슈팅

### 문제 1: k6 실행 시 "connection refused"

**원인:** 애플리케이션이 실행되지 않음

**해결:**
```bash
# 애플리케이션 상태 확인
curl http://localhost:8080/actuator/health

# 실행 중이 아니면 재시작
./gradlew bootRun
```

### 문제 2: MySQL connection 에러

**원인:** Docker 컨테이너가 실행되지 않음

**해결:**
```bash
# Docker 컨테이너 상태 확인
docker compose -f docker-compose-full.yml ps

# 재시작
docker compose -f docker-compose-full.yml up -d
```

### 문제 3: Kafka "Topic not found"

**원인:** Topic이 자동 생성되지 않음

**해결:**
```bash
# Topic 수동 생성
docker exec -it hhplus_kafka kafka-topics \
  --bootstrap-server localhost:9092 \
  --create \
  --topic coupon-issue-request \
  --partitions 5 \
  --replication-factor 1
```

### 문제 4: k6 VU 부족 경고

**원인:** 동시 사용자 수 부족

**해결:**
```bash
# maxVUs 증가
k6 run --vus 1000 --max-vus 2000 k6/scenarios/load-test-products.js
```

### 문제 5: 테스트 중 애플리케이션 crash

**원인:** Heap 메모리 부족

**해결:**
```bash
# JVM Heap 증가
export JAVA_OPTS="-Xms2g -Xmx4g"
./gradlew bootRun
```

---

## 성능 개선 체크리스트

### 즉시 대응 (Short-term)

- [ ] DB Connection Pool 증가 (20 → 50)
- [ ] Redis 분산락 타임아웃 감소 (50ms → 20ms)
- [ ] JVM Heap 증가 (1g → 2g)
- [ ] Tomcat Thread Pool 증가 (200 → 400)

### 중기 대응 (Mid-term)

- [ ] Kafka 기반 비동기 쿠폰 발급 적용 (Step 18)
- [ ] Read Replica 추가 (조회 부하 분산)
- [ ] Redis Cluster 구성 (고가용성)
- [ ] CDN 적용 (정적 리소스)

### 장기 대응 (Long-term)

- [ ] MSA 전환 (도메인별 분리)
- [ ] CQRS 패턴 적용
- [ ] Event Sourcing
- [ ] Auto Scaling (K8s)

---

## 다음 단계 (Step 20)

1. **결과 수집 및 분석**
   - 모든 테스트 결과 JSON 저장
   - 메트릭 시각화 (Grafana)
   - 병목 지점 문서화

2. **시스템 개선**
   - 식별된 병목 해결
   - 개선 전/후 벤치마크

3. **장애 대응 문서 작성**
   - 장애 시나리오
   - 대응 절차
   - MTTD, MTTR 목표

4. **최종 보고서 작성**
   - 테스트 계획 및 결과
   - 성능 개선 내역
   - 향후 개선 방향

---

## 참고 자료

- [k6 Documentation](https://k6.io/docs/)
- [k6 Best Practices](https://k6.io/docs/testing-guides/best-practices/)
- [HikariCP Configuration](https://github.com/brettwooldridge/HikariCP#configuration)
- [Spring Boot Actuator](https://docs.spring.io/spring-boot/reference/actuator/metrics.html)
- [Docker Compose](https://docs.docker.com/compose/)