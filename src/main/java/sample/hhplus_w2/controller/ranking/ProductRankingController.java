package sample.hhplus_w2.controller.ranking;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import sample.hhplus_w2.service.ranking.ProductRankingService;

import java.util.List;

/**
 * 상품 랭킹 API Controller
 *
 * Redis Sorted Set 기반 실시간 랭킹 제공
 */
@RestController
@RequestMapping("/api/ranking")
public class ProductRankingController {

    private final ProductRankingService rankingService;

    public ProductRankingController(ProductRankingService rankingService) {
        this.rankingService = rankingService;
    }

    /**
     * TOP N 상품 랭킹 조회
     *
     * GET /api/ranking/products/top?limit=10
     *
     * @param limit 조회할 상위 개수 (기본값: 10)
     * @return TOP N 상품 랭킹 리스트
     */
    @GetMapping("/products/top")
    public ResponseEntity<List<ProductRankingService.ProductRanking>> getTopProducts(
            @RequestParam(defaultValue = "10") int limit) {

        if (limit < 1 || limit > 100) {
            throw new IllegalArgumentException("조회 개수는 1~100 사이여야 합니다.");
        }

        List<ProductRankingService.ProductRanking> rankings = rankingService.getTopProducts(limit);
        return ResponseEntity.ok(rankings);
    }

    /**
     * 특정 상품의 랭킹 조회
     *
     * GET /api/ranking/products/{productId}
     *
     * @param productId 상품 ID
     * @return 상품 랭킹 정보
     */
    @GetMapping("/products/{productId}")
    public ResponseEntity<ProductRankingResponse> getProductRanking(@PathVariable Long productId) {
        Integer rank = rankingService.getProductRank(productId);
        Integer orderCount = rankingService.getProductOrderCount(productId);

        ProductRankingResponse response = new ProductRankingResponse(productId, rank, orderCount);
        return ResponseEntity.ok(response);
    }

    /**
     * 랭킹 초기화 (관리자용)
     *
     * DELETE /api/ranking/products/reset
     */
    @DeleteMapping("/products/reset")
    public ResponseEntity<Void> resetRanking() {
        rankingService.resetRanking();
        return ResponseEntity.noContent().build();
    }

    /**
     * 상품 랭킹 응답 DTO
     */
    public static class ProductRankingResponse {
        private final Long productId;
        private final Integer rank;        // null이면 랭킹 밖
        private final Integer orderCount;

        public ProductRankingResponse(Long productId, Integer rank, Integer orderCount) {
            this.productId = productId;
            this.rank = rank;
            this.orderCount = orderCount;
        }

        public Long getProductId() {
            return productId;
        }

        public Integer getRank() {
            return rank;
        }

        public Integer getOrderCount() {
            return orderCount;
        }

        public boolean isInRanking() {
            return rank != null;
        }
    }
}
