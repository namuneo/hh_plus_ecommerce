package sample.hhplus_w2.controller.stats;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import sample.hhplus_w2.domain.stats.ProductSalesStats;
import sample.hhplus_w2.service.stats.ProductStatsService;

import java.util.List;

@RestController
@RequestMapping("/api/products/stats")
public class ProductStatsController {

    private final ProductStatsService productStatsService;

    public ProductStatsController(ProductStatsService productStatsService) {
        this.productStatsService = productStatsService;
    }

    @GetMapping("/popular")
    public ResponseEntity<List<ProductSalesStats>> getPopularProducts(
            @RequestParam(defaultValue = "3") Integer days,
            @RequestParam(defaultValue = "5") Integer limit) {
        List<ProductSalesStats> stats = productStatsService.getTopProductsByPeriod(days, limit);
        return ResponseEntity.ok(stats);
    }

    @GetMapping("/revenue")
    public ResponseEntity<List<ProductSalesStats>> getTopRevenueProducts(
            @RequestParam(defaultValue = "3") Integer days,
            @RequestParam(defaultValue = "5") Integer limit) {
        List<ProductSalesStats> stats = productStatsService.getTopProductsByRevenue(days, limit);
        return ResponseEntity.ok(stats);
    }
}
