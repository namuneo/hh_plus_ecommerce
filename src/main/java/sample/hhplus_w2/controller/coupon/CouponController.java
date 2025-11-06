package sample.hhplus_w2.controller.coupon;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import sample.hhplus_w2.domain.coupon.Coupon;
import sample.hhplus_w2.domain.coupon.CouponUser;
import sample.hhplus_w2.service.coupon.CouponService;

import java.util.List;

@RestController
@RequestMapping("/api/coupons")
public class CouponController {

    private final CouponService couponService;

    public CouponController(CouponService couponService) {
        this.couponService = couponService;
    }

    @GetMapping
    public ResponseEntity<List<Coupon>> getPublishedCoupons() {
        List<Coupon> coupons = couponService.getPublishedCoupons();
        return ResponseEntity.ok(coupons);
    }

    @GetMapping("/{id}")
    public ResponseEntity<Coupon> getCoupon(@PathVariable Long id) {
        Coupon coupon = couponService.getCoupon(id);
        return ResponseEntity.ok(coupon);
    }

    @PostMapping("/{couponId}/issue")
    public ResponseEntity<CouponUser> issueCoupon(
            @PathVariable Long couponId,
            @RequestParam Long userId) {
        CouponUser couponUser = couponService.issueCoupon(couponId, userId);
        return ResponseEntity.ok(couponUser);
    }

    @GetMapping("/user/{userId}")
    public ResponseEntity<List<CouponUser>> getUserCoupons(@PathVariable Long userId) {
        List<CouponUser> coupons = couponService.getUserCoupons(userId);
        return ResponseEntity.ok(coupons);
    }

    @PostMapping("/{couponId}/publish")
    public ResponseEntity<Void> publishCoupon(@PathVariable Long couponId) {
        couponService.publishCoupon(couponId);
        return ResponseEntity.ok().build();
    }
}
