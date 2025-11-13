package sample.hhplus_w2.domain.stats;

import jakarta.persistence.*;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 상품 판매 통계 (인기 상품 집계용)
 */
@Entity
@Table(name = "product_sales_stats", indexes = {
        @Index(name = "idx_stats_days_sales", columnList = "days_range, sales_count"),
        @Index(name = "idx_stats_days_revenue", columnList = "days_range, sales_amount"),
        @Index(name = "uk_stats_product_days", columnList = "product_id, days_range", unique = true)
})
@Getter
public class ProductSalesStats {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(name = "product_name", nullable = false, length = 255)
    private String productName;        // 상품명 (조회 편의성)

    @Column(name = "sales_count", nullable = false)
    private Integer salesCount;        // 판매 수량

    @Column(name = "sales_amount", nullable = false, precision = 10, scale = 2)
    private BigDecimal salesAmount;    // 매출액

    @Column(name = "aggregated_at", nullable = false)
    private LocalDateTime aggregatedAt; // 집계 시각

    @Column(name = "days_range", nullable = false)
    private Integer daysRange;         // 집계 기간 (일)

    protected ProductSalesStats() {
    }

    /**
     * 통계 생성
     */
    public static ProductSalesStats create(Long productId, String productName,
                                          Integer salesCount, BigDecimal salesAmount,
                                          Integer daysRange) {
        ProductSalesStats stats = new ProductSalesStats();
        stats.productId = productId;
        stats.productName = productName;
        stats.salesCount = salesCount != null ? salesCount : 0;
        stats.salesAmount = salesAmount != null ? salesAmount : BigDecimal.ZERO;
        stats.aggregatedAt = LocalDateTime.now();
        stats.daysRange = daysRange;
        return stats;
    }

    /**
     * ID 할당 (Repository에서 사용)
     */
    public void assignId(Long id) {
        if (this.id != null) {
            throw new IllegalStateException("ID는 이미 할당되었습니다.");
        }
        this.id = id;
    }

    /**
     * 통계 업데이트
     */
    public void update(Integer salesCount, BigDecimal salesAmount) {
        this.salesCount = salesCount;
        this.salesAmount = salesAmount;
        this.aggregatedAt = LocalDateTime.now();
    }
}
