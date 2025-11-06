package sample.hhplus_w2.repository.stats.impl;

import org.springframework.stereotype.Repository;
import sample.hhplus_w2.domain.stats.ProductSalesStats;
import sample.hhplus_w2.repository.stats.ProductSalesStatsRepository;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

@Repository
public class ProductSalesStatsRepositoryImpl implements ProductSalesStatsRepository {

    private final Map<Long, ProductSalesStats> store = new ConcurrentHashMap<>();
    private final AtomicLong idGenerator = new AtomicLong(1);

    @Override
    public ProductSalesStats save(ProductSalesStats stats) {
        if (stats.getId() == null) {
            stats.assignId(idGenerator.getAndIncrement());
        }
        store.put(stats.getId(), stats);
        return stats;
    }

    @Override
    public Optional<ProductSalesStats> findByProductIdAndDaysRange(Long productId, Integer daysRange) {
        return store.values().stream()
                .filter(stats -> stats.getProductId().equals(productId) && stats.getDaysRange().equals(daysRange))
                .findFirst();
    }

    @Override
    public List<ProductSalesStats> findByDaysRangeOrderBySalesCountDesc(Integer daysRange, int limit) {
        return store.values().stream()
                .filter(stats -> stats.getDaysRange().equals(daysRange))
                .sorted(Comparator.comparing(ProductSalesStats::getSalesCount).reversed())
                .limit(limit)
                .collect(Collectors.toList());
    }

    @Override
    public List<ProductSalesStats> findByDaysRangeOrderBySalesAmountDesc(Integer daysRange, int limit) {
        return store.values().stream()
                .filter(stats -> stats.getDaysRange().equals(daysRange))
                .sorted(Comparator.comparing(ProductSalesStats::getSalesAmount).reversed())
                .limit(limit)
                .collect(Collectors.toList());
    }

    @Override
    public void deleteAll() {
        store.clear();
    }
}
