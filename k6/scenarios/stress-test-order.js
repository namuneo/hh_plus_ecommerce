/**
 * k6 Stress Test - 주문 결제 프로세스
 *
 * 목적: 시스템의 한계점(Breaking Point) 발견
 * 시나리오: 점진적으로 부하를 증가시켜 시스템이 버틸 수 있는 최대 용량 측정
 *
 * 테스트 패턴:
 * - 0-2분: 50 VUs (워밍업)
 * - 2-4분: 50 → 100 VUs
 * - 4-6분: 100 → 200 VUs
 * - 6-8분: 200 → 300 VUs
 * - 8-10분: 300 → 500 VUs (시스템 한계 도전)
 *
 * 성공 기준:
 * - p95 응답시간 < 1000ms (500 VUs 미만 시)
 * - p99 응답시간 < 2000ms (500 VUs 미만 시)
 * - 에러율 < 5%
 * - Breaking Point 식별
 */

import http from 'k6/http';
import { check, group } from 'k6';
import { Counter, Trend, Rate } from 'k6/metrics';
import {
    randomUserId,
    generateCartItems,
    generateRequestId,
    randomCouponId,
    weighted80,
} from '../utils/fixtures.js';

// Custom Metrics
const orderCreated = new Counter('orders_created');
const orderPaid = new Counter('orders_paid');
const orderFailed = new Counter('orders_failed');
const paymentResponseTime = new Trend('payment_response_time');
const stockErrors = new Counter('stock_errors');
const errorRate = new Rate('error_rate');

// Test Configuration
export const options = {
    scenarios: {
        stress: {
            executor: 'ramping-vus',
            startVUs: 50,
            stages: [
                { duration: '2m', target: 50 },   // Warm-up
                { duration: '2m', target: 100 },  // Ramp to 100
                { duration: '2m', target: 200 },  // Ramp to 200
                { duration: '2m', target: 300 },  // Ramp to 300
                { duration: '2m', target: 500 },  // Stress to 500
            ],
            gracefulRampDown: '30s',
        },
    },
    thresholds: {
        http_req_duration: ['p(95)<1000', 'p(99)<2000'],
        'http_req_duration{name:PaymentProcess}': ['p(95)<1000', 'p(99)<2000'],
        http_req_failed: ['rate<0.05'],  // <5%
        payment_response_time: ['p(95)<1000', 'p(99)<2000'],
    },
};

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';

/**
 * Complete order payment flow
 */
export default function () {
    const userId = randomUserId(1000);
    const requestId = generateRequestId();

    group('Order Payment Flow', function () {
        // Step 1: Create Order
        const orderData = createOrder(userId, requestId);
        if (!orderData) {
            orderFailed.add(1);
            return;
        }

        // Step 2: Process Payment
        const paymentSuccess = processPayment(orderData.orderId, userId, requestId);
        if (paymentSuccess) {
            orderPaid.add(1);
        } else {
            orderFailed.add(1);
        }
    });

    // No artificial sleep - users proceed immediately
}

/**
 * Step 1: Create Order
 */
function createOrder(userId, requestId) {
    const cartItems = generateCartItems();
    const useCoupon = weighted80();  // 80% use coupon

    const payload = JSON.stringify({
        userId: userId,
        items: cartItems,
        couponId: useCoupon ? randomCouponId(5) : null,
        requestId: requestId,
    });

    const params = {
        headers: {
            'Content-Type': 'application/json',
            'Idempotency-Key': requestId,
        },
        tags: { name: 'CreateOrder' },
    };

    const response = http.post(`${BASE_URL}/api/orders`, payload, params);

    const success = check(response, {
        'order created': (r) => r.status === 200 || r.status === 201,
        'has orderId': (r) => {
            try {
                const body = JSON.parse(r.body);
                return body.orderId !== undefined;
            } catch {
                return false;
            }
        },
    });

    if (success) {
        orderCreated.add(1);
        const body = JSON.parse(response.body);
        return {
            orderId: body.orderId,
            totalAmount: body.totalAmount,
        };
    } else if (response.body.includes('OUT_OF_STOCK')) {
        stockErrors.add(1);
        // Not counted as error - expected during high load
    } else {
        errorRate.add(1);
        console.error(`Order creation failed: ${response.status} - ${response.body}`);
    }

    return null;
}

/**
 * Step 2: Process Payment
 */
function processPayment(orderId, userId, requestId) {
    const payload = JSON.stringify({
        orderId: orderId,
        userId: userId,
        requestId: `${requestId}-payment`,
    });

    const params = {
        headers: {
            'Content-Type': 'application/json',
            'Idempotency-Key': `${requestId}-payment`,
        },
        tags: { name: 'PaymentProcess' },
    };

    const startTime = Date.now();
    const response = http.post(`${BASE_URL}/api/payments`, payload, params);
    const duration = Date.now() - startTime;

    paymentResponseTime.add(duration);

    const success = check(response, {
        'payment successful': (r) => r.status === 200,
        'response time acceptable': () => duration < 2000,
    });

    if (!success) {
        if (!response.body.includes('INSUFFICIENT_BALANCE')) {
            errorRate.add(1);
            console.error(`Payment failed: ${response.status} - ${response.body}`);
        }
    }

    return success;
}

/**
 * Setup
 */
export function setup() {
    console.log('=== Stress Test Setup ===');
    console.log(`Target: ${BASE_URL}`);
    console.log('Expected VUs: 50 → 500 (10 minutes)');
    console.log('Goal: Find breaking point');
    console.log('=========================');

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
    const duration = (Date.now() - data.startTime) / 1000;
    console.log('=== Stress Test Summary ===');
    console.log(`Total Duration: ${duration}s`);
    console.log('Analyze metrics to identify breaking point');
    console.log('===========================');
}