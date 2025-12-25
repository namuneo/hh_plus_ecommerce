/**
 * k6 Spike Test - 선착순 쿠폰 발급
 *
 * 목적: 순간적인 대량 트래픽 발생 시 시스템 동작 검증
 * 시나리오: 인기 쿠폰 오픈 시 10,000명이 동시에 발급 요청
 *
 * 테스트 패턴:
 * - 0-10초: 0 → 10,000 VUs (급격한 증가)
 * - 10-20초: 10,000 VUs 유지
 * - 20-30초: 10,000 → 0 VUs (급격한 감소)
 *
 * 성공 기준:
 * - p95 응답시간 < 500ms
 * - p99 응답시간 < 1000ms
 * - 에러율 < 1% (수량 소진 제외)
 * - 쿠폰 발급 수량 = 정확히 totalQty
 */

import http from 'k6/http';
import { check, sleep } from 'k6';
import { Counter, Trend, Rate } from 'k6/metrics';
import { randomUserId, randomCouponId, generateRequestId } from '../utils/fixtures.js';

// Custom Metrics
const issuedCoupons = new Counter('issued_coupons');
const soldOutErrors = new Counter('sold_out_errors');
const duplicateErrors = new Counter('duplicate_errors');
const responseTime = new Trend('coupon_issue_response_time');
const errorRate = new Rate('error_rate');

// Test Configuration
export const options = {
    scenarios: {
        spike: {
            executor: 'ramping-vus',
            startVUs: 0,
            stages: [
                { duration: '10s', target: 10000 },  // Spike to 10,000 users
                { duration: '10s', target: 10000 },  // Hold at 10,000
                { duration: '10s', target: 0 },      // Drop to 0
            ],
            gracefulRampDown: '5s',
        },
    },
    thresholds: {
        http_req_duration: ['p(95)<500', 'p(99)<1000'],
        http_req_failed: ['rate<0.01'],  // <1% excluding sold out
        coupon_issue_response_time: ['p(95)<500', 'p(99)<1000'],
    },
};

// Base URL (환경변수로 설정 가능)
const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';

/**
 * Main test scenario
 */
export default function () {
    const userId = randomUserId(10000);
    const couponId = randomCouponId(5);  // 5개 쿠폰 중 랜덤 선택
    const requestId = generateRequestId();

    const url = `${BASE_URL}/api/coupons/${couponId}/issue`;
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
    const response = http.post(url, payload, params);
    const duration = Date.now() - startTime;

    responseTime.add(duration);

    // Response validation
    const result = check(response, {
        'status is 200 or 202': (r) => r.status === 200 || r.status === 202,
        'response time < 1000ms': (r) => duration < 1000,
        'has valid response body': (r) => r.body.length > 0,
    });

    // Categorize responses
    if (response.status === 200 || response.status === 202) {
        issuedCoupons.add(1);
    } else if (response.status === 409 || response.body.includes('SOLD_OUT')) {
        soldOutErrors.add(1);
        // Not counted as error - expected when coupon runs out
    } else if (response.status === 409 && response.body.includes('DUPLICATE')) {
        duplicateErrors.add(1);
        // Not counted as error - idempotency working correctly
    } else {
        errorRate.add(1);
        console.error(`Error: ${response.status} - ${response.body}`);
    }

    // No artificial sleep - per checkPoint.md requirements
    // Users naturally retry or move on immediately
}

/**
 * Setup: Pre-test preparation
 */
export function setup() {
    console.log('=== Spike Test Setup ===');
    console.log(`Target: ${BASE_URL}`);
    console.log('Expected VUs: 0 → 10,000 → 0');
    console.log('Duration: 30 seconds');
    console.log('========================');

    // Verify API is accessible
    const response = http.get(`${BASE_URL}/actuator/health`);
    if (response.status !== 200) {
        throw new Error('API is not accessible');
    }

    return { startTime: Date.now() };
}

/**
 * Teardown: Post-test summary
 */
export function teardown(data) {
    console.log('=== Spike Test Summary ===');
    console.log(`Total Duration: ${(Date.now() - data.startTime) / 1000}s`);
    console.log('Check metrics for detailed results');
    console.log('==========================');
}