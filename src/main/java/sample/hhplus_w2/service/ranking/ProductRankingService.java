package sample.hhplus_w2.service.ranking;

import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Redis Sorted Set 기반 상품 랭킹 서비스
 *
 * 핵심 개념:
 * - Sorted Set: score(주문 수량) 기준으로 자동 정렬되는 자료구조
 * - Atomicity: ZINCRBY 명령어로 원자적 증가 보장
 * - 실시간성: 주문 완료 시 즉시 반영
 */
@Slf4j
@Service
public class ProductRankingService {

    private static final String RANKING_KEY = "product:ranking";

    private final RedisTemplate<String, Object> redisTemplate;

    public ProductRankingService(RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * 상품 주문 수량 증가 (Atomicity 보장)
     *
     * Redis ZINCRBY 명령어 사용:
     * - 원자적 증가 연산
     * - 동시 요청 시에도 정확한 카운트 보장
     *
     * @param productId 상품 ID
     * @param quantity 주문 수량
     */
    public void incrementProductOrder(Long productId, Integer quantity) {
        ZSetOperations<String, Object> zSetOps = redisTemplate.opsForZSet();

        // ZINCRBY product:ranking {quantity} {productId}
        Double newScore = zSetOps.incrementScore(RANKING_KEY, productId.toString(), quantity);

        log.info("상품 랭킹 업데이트: productId={}, quantity={}, totalScore={}",
                productId, quantity, newScore);
    }

    /**
     * TOP N 상품 랭킹 조회 (내림차순)
     *
     * Redis ZREVRANGE 명령어 사용:
     * - score 높은 순으로 정렬
     * - O(log(N)+M) 시간 복잡도 (N=전체 크기, M=조회 개수)
     *
     * @param topN 조회할 상위 개수
     * @return 상위 N개 상품의 랭킹 정보
     */
    public List<ProductRanking> getTopProducts(int topN) {
        ZSetOperations<String, Object> zSetOps = redisTemplate.opsForZSet();

        // ZREVRANGE product:ranking 0 {topN-1} WITHSCORES
        Set<ZSetOperations.TypedTuple<Object>> topProducts =
                zSetOps.reverseRangeWithScores(RANKING_KEY, 0, topN - 1);

        List<ProductRanking> rankings = new ArrayList<>();
        if (topProducts != null) {
            int rank = 1;
            for (ZSetOperations.TypedTuple<Object> tuple : topProducts) {
                String productId = (String) tuple.getValue();
                Double score = tuple.getScore();

                rankings.add(new ProductRanking(
                        rank++,
                        Long.parseLong(productId),
                        score != null ? score.intValue() : 0
                ));
            }
        }

        log.debug("TOP {} 상품 랭킹 조회: {} 건", topN, rankings.size());
        return rankings;
    }

    /**
     * 특정 상품의 랭킹 조회
     *
     * Redis ZREVRANK 명령어 사용:
     * - 특정 member의 순위 조회 (0-based index)
     * - O(log(N)) 시간 복잡도
     *
     * @param productId 상품 ID
     * @return 순위 (1-based), 없으면 null
     */
    public Integer getProductRank(Long productId) {
        ZSetOperations<String, Object> zSetOps = redisTemplate.opsForZSet();

        // ZREVRANK product:ranking {productId}
        Long rank = zSetOps.reverseRank(RANKING_KEY, productId.toString());

        if (rank != null) {
            // 0-based → 1-based 변환
            return rank.intValue() + 1;
        }

        return null;
    }

    /**
     * 특정 상품의 주문 수량 조회
     *
     * Redis ZSCORE 명령어 사용:
     * - 특정 member의 score 조회
     * - O(1) 시간 복잡도
     *
     * @param productId 상품 ID
     * @return 주문 수량, 없으면 0
     */
    public Integer getProductOrderCount(Long productId) {
        ZSetOperations<String, Object> zSetOps = redisTemplate.opsForZSet();

        // ZSCORE product:ranking {productId}
        Double score = zSetOps.score(RANKING_KEY, productId.toString());

        return score != null ? score.intValue() : 0;
    }

    /**
     * 전체 랭킹 초기화
     *
     * 테스트 또는 주기적 리셋용
     */
    public void resetRanking() {
        redisTemplate.delete(RANKING_KEY);
        log.info("상품 랭킹 초기화 완료");
    }

    /**
     * 상품 랭킹 정보 DTO
     */
    public static class ProductRanking {
        private final int rank;          // 순위 (1부터 시작)
        private final Long productId;    // 상품 ID
        private final int orderCount;    // 주문 수량

        public ProductRanking(int rank, Long productId, int orderCount) {
            this.rank = rank;
            this.productId = productId;
            this.orderCount = orderCount;
        }

        public int getRank() {
            return rank;
        }

        public Long getProductId() {
            return productId;
        }

        public int getOrderCount() {
            return orderCount;
        }

        @Override
        public String toString() {
            return String.format("Rank %d: Product %d (Orders: %d)", rank, productId, orderCount);
        }
    }
}
