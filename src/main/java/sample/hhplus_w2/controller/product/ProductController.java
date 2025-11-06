package sample.hhplus_w2.controller.product;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import sample.hhplus_w2.domain.product.Product;
import sample.hhplus_w2.service.product.ProductService;

import java.math.BigDecimal;
import java.util.List;

/**
 * Product Controller
 */
@RestController
@RequestMapping("/api/products")
public class ProductController {

    private final ProductService productService;

    public ProductController(ProductService productService) {
        this.productService = productService;
    }

    /**
     * 상품 목록 조회
     */
    @GetMapping
    public ResponseEntity<List<Product>> getProducts(@RequestParam(required = false) Long categoryId) {
        if (categoryId != null) {
            return ResponseEntity.ok(productService.getProductsByCategory(categoryId));
        }
        return ResponseEntity.ok(productService.getActiveProducts());
    }

    /**
     * 상품 상세 조회
     */
    @GetMapping("/{id}")
    public ResponseEntity<Product> getProduct(@PathVariable Long id) {
        return ResponseEntity.ok(productService.getProduct(id));
    }

    /**
     * 상품 등록
     */
    @PostMapping
    public ResponseEntity<Product> createProduct(
            @RequestParam Long categoryId,
            @RequestParam String name,
            @RequestParam String brand,
            @RequestParam String description,
            @RequestParam BigDecimal price,
            @RequestParam Integer stockQty) {
        Product product = productService.createProduct(categoryId, name, brand, description, price, stockQty);
        return ResponseEntity.ok(product);
    }

    /**
     * 상품 정보 수정
     */
    @PutMapping("/{id}")
    public ResponseEntity<Product> updateProduct(
            @PathVariable Long id,
            @RequestParam String name,
            @RequestParam String brand,
            @RequestParam String description,
            @RequestParam BigDecimal price) {
        Product product = productService.updateProduct(id, name, brand, description, price);
        return ResponseEntity.ok(product);
    }

    /**
     * 재고 증가
     */
    @PostMapping("/{id}/stock/increase")
    public ResponseEntity<Void> increaseStock(
            @PathVariable Long id,
            @RequestParam Integer quantity) {
        productService.increaseStock(id, quantity);
        return ResponseEntity.ok().build();
    }
}
