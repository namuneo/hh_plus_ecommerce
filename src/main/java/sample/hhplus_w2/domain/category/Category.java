package sample.hhplus_w2.domain.category;

import lombok.Getter;

import java.time.LocalDateTime;

/**
 * Category 도메인 엔티티
 */
@Getter
public class Category {
    private Long id;
    private String name;
    private Boolean isActive;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    private Category() {
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