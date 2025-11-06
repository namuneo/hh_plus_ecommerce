package sample.hhplus_w2.domain.stats;

import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 상품 판매 통계 (인기 상품 집계용)
 */
@Getter
public class ProductSalesStats {
    private Long id;
    private Long productId;
    private String productName;        // 상품명 (조회 편의성)
    private Integer salesCount;        // 판매 수량
    private BigDecimal salesAmount;    // 매출액
    private LocalDateTime aggregatedAt; // 집계 시각
    private Integer daysRange;         // 집계 기간 (일)

    private ProductSalesStats() {
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
