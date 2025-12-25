#!/bin/bash

###############################################################################
# k6 Load Testing Suite - Run All Tests
#
# 모든 부하 테스트 시나리오를 순차적으로 실행하고 결과를 저장합니다.
#
# Usage:
#   ./run-all-tests.sh [BASE_URL]
#
# Example:
#   ./run-all-tests.sh http://localhost:8080
###############################################################################

set -e  # Exit on error

# Configuration
BASE_URL=${1:-"http://localhost:8080"}
RESULTS_DIR="./results"
TIMESTAMP=$(date +"%Y%m%d_%H%M%S")

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Create results directory
mkdir -p "$RESULTS_DIR"

echo "========================================"
echo "k6 Load Testing Suite"
echo "========================================"
echo "Base URL: $BASE_URL"
echo "Results Directory: $RESULTS_DIR"
echo "Timestamp: $TIMESTAMP"
echo "========================================"
echo ""

# Check if k6 is installed
if ! command -v k6 &> /dev/null; then
    echo -e "${RED}Error: k6 is not installed${NC}"
    echo "Install k6: https://k6.io/docs/getting-started/installation/"
    exit 1
fi

# Check if application is running
echo -e "${YELLOW}[1/5] Checking application health...${NC}"
if curl -s -f "$BASE_URL/actuator/health" > /dev/null; then
    echo -e "${GREEN}✓ Application is running${NC}"
else
    echo -e "${RED}✗ Application is not accessible at $BASE_URL${NC}"
    echo "Please start the application first: ./gradlew bootRun"
    exit 1
fi
echo ""

# Test 1: Spike Test
echo -e "${YELLOW}[2/5] Running Spike Test (Coupon Issuance)...${NC}"
echo "Duration: ~30 seconds"
echo "VUs: 0 → 10,000 → 0"
echo ""
k6 run \
    -e BASE_URL="$BASE_URL" \
    --out json="$RESULTS_DIR/spike-test_$TIMESTAMP.json" \
    scenarios/spike-test-coupon.js
echo -e "${GREEN}✓ Spike Test completed${NC}"
echo ""
sleep 5  # Cool down period

# Test 2: Load Test
echo -e "${YELLOW}[3/5] Running Load Test (Product Queries)...${NC}"
echo "Duration: ~5 minutes"
echo "Target RPS: 10,000"
echo ""
k6 run \
    -e BASE_URL="$BASE_URL" \
    --out json="$RESULTS_DIR/load-test_$TIMESTAMP.json" \
    scenarios/load-test-products.js
echo -e "${GREEN}✓ Load Test completed${NC}"
echo ""
sleep 5  # Cool down period

# Test 3: Stress Test
echo -e "${YELLOW}[4/5] Running Stress Test (Order Payment)...${NC}"
echo "Duration: ~10 minutes"
echo "VUs: 50 → 500"
echo ""
k6 run \
    -e BASE_URL="$BASE_URL" \
    --out json="$RESULTS_DIR/stress-test_$TIMESTAMP.json" \
    scenarios/stress-test-order.js
echo -e "${GREEN}✓ Stress Test completed${NC}"
echo ""
sleep 5  # Cool down period

# Test 4: Soak Test (Optional - takes 2 hours)
echo -e "${YELLOW}[5/5] Soak Test (User Journey)${NC}"
echo "Duration: 2 hours"
echo "VUs: 100 (constant)"
echo ""
read -p "Run Soak Test? This will take 2 hours. (y/N): " -n 1 -r
echo ""
if [[ $REPLY =~ ^[Yy]$ ]]; then
    k6 run \
        -e BASE_URL="$BASE_URL" \
        --out json="$RESULTS_DIR/soak-test_$TIMESTAMP.json" \
        scenarios/soak-test-journey.js
    echo -e "${GREEN}✓ Soak Test completed${NC}"
else
    echo -e "${YELLOW}⊘ Soak Test skipped${NC}"
fi
echo ""

# Summary
echo "========================================"
echo "All Tests Completed!"
echo "========================================"
echo "Results saved to: $RESULTS_DIR"
echo ""
echo "Files:"
ls -lh "$RESULTS_DIR"/*"$TIMESTAMP"* 2>/dev/null || echo "No result files found"
echo ""
echo "Next Steps:"
echo "1. Analyze results using k6 summary"
echo "2. Import JSON to Grafana/InfluxDB"
echo "3. Document findings in Step 20 report"
echo "========================================"