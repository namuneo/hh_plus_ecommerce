/**
 * k6 Soak Test - 사용자 여정 시뮬레이션
 *
 * 목적: 장시간 일정한 부하에서 시스템 안정성 검증
 * 시나리오: 실제 사용자의 전체 구매 여정을 2시간 동안 지속 실행
 *
 * 테스트 패턴:
 * - 100명의 동시 사용자가 2시간 동안 반복적으로 구매 여정 수행
 * - 사용자 여정:
 *   1. 상품 검색/목록 조회
 *   2. 상품 상세 조회 (2-3개)
 *   3. 쿠폰 조회 및 발급 (선택적)
 *   4. 주문 생성
 *   5. 결제 처리
 *
 * 성공 기준:
 * - 메모리 누수 없음 (Heap 사용량 일정)
 * - 응답 시간 지속적 유지 (p95 < 1000ms)
 * - 에러율 < 1%
 * - DB Connection Pool 안정적 유지
 */

import http from 'k6/http';
import { check, group, sleep } from 'k6';
import { Counter, Trend, Rate } from 'k6/metrics';
import {
    randomUserId,
    randomProductId,
    randomCouponId,
    randomSearchQuery,
    generateCartItems,
    generateRequestId,
    weighted80,
} from '../utils/fixtures.js';

// Custom Metrics
const journeyCompleted = new Counter('journey_completed');
const journeyFailed = new Counter('journey_failed');
const stepFailed = new Counter('step_failed');
const journeyDuration = new Trend('journey_duration');
const errorRate = new Rate('error_rate');

// Step-specific metrics
const searchTime = new Trend('search_time');
const detailTime = new Trend('detail_time');
const couponTime = new Trend('coupon_time');
const orderTime = new Trend('order_time');
const paymentTime = new Trend('payment_time');

// Test Configuration
export const options = {
    scenarios: {
        soak: {
            executor: 'constant-vus',
            vus: 100,           // 100 concurrent users
            duration: '2h',     // 2 hours
        },
    },
    thresholds: {
        http_req_duration: ['p(95)<1000', 'p(99)<2000'],
        http_req_failed: ['rate<0.01'],  // <1%
        journey_duration: ['p(95)<5000', 'p(99)<10000'],  // <10s for full journey
        error_rate: ['rate<0.01'],
        checks: ['rate>0.95'],  // >95% success
    },
};

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';

/**
 * Main user journey
 */
export default function () {
    const userId = randomUserId(500);
    const journeyId = generateRequestId();
    const startTime = Date.now();

    const success = group('User Purchase Journey', function () {
        // Step 1: Browse Products
        const browseSuccess = browseProducts(userId);
        if (!browseSuccess) {
            stepFailed.add(1);
            return false;
        }

        // Step 2: View Product Details (2-3 products)
        const viewSuccess = viewProductDetails(userId, 2 + Math.floor(Math.random() * 2));
        if (!viewSuccess) {
            stepFailed.add(1);
            return false;
        }

        // Step 3: (Optional) Get Coupon
        let couponId = null;
        if (weighted80()) {  // 80% try to get coupon
            couponId = tryGetCoupon(userId, journeyId);
        }

        // Step 4: Create Order
        const orderData = createOrder(userId, couponId, journeyId);
        if (!orderData) {
            stepFailed.add(1);
            return false;
        }

        // Step 5: Process Payment
        const paymentSuccess = processPayment(orderData.orderId, userId, journeyId);
        if (!paymentSuccess) {
            stepFailed.add(1);
            return false;
        }

        return true;
    });

    const duration = Date.now() - startTime;
    journeyDuration.add(duration);

    if (success) {
        journeyCompleted.add(1);
    } else {
        journeyFailed.add(1);
    }

    // Natural user think time between journeys: 10-30 seconds
    // This is realistic user behavior, not artificial delay
    sleep(10 + Math.random() * 20);
}

/**
 * Step 1: Browse Products (Search or List)
 */
function browseProducts(userId) {
    const useSearch = Math.random() < 0.3;  // 30% use search

    let url, params;
    if (useSearch) {
        const query = randomSearchQuery();
        url = `${BASE_URL}/api/products/search?q=${query}&limit=20`;
        params = { tags: { name: 'Search' } };
    } else {
        const offset = Math.floor(Math.random() * 50);
        url = `${BASE_URL}/api/products?offset=${offset}&limit=20`;
        params = { tags: { name: 'ProductList' } };
    }

    const startTime = Date.now();
    const response = http.get(url, params);
    searchTime.add(Date.now() - startTime);

    const success = check(response, {
        'browse success': (r) => r.status === 200,
    });

    if (!success) {
        errorRate.add(1);
    }

    return success;
}

/**
 * Step 2: View Product Details
 */
function viewProductDetails(userId, count) {
    for (let i = 0; i < count; i++) {
        const productId = randomProductId(100);
        const url = `${BASE_URL}/api/products/${productId}`;

        const startTime = Date.now();
        const response = http.get(url, { tags: { name: 'ProductDetail' } });
        detailTime.add(Date.now() - startTime);

        const success = check(response, {
            'detail success': (r) => r.status === 200 || r.status === 404,
        });

        if (!success) {
            errorRate.add(1);
            return false;
        }

        // User reads product details: 2-5 seconds
        sleep(2 + Math.random() * 3);
    }

    return true;
}

/**
 * Step 3: Try to Get Coupon (optional)
 */
function tryGetCoupon(userId, journeyId) {
    // First, check available coupons
    const listUrl = `${BASE_URL}/api/coupons?status=PUBLISHED`;
    const listResponse = http.get(listUrl, { tags: { name: 'CouponList' } });

    if (listResponse.status !== 200) {
        return null;
    }

    // Try to issue a random coupon
    const couponId = randomCouponId(5);
    const requestId = `${journeyId}-coupon`;

    const payload = JSON.stringify({
        userId: userId,
        requestId: requestId,
    });

    const params = {
        headers: {
            'Content-Type': 'application/json',
            'Idempotency-Key': requestId,
        },
        tags: { name: 'IssueCoupon' },
    };

    const startTime = Date.now();
    const response = http.post(`${BASE_URL}/api/coupons/${couponId}/issue`, payload, params);
    couponTime.add(Date.now() - startTime);

    const success = check(response, {
        'coupon issued or sold out': (r) => r.status === 200 || r.status === 409,
    });

    if (response.status === 200 || response.status === 202) {
        return couponId;
    }

    // Coupon sold out or already issued - not an error
    return null;
}

/**
 * Step 4: Create Order
 */
function createOrder(userId, couponId, journeyId) {
    const cartItems = generateCartItems();
    const requestId = `${journeyId}-order`;

    const payload = JSON.stringify({
        userId: userId,
        items: cartItems,
        couponId: couponId,
        requestId: requestId,
    });

    const params = {
        headers: {
            'Content-Type': 'application/json',
            'Idempotency-Key': requestId,
        },
        tags: { name: 'CreateOrder' },
    };

    const startTime = Date.now();
    const response = http.post(`${BASE_URL}/api/orders`, payload, params);
    orderTime.add(Date.now() - startTime);

    const success = check(response, {
        'order created': (r) => r.status === 200 || r.status === 201,
    });

    if (success) {
        const body = JSON.parse(response.body);
        return {
            orderId: body.orderId,
            totalAmount: body.totalAmount,
        };
    } else {
        if (!response.body.includes('OUT_OF_STOCK')) {
            errorRate.add(1);
        }
    }

    return null;
}

/**
 * Step 5: Process Payment
 */
function processPayment(orderId, userId, journeyId) {
    const requestId = `${journeyId}-payment`;

    const payload = JSON.stringify({
        orderId: orderId,
        userId: userId,
        requestId: requestId,
    });

    const params = {
        headers: {
            'Content-Type': 'application/json',
            'Idempotency-Key': requestId,
        },
        tags: { name: 'ProcessPayment' },
    };

    const startTime = Date.now();
    const response = http.post(`${BASE_URL}/api/payments`, payload, params);
    paymentTime.add(Date.now() - startTime);

    const success = check(response, {
        'payment successful': (r) => r.status === 200,
    });

    if (!success) {
        if (!response.body.includes('INSUFFICIENT_BALANCE')) {
            errorRate.add(1);
        }
    }

    return success;
}

/**
 * Setup
 */
export function setup() {
    console.log('=== Soak Test Setup ===');
    console.log(`Target: ${BASE_URL}`);
    console.log('Concurrent Users: 100');
    console.log('Duration: 2 hours');
    console.log('Journey Steps:');
    console.log('  1. Browse Products');
    console.log('  2. View Details (2-3 products)');
    console.log('  3. Get Coupon (optional)');
    console.log('  4. Create Order');
    console.log('  5. Process Payment');
    console.log('=======================');

    // Health check
    const response = http.get(`${BASE_URL}/actuator/health`);
    if (response.status !== 200) {
        throw new Error('API is not accessible');
    }

    return { startTime: Date.now() };
}

/**
 * Teardown
 */
export function teardown(data) {
    const duration = (Date.now() - data.startTime) / 1000 / 60;
    console.log('=== Soak Test Summary ===');
    console.log(`Total Duration: ${duration.toFixed(2)} minutes`);
    console.log('Monitor for:');
    console.log('  - Memory leaks (Heap usage trend)');
    console.log('  - Connection pool exhaustion');
    console.log('  - Response time degradation');
    console.log('=========================');
}