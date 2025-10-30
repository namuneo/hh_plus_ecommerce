package sample.hhplus_w2.product.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Product", description = "상품 API")
@RestController
@RequestMapping("/api/products")
public class ProductController {

    @Operation(summary = "상품 목록 조회", description = "검색어, 카테고리, 정렬 조건으로 상품 목록을 조회합니다")
    @ApiResponse(responseCode = "200", description = "상품 목록 조회 성공")
    @GetMapping
    public ResponseEntity<String> getProducts(
            @Parameter(description = "검색어") @RequestParam(required = false) String q,
            @Parameter(description = "카테고리 ID") @RequestParam(required = false) Long categoryId,
            @Parameter(description = "정렬 조건 (POPULAR, LATEST, PRICE_ASC, PRICE_DESC)") @RequestParam(required = false) String sort) {
        return ResponseEntity.ok("상품 목록 조회");
    }

    @Operation(summary = "상품 상세 조회", description = "상품 ID로 상품 상세 정보와 SKU를 조회합니다")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "상품 상세 조회 성공"),
        @ApiResponse(responseCode = "404", description = "상품 없음")
    })
    @GetMapping("/{productId}")
    public ResponseEntity<String> getProduct(
            @Parameter(description = "상품 ID", required = true) @PathVariable Long productId) {
        return ResponseEntity.ok("상품 상세 조회");
    }

    @Operation(summary = "인기 상품 TopN 조회", description = "최근 N일 기준 인기 상품을 조회합니다 (기본 3일, Top 5)")
    @ApiResponse(responseCode = "200", description = "인기 상품 조회 성공")
    @GetMapping("/popular")
    public ResponseEntity<String> getPopularProducts(
            @Parameter(description = "조회 기간 (일)", example = "3") @RequestParam(defaultValue = "3") Integer days,
            @Parameter(description = "조회 개수", example = "5") @RequestParam(defaultValue = "5") Integer limit) {
        return ResponseEntity.ok("인기 상품 TopN 조회");
    }
}