package sample.hhplus_w2.coupon.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Coupon", description = "쿠폰 API")
@RestController
@RequestMapping("/api/coupons")
public class CouponController {

    @Operation(summary = "쿠폰 목록 조회", description = "전체 쿠폰 목록을 조회합니다")
    @ApiResponse(responseCode = "200", description = "쿠폰 목록 조회 성공")
    @GetMapping
    public ResponseEntity<String> getCoupons() {
        return ResponseEntity.ok("쿠폰 목록 조회");
    }

    @Operation(summary = "쿠폰 생성", description = "새로운 쿠폰을 생성합니다 (운영자)")
    @ApiResponse(responseCode = "201", description = "쿠폰 생성 완료")
    @PostMapping
    public ResponseEntity<String> createCoupon(@RequestBody String requestBody) {
        return ResponseEntity.status(HttpStatus.CREATED).body("쿠폰 생성 완료");
    }

    @Operation(summary = "쿠폰 상세 조회", description = "특정 쿠폰의 상세 정보를 조회합니다")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "쿠폰 상세 조회 성공"),
        @ApiResponse(responseCode = "404", description = "쿠폰 없음")
    })
    @GetMapping("/{couponId}")
    public ResponseEntity<String> getCoupon(
            @Parameter(description = "쿠폰 ID", required = true) @PathVariable Long couponId) {
        return ResponseEntity.ok("쿠폰 상세 조회");
    }

    @Operation(summary = "선착순 쿠폰 발급", description = "선착순으로 쿠폰을 발급받습니다")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "쿠폰 발급 완료"),
        @ApiResponse(responseCode = "409", description = "발급 한도 초과 또는 중복 발급")
    })
    @PostMapping("/{couponId}/issue")
    public ResponseEntity<String> issueCoupon(
            @Parameter(description = "쿠폰 ID", required = true) @PathVariable Long couponId) {
        return ResponseEntity.status(HttpStatus.CREATED).body("쿠폰 발급 완료");
    }
}