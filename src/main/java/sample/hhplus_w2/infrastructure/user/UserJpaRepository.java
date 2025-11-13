package sample.hhplus_w2.infrastructure.user;

import org.springframework.data.jpa.repository.JpaRepository;
import sample.hhplus_w2.domain.user.User;

import java.util.List;
import java.util.Optional;

public interface UserJpaRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmail(String email);
    List<User> findByIsActive(Boolean isActive);
}
