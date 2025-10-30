package sample.hhplus_w2.sku.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Tag(name = "SKU", description = "SKU 관리 API (운영자)")
@RestController
@RequestMapping("/api")
public class SkuController {

    @Operation(summary = "SKU 등록", description = "특정 상품에 대한 SKU를 등록합니다 (운영자)")
    @ApiResponse(responseCode = "201", description = "SKU 등록 완료")
    @PostMapping("/products/{productId}/skus")
    public ResponseEntity<String> createSku(
            @Parameter(description = "상품 ID", required = true) @PathVariable Long productId,
            @RequestBody String requestBody) {
        return ResponseEntity.status(HttpStatus.CREATED).body("SKU 등록 완료");
    }

    @Operation(summary = "SKU 수정", description = "SKU의 가격, 재고, 활성 상태 등을 수정합니다")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "SKU 수정 완료"),
        @ApiResponse(responseCode = "409", description = "동시성 충돌")
    })
    @PatchMapping("/skus/{skuId}")
    public ResponseEntity<String> updateSku(
            @Parameter(description = "SKU ID", required = true) @PathVariable Long skuId,
            @RequestBody String requestBody) {
        return ResponseEntity.ok("SKU 수정 완료");
    }
}