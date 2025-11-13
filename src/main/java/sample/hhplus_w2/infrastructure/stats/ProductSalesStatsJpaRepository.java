package sample.hhplus_w2.infrastructure.stats;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import sample.hhplus_w2.domain.stats.ProductSalesStats;

import java.util.List;
import java.util.Optional;

public interface ProductSalesStatsJpaRepository extends JpaRepository<ProductSalesStats, Long> {
    Optional<ProductSalesStats> findByProductIdAndDaysRange(Long productId, Integer daysRange);
    List<ProductSalesStats> findByDaysRangeOrderBySalesCountDesc(Integer daysRange, Pageable pageable);
    List<ProductSalesStats> findByDaysRangeOrderBySalesAmountDesc(Integer daysRange, Pageable pageable);
}
