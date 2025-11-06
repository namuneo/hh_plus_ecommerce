package sample.hhplus_w2.repository.user;

import sample.hhplus_w2.domain.user.User;

import java.util.List;
import java.util.Optional;

/**
 * User Repository 인터페이스
 */
public interface UserRepository {

    /**
     * 사용자 저장
     */
    User save(User user);

    /**
     * ID로 사용자 조회
     */
    Optional<User> findById(Long id);

    /**
     * 이메일로 사용자 조회
     */
    Optional<User> findByEmail(String email);

    /**
     * 모든 사용자 조회
     */
    List<User> findAll();

    /**
     * 활성 사용자 조회
     */
    List<User> findByIsActive(Boolean isActive);

    /**
     * 사용자 삭제
     */
    void delete(Long id);

    /**
     * 모든 사용자 삭제 (테스트용)
     */
    void deleteAll();
}
