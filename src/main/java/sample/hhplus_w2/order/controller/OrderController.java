package sample.hhplus_w2.order.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Order", description = "주문/결제 API")
@RestController
@RequestMapping("/api/orders")
public class OrderController {

    @Operation(summary = "주문 생성", description = "장바구니에서 주문을 생성합니다 (결제 전)")
    @ApiResponse(responseCode = "201", description = "주문 생성 완료")
    @PostMapping
    public ResponseEntity<String> createOrder(@RequestBody(required = false) String requestBody) {
        return ResponseEntity.status(HttpStatus.CREATED).body("주문 생성 완료");
    }

    @Operation(summary = "주문 목록 조회", description = "현재 사용자의 전체 주문 목록을 조회합니다")
    @ApiResponse(responseCode = "200", description = "주문 목록 조회 성공")
    @GetMapping
    public ResponseEntity<String> getOrders() {
        return ResponseEntity.ok("주문 목록 조회");
    }

    @Operation(summary = "주문 상세 조회", description = "특정 주문의 상세 정보를 조회합니다")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "주문 상세 조회 성공"),
        @ApiResponse(responseCode = "404", description = "주문 없음")
    })
    @GetMapping("/{orderId}")
    public ResponseEntity<String> getOrder(
            @Parameter(description = "주문 ID", required = true) @PathVariable Long orderId) {
        return ResponseEntity.ok("주문 상세 조회");
    }

    @Operation(summary = "주문 상태 이력 조회", description = "주문의 상태 변경 이력을 조회합니다")
    @ApiResponse(responseCode = "200", description = "주문 상태 이력 조회 성공")
    @GetMapping("/{orderId}/history")
    public ResponseEntity<String> getOrderHistory(
            @Parameter(description = "주문 ID", required = true) @PathVariable Long orderId) {
        return ResponseEntity.ok("주문 상태 이력 조회");
    }

    @Operation(summary = "쿠폰 적용", description = "주문에 쿠폰을 적용합니다 (결제 전)")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "쿠폰 적용 완료"),
        @ApiResponse(responseCode = "422", description = "쿠폰 유효성 실패")
    })
    @PostMapping("/{orderId}/apply-coupon")
    public ResponseEntity<String> applyCoupon(
            @Parameter(description = "주문 ID", required = true) @PathVariable Long orderId,
            @RequestBody String requestBody) {
        return ResponseEntity.ok("쿠폰 적용 완료");
    }

    @Operation(
        summary = "결제 처리",
        description = "지갑 잔액으로 결제 처리하고 재고를 차감합니다 (멱등성 보장)"
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "결제 완료"),
        @ApiResponse(responseCode = "402", description = "잔액 부족"),
        @ApiResponse(responseCode = "409", description = "재고 경합/동시성 실패")
    })
    @PostMapping("/{orderId}/pay")
    public ResponseEntity<String> pay(
            @Parameter(description = "주문 ID", required = true) @PathVariable Long orderId,
            @Parameter(
                description = "중복 결제 방지를 위한 멱등 키",
                required = true,
                schema = @Schema(type = "string")
            ) @RequestHeader("Idempotency-Key") String idempotencyKey) {
        return ResponseEntity.ok("결제 완료");
    }
}