/**
 * k6 Load Test - 상품 조회 API
 *
 * 목적: 일반적인 운영 부하에서 시스템 성능 검증
 * 시나리오: 평소 트래픽 패턴 시뮬레이션 (10,000 RPS 목표)
 *
 * 테스트 패턴:
 * - 5분간 일정한 요청률 유지 (10,000 RPS)
 * - 다양한 조회 패턴 믹스:
 *   - 상품 목록 조회 (40%)
 *   - 상품 상세 조회 (30%)
 *   - 인기 상품 랭킹 조회 (20%)
 *   - 상품 검색 (10%)
 *
 * 성공 기준:
 * - p95 응답시간 < 100ms (캐시 적중 시)
 * - p95 응답시간 < 200ms (캐시 미스 시)
 * - p99 응답시간 < 300ms
 * - 에러율 < 0.1%
 * - 처리량 >= 10,000 RPS
 */

import http from 'k6/http';
import { check } from 'k6';
import { Counter, Trend, Rate } from 'k6/metrics';
import {
    randomProductId,
    randomSearchQuery,
    randomOffset,
} from '../utils/fixtures.js';

// Custom Metrics
const productListCalls = new Counter('product_list_calls');
const productDetailCalls = new Counter('product_detail_calls');
const rankingCalls = new Counter('ranking_calls');
const searchCalls = new Counter('search_calls');
const cacheHits = new Counter('cache_hits');
const cacheMisses = new Counter('cache_misses');
const responseTime = new Trend('product_query_response_time');
const errorRate = new Rate('error_rate');

// Test Configuration
export const options = {
    scenarios: {
        constant_load: {
            executor: 'constant-arrival-rate',
            rate: 10000,           // 10,000 requests per timeUnit
            timeUnit: '1s',        // per second = 10,000 RPS
            duration: '5m',        // 5 minutes
            preAllocatedVUs: 500,  // Start with 500 VUs
            maxVUs: 1000,          // Scale up to 1000 if needed
        },
    },
    thresholds: {
        http_req_duration: ['p(95)<100', 'p(99)<300'],
        'http_req_duration{type:cached}': ['p(95)<100', 'p(99)<200'],
        'http_req_duration{type:uncached}': ['p(95)<200', 'p(99)<300'],
        http_req_failed: ['rate<0.001'],  // <0.1%
        product_query_response_time: ['p(95)<100', 'p(99)<300'],
        checks: ['rate>0.99'],  // >99% success
    },
};

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';

/**
 * Main test scenario - Random query pattern
 */
export default function () {
    const rand = Math.random();

    if (rand < 0.4) {
        // 40% - Product List
        queryProductList();
    } else if (rand < 0.7) {
        // 30% - Product Detail
        queryProductDetail();
    } else if (rand < 0.9) {
        // 20% - Ranking
        queryRanking();
    } else {
        // 10% - Search
        queryProductSearch();
    }
}

/**
 * Query: Product List (with pagination)
 */
function queryProductList() {
    const offset = randomOffset(100);
    const limit = 20;

    const url = `${BASE_URL}/api/products?offset=${offset}&limit=${limit}`;
    const params = {
        tags: { name: 'ProductList', type: 'cached' },
    };

    const startTime = Date.now();
    const response = http.get(url, params);
    const duration = Date.now() - startTime;

    responseTime.add(duration);
    productListCalls.add(1);

    // Check for cache hit (via custom header or response time)
    const isCached = duration < 50 || response.headers['X-Cache-Hit'] === 'true';
    if (isCached) {
        cacheHits.add(1);
    } else {
        cacheMisses.add(1);
    }

    const success = check(response, {
        'status is 200': (r) => r.status === 200,
        'has products array': (r) => {
            try {
                const body = JSON.parse(r.body);
                return Array.isArray(body.products);
            } catch {
                return false;
            }
        },
        'response time < 200ms': () => duration < 200,
    });

    if (!success) {
        errorRate.add(1);
    }
}

/**
 * Query: Product Detail
 */
function queryProductDetail() {
    const productId = randomProductId(100);

    const url = `${BASE_URL}/api/products/${productId}`;
    const params = {
        tags: { name: 'ProductDetail', type: 'cached' },
    };

    const startTime = Date.now();
    const response = http.get(url, params);
    const duration = Date.now() - startTime;

    responseTime.add(duration);
    productDetailCalls.add(1);

    const isCached = duration < 50 || response.headers['X-Cache-Hit'] === 'true';
    if (isCached) {
        cacheHits.add(1);
    } else {
        cacheMisses.add(1);
    }

    const success = check(response, {
        'status is 200': (r) => r.status === 200,
        'has product data': (r) => {
            try {
                const body = JSON.parse(r.body);
                return body.productId === productId;
            } catch {
                return false;
            }
        },
        'response time < 150ms': () => duration < 150,
    });

    if (!success && response.status !== 404) {
        errorRate.add(1);
    }
}

/**
 * Query: Popular Product Ranking
 */
function queryRanking() {
    const url = `${BASE_URL}/api/products/ranking/popular?days=3&limit=5`;
    const params = {
        tags: { name: 'ProductRanking', type: 'cached' },
    };

    const startTime = Date.now();
    const response = http.get(url, params);
    const duration = Date.now() - startTime;

    responseTime.add(duration);
    rankingCalls.add(1);

    // Ranking should be heavily cached (5-minute cache per requirements)
    const isCached = duration < 30 || response.headers['X-Cache-Hit'] === 'true';
    if (isCached) {
        cacheHits.add(1);
    } else {
        cacheMisses.add(1);
    }

    const success = check(response, {
        'status is 200': (r) => r.status === 200,
        'has ranking data': (r) => {
            try {
                const body = JSON.parse(r.body);
                return Array.isArray(body.rankings) && body.rankings.length <= 5;
            } catch {
                return false;
            }
        },
        'response time < 100ms': () => duration < 100,
    });

    if (!success) {
        errorRate.add(1);
    }
}

/**
 * Query: Product Search
 */
function queryProductSearch() {
    const query = randomSearchQuery();
    const offset = randomOffset(20);

    const url = `${BASE_URL}/api/products/search?q=${query}&offset=${offset}&limit=20`;
    const params = {
        tags: { name: 'ProductSearch', type: 'uncached' },
    };

    const startTime = Date.now();
    const response = http.get(url, params);
    const duration = Date.now() - startTime;

    responseTime.add(duration);
    searchCalls.add(1);

    // Search is typically not cached (or short TTL)
    cacheMisses.add(1);

    const success = check(response, {
        'status is 200': (r) => r.status === 200,
        'has search results': (r) => {
            try {
                const body = JSON.parse(r.body);
                return Array.isArray(body.results);
            } catch {
                return false;
            }
        },
        'response time < 300ms': () => duration < 300,
    });

    if (!success) {
        errorRate.add(1);
    }
}

/**
 * Setup
 */
export function setup() {
    console.log('=== Load Test Setup ===');
    console.log(`Target: ${BASE_URL}`);
    console.log('Target RPS: 10,000');
    console.log('Duration: 5 minutes');
    console.log('Query Mix:');
    console.log('  - Product List: 40%');
    console.log('  - Product Detail: 30%');
    console.log('  - Ranking: 20%');
    console.log('  - Search: 10%');
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
    const duration = (Date.now() - data.startTime) / 1000;
    console.log('=== Load Test Summary ===');
    console.log(`Total Duration: ${duration}s`);
    console.log('Check metrics for cache hit rate and response times');
    console.log('=========================');
}