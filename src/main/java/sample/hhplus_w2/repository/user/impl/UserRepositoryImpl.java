package sample.hhplus_w2.repository.user.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import sample.hhplus_w2.domain.user.User;
import sample.hhplus_w2.infrastructure.user.UserJpaRepository;
import sample.hhplus_w2.repository.user.UserRepository;

import java.util.List;
import java.util.Optional;

/**
 * User Repository 구현체
 */
@Repository
@RequiredArgsConstructor
public class UserRepositoryImpl implements UserRepository {
    private final UserJpaRepository jpaRepository;

    @Override
    public User save(User user) {
        return jpaRepository.save(user);
    }

    @Override
    public Optional<User> findById(Long id) {
        return jpaRepository.findById(id);
    }

    @Override
    public Optional<User> findByEmail(String email) {
        return jpaRepository.findByEmail(email);
    }

    @Override
    public List<User> findAll() {
        return jpaRepository.findAll();
    }

    @Override
    public List<User> findByIsActive(Boolean isActive) {
        return jpaRepository.findByIsActive(isActive);
    }

    @Override
    public void delete(Long id) {
        jpaRepository.deleteById(id);
    }

    @Override
    public void deleteAll() {
        jpaRepository.deleteAll();
    }
}
