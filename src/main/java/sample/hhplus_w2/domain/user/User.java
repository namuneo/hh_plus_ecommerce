package sample.hhplus_w2.domain.user;

import jakarta.persistence.*;
import lombok.Getter;

import java.time.LocalDateTime;

/**
 * User 도메인 엔티티
 */
@Entity
@Table(name = "user", indexes = {
        @Index(name = "idx_user_email", columnList = "email")
})
@Getter
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false, length = 100)
    private String email;

    @Column(name = "password_hash", length = 255)
    private String passwordHash;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(length = 20)
    private String phone;

    @Column(name = "address_line1", length = 255)
    private String addressLine1;

    @Column(name = "address_line2", length = 255)
    private String addressLine2;

    @Column(name = "postal_code", length = 20)
    private String postalCode;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    protected User() {
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

    private void oldConstructor() {
    }

    /**
     * 신규 사용자 생성
     */
    public static User create(String email, String passwordHash, String name, String phone,
                              String addressLine1, String addressLine2, String postalCode) {
        User user = new User();
        user.email = email;
        user.passwordHash = passwordHash;
        user.name = name;
        user.phone = phone;
        user.addressLine1 = addressLine1;
        user.addressLine2 = addressLine2;
        user.postalCode = postalCode;
        user.isActive = true;
        user.createdAt = LocalDateTime.now();
        user.updatedAt = LocalDateTime.now();
        return user;
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
     * 사용자 정보 업데이트
     */
    public void updateInfo(String name, String phone, String addressLine1, String addressLine2, String postalCode) {
        this.name = name;
        this.phone = phone;
        this.addressLine1 = addressLine1;
        this.addressLine2 = addressLine2;
        this.postalCode = postalCode;
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * 사용자 비활성화
     */
    public void deactivate() {
        this.isActive = false;
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * 사용자 활성화
     */
    public void activate() {
        this.isActive = true;
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * 전체 주소 반환
     */
    public String getFullAddress() {
        if (addressLine2 == null || addressLine2.trim().isEmpty()) {
            return String.format("[%s] %s", postalCode, addressLine1);
        }
        return String.format("[%s] %s %s", postalCode, addressLine1, addressLine2);
    }
}
