package sample.hhplus_w2.cart.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Cart", description = "장바구니 API")
@RestController
@RequestMapping("/api/cart")
public class CartController {

    @Operation(summary = "장바구니 조회", description = "현재 사용자의 장바구니를 조회합니다")
    @ApiResponse(responseCode = "200", description = "장바구니 조회 성공")
    @GetMapping
    public ResponseEntity<String> getCart() {
        return ResponseEntity.ok("장바구니 조회");
    }

    @Operation(summary = "장바구니에 SKU 추가", description = "장바구니에 상품(SKU)을 추가합니다")
    @ApiResponse(responseCode = "201", description = "장바구니 항목 추가 완료")
    @PostMapping("/items")
    public ResponseEntity<String> addCartItem(@RequestBody String requestBody) {
        return ResponseEntity.status(HttpStatus.CREATED).body("장바구니 항목 추가 완료");
    }

    @Operation(summary = "장바구니 항목 수량 변경", description = "장바구니 항목의 수량을 변경합니다")
    @ApiResponse(responseCode = "200", description = "장바구니 항목 수정 완료")
    @PatchMapping("/items/{itemId}")
    public ResponseEntity<String> updateCartItem(
            @Parameter(description = "장바구니 항목 ID", required = true) @PathVariable Long itemId,
            @RequestBody String requestBody) {
        return ResponseEntity.ok("장바구니 항목 수정 완료");
    }

    @Operation(summary = "장바구니 항목 삭제", description = "장바구니에서 특정 항목을 삭제합니다")
    @ApiResponse(responseCode = "204", description = "장바구니 항목 삭제 완료")
    @DeleteMapping("/items/{itemId}")
    public ResponseEntity<String> deleteCartItem(
            @Parameter(description = "장바구니 항목 ID", required = true) @PathVariable Long itemId) {
        return ResponseEntity.status(HttpStatus.NO_CONTENT).body("장바구니 항목 삭제 완료");
    }
}