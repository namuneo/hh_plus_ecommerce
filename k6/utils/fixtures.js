/**
 * k6 Test Fixtures - Random Data Generator
 *
 * Provides realistic random data for load testing without artificial delays.
 * Per checkPoint.md requirements: No sleep(), realistic user patterns.
 */

/**
 * Generate random user ID between 1 and maxUsers
 */
export function randomUserId(maxUsers = 10000) {
    return Math.floor(Math.random() * maxUsers) + 1;
}

/**
 * Generate random coupon ID
 * Assuming 10 different coupons for testing
 */
export function randomCouponId(maxCoupons = 10) {
    return Math.floor(Math.random() * maxCoupons) + 1;
}

/**
 * Generate random product ID
 * Assuming 100 different products
 */
export function randomProductId(maxProducts = 100) {
    return Math.floor(Math.random() * maxProducts) + 1;
}

/**
 * Generate random SKU ID
 * Each product has 3-5 SKUs
 */
export function randomSkuId(productId) {
    const skuCount = Math.floor(Math.random() * 3) + 3; // 3-5 SKUs
    const skuIndex = Math.floor(Math.random() * skuCount) + 1;
    return productId * 10 + skuIndex;
}

/**
 * Generate random order quantity
 */
export function randomQuantity(min = 1, max = 5) {
    return Math.floor(Math.random() * (max - min + 1)) + min;
}

/**
 * Generate random order amount
 */
export function randomOrderAmount(min = 10000, max = 500000) {
    return Math.floor(Math.random() * (max - min + 1)) + min;
}

/**
 * Generate random cart items (1-3 items)
 */
export function generateCartItems() {
    const itemCount = Math.floor(Math.random() * 3) + 1;
    const items = [];

    for (let i = 0; i < itemCount; i++) {
        const productId = randomProductId();
        items.push({
            productId: productId,
            skuId: randomSkuId(productId),
            quantity: randomQuantity(1, 3)
        });
    }

    return items;
}

/**
 * Generate unique request ID for idempotency
 */
export function generateRequestId() {
    return `req-${Date.now()}-${Math.random().toString(36).substr(2, 9)}`;
}

/**
 * Generate realistic product search query
 */
export function randomSearchQuery() {
    const queries = [
        't-shirt',
        'jeans',
        'sneakers',
        'jacket',
        'dress',
        'hoodie',
        'pants',
        'shoes',
        'bag',
        'watch'
    ];
    return queries[Math.floor(Math.random() * queries.length)];
}

/**
 * Generate random pagination offset
 */
export function randomOffset(maxOffset = 100) {
    return Math.floor(Math.random() * maxOffset);
}

/**
 * Weighted random: 80% return true, 20% return false
 * Useful for simulating success-heavy scenarios
 */
export function weighted80() {
    return Math.random() < 0.8;
}