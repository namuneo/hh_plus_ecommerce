package sample.hhplus_w2.repository.stats;

import sample.hhplus_w2.domain.stats.ProductSalesStats;

import java.util.List;
import java.util.Optional;

/**
 * 상품 판매 통계 Repository
 */
public interface ProductSalesStatsRepository {
    ProductSalesStats save(ProductSalesStats stats);
    Optional<ProductSalesStats> findByProductIdAndDaysRange(Long productId, Integer daysRange);
    List<ProductSalesStats> findByDaysRangeOrderBySalesCountDesc(Integer daysRange, int limit);
    List<ProductSalesStats> findByDaysRangeOrderBySalesAmountDesc(Integer daysRange, int limit);
    void deleteAll();
}
