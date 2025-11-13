package sample.hhplus_w2.service.stats;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sample.hhplus_w2.domain.order.Order;
import sample.hhplus_w2.domain.order.OrderItem;
import sample.hhplus_w2.domain.order.OrderStatus;
import sample.hhplus_w2.domain.product.Product;
import sample.hhplus_w2.domain.stats.ProductSalesStats;
import sample.hhplus_w2.repository.order.OrderItemRepository;
import sample.hhplus_w2.repository.order.OrderRepository;
import sample.hhplus_w2.repository.product.ProductRepository;
import sample.hhplus_w2.repository.stats.ProductSalesStatsRepository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 상품 판매 통계 서비스
 */
@Service
public class ProductStatsService {

    private final ProductSalesStatsRepository statsRepository;
    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final ProductRepository productRepository;

    public ProductStatsService(ProductSalesStatsRepository statsRepository,
                              OrderRepository orderRepository,
                              OrderItemRepository orderItemRepository,
                              ProductRepository productRepository) {
        this.statsRepository = statsRepository;
        this.orderRepository = orderRepository;
        this.orderItemRepository = orderItemRepository;
        this.productRepository = productRepository;
    }

    /**
     * 최근 N일간 인기 상품 TOP 조회 (판매량 기준)
     */
    @Transactional
    public List<ProductSalesStats> getTopProductsByPeriod(Integer days, int limit) {
        // 통계 갱신
        aggregateSalesStats(days);

        // TOP N 조회
        return statsRepository.findByDaysRangeOrderBySalesCountDesc(days, limit);
    }

    /**
     * 최근 N일간 인기 상품 TOP 조회 (매출액 기준)
     */
    @Transactional
    public List<ProductSalesStats> getTopProductsByRevenue(Integer days, int limit) {
        // 통계 갱신
        aggregateSalesStats(days);

        // TOP N 조회
        return statsRepository.findByDaysRangeOrderBySalesAmountDesc(days, limit);
    }

    /**
     * 판매 통계 집계
     * 최근 N일간의 결제 완료된 주문을 기반으로 상품별 판매량/매출 집계
     */
    @Transactional
    public void aggregateSalesStats(Integer days) {
        LocalDateTime fromDate = LocalDateTime.now().minusDays(days);

        // 최근 N일간 결제 완료된 주문 조회
        List<Order> paidOrders = orderRepository.findAll().stream()
                .filter(order -> OrderStatus.PAID.equals(order.getStatus()))
                .filter(order -> order.getCreatedAt().isAfter(fromDate))
                .collect(Collectors.toList());

        // 주문 아이템별로 집계
        Map<Long, SalesData> salesMap = new HashMap<>();

        for (Order order : paidOrders) {
            List<OrderItem> orderItems = orderItemRepository.findByOrderId(order.getId());

            for (OrderItem item : orderItems) {
                Long productId = item.getProductId();
                salesMap.putIfAbsent(productId, new SalesData());

                SalesData data = salesMap.get(productId);
                data.salesCount += item.getQty();
                data.salesAmount = data.salesAmount.add(item.getSubtotal());
            }
        }

        // 통계 저장/업데이트
        for (Map.Entry<Long, SalesData> entry : salesMap.entrySet()) {
            Long productId = entry.getKey();
            SalesData data = entry.getValue();

            Optional<Product> productOpt = productRepository.findById(productId);
            String productName = productOpt.map(Product::getName).orElse("Unknown");

            Optional<ProductSalesStats> existingStats =
                    statsRepository.findByProductIdAndDaysRange(productId, days);

            if (existingStats.isPresent()) {
                ProductSalesStats stats = existingStats.get();
                stats.update(data.salesCount, data.salesAmount);
                statsRepository.save(stats);
            } else {
                ProductSalesStats newStats = ProductSalesStats.create(
                        productId, productName, data.salesCount, data.salesAmount, days);
                statsRepository.save(newStats);
            }
        }
    }

    /**
     * 집계 데이터 임시 저장용 내부 클래스
     */
    private static class SalesData {
        int salesCount = 0;
        BigDecimal salesAmount = BigDecimal.ZERO;
    }
}
