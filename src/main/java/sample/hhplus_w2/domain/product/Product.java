package sample.hhplus_w2.domain.product;

import jakarta.persistence.*;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Product 도메인 엔티티
 * SKU 제거, Product 단위 재고 관리
 */
@Entity
@Table(name = "product", indexes = {
        @Index(name = "idx_product_category", columnList = "category_id, created_at"),
        @Index(name = "idx_product_active", columnList = "is_active, created_at")
})
@Getter
public class Product {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "category_id")
    private Long categoryId;

    @Column(nullable = false, length = 255)
    private String name;

    @Column(length = 100)
    private String brand;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal price;

    @Column(name = "stock_qty", nullable = false)
    private Integer stockQty;

    @Version
    @Column(nullable = false)
    private Integer version; // 낙관적 락

    @Column(name = "is_active", nullable = false)
    private Boolean isActive;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    protected Product() {
    }

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
        if (this.version == null) {
            this.version = 0;
        }
        if (this.isActive == null) {
            this.isActive = true;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * 신규 상품 생성
     */
    public static Product create(Long categoryId, String name, String brand, String description,
                                  BigDecimal price, Integer stockQty) {
        if (price == null || price.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("가격은 0 이상이어야 합니다.");
        }
        if (stockQty == null || stockQty < 0) {
            throw new IllegalArgumentException("재고는 0 이상이어야 합니다.");
        }

        Product product = new Product();
        product.categoryId = categoryId;
        product.name = name;
        product.brand = brand;
        product.description = description;
        product.price = price;
        product.stockQty = stockQty;
        product.version = 0;
        product.isActive = true;
        product.createdAt = LocalDateTime.now();
        product.updatedAt = LocalDateTime.now();
        return product;
    }

    /**
     * ID 설정 (Repository에서 사용)
     */
    public void assignId(Long id) {
        if (this.id != null) {
            throw new IllegalStateException("ID는 이미 할당되었습니다.");
        }
        this.id = id;
    }

    /**
     * 상품 정보 업데이트
     */
    public void updateInfo(String name, String brand, String description, BigDecimal price) {
        if (price.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("가격은 0 이상이어야 합니다.");
        }
        this.name = name;
        this.brand = brand;
        this.description = description;
        this.price = price;
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * 재고 차감 (낙관적 락)
     * JPA @Version이 자동으로 동시성 제어
     * @param quantity 차감 수량
     */
    public void decreaseStock(Integer quantity) {
        if (this.stockQty < quantity) {
            throw new IllegalStateException("재고가 부족합니다.");
        }
        this.stockQty -= quantity;
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * 재고 증가 (반품, 입고 등)
     */
    public void increaseStock(Integer quantity) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("증가 수량은 0보다 커야 합니다.");
        }
        this.stockQty += quantity;
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * 재고 여부 확인
     */
    public boolean hasStock(Integer quantity) {
        return this.stockQty >= quantity;
    }

    /**
     * 상품 비활성화
     */
    public void deactivate() {
        this.isActive = false;
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * 상품 활성화
     */
    public void activate() {
        this.isActive = true;
        this.updatedAt = LocalDateTime.now();
    }
}
