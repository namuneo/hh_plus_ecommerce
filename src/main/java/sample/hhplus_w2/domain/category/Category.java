package sample.hhplus_w2.domain.category;

import jakarta.persistence.*;
import lombok.Getter;

import java.time.LocalDateTime;

/**
 * Category 도메인 엔티티
 */
@Entity
@Table(name = "category")
@Getter
public class Category {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    protected Category() {
    }

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * 신규 카테고리 생성
     */
    public static Category create(String name) {
        Category category = new Category();
        category.name = name;
        category.isActive = true;
        category.createdAt = LocalDateTime.now();
        category.updatedAt = LocalDateTime.now();
        return category;
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
     * 카테고리 이름 변경
     */
    public void updateName(String name) {
        this.name = name;
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * 카테고리 비활성화
     */
    public void deactivate() {
        this.isActive = false;
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * 카테고리 활성화
     */
    public void activate() {
        this.isActive = true;
        this.updatedAt = LocalDateTime.now();
    }
}