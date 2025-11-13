package sample.hhplus_w2.repository.stats.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;
import sample.hhplus_w2.domain.stats.ProductSalesStats;
import sample.hhplus_w2.infrastructure.stats.ProductSalesStatsJpaRepository;
import sample.hhplus_w2.repository.stats.ProductSalesStatsRepository;

import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class ProductSalesStatsRepositoryImpl implements ProductSalesStatsRepository {
    private final ProductSalesStatsJpaRepository jpaRepository;

    @Override
    public ProductSalesStats save(ProductSalesStats stats) {
        return jpaRepository.save(stats);
    }

    @Override
    public Optional<ProductSalesStats> findByProductIdAndDaysRange(Long productId, Integer daysRange) {
        return jpaRepository.findByProductIdAndDaysRange(productId, daysRange);
    }

    @Override
    public List<ProductSalesStats> findByDaysRangeOrderBySalesCountDesc(Integer daysRange, int limit) {
        return jpaRepository.findByDaysRangeOrderBySalesCountDesc(daysRange, PageRequest.of(0, limit));
    }

    @Override
    public List<ProductSalesStats> findByDaysRangeOrderBySalesAmountDesc(Integer daysRange, int limit) {
        return jpaRepository.findByDaysRangeOrderBySalesAmountDesc(daysRange, PageRequest.of(0, limit));
    }

    @Override
    public void deleteAll() {
        jpaRepository.deleteAll();
    }
}
