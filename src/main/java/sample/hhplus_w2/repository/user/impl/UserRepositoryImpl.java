package sample.hhplus_w2.repository.user.impl;

import org.springframework.stereotype.Repository;
import sample.hhplus_w2.domain.user.User;
import sample.hhplus_w2.repository.user.UserRepository;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

/**
 * User Repository 구현체
 */
@Repository
public class UserRepositoryImpl implements UserRepository {

    private final Map<Long, User> store = new ConcurrentHashMap<>();
    private final AtomicLong idGenerator = new AtomicLong(1);

    @Override
    public User save(User user) {
        if (user.getId() == null) {
            user.assignId(idGenerator.getAndIncrement());
        }
        store.put(user.getId(), user);
        return user;
    }

    @Override
    public Optional<User> findById(Long id) {
        return Optional.ofNullable(store.get(id));
    }

    @Override
    public Optional<User> findByEmail(String email) {
        return store.values().stream()
                .filter(user -> user.getEmail().equals(email))
                .findFirst();
    }

    @Override
    public List<User> findAll() {
        return store.values().stream()
                .collect(Collectors.toList());
    }

    @Override
    public List<User> findByIsActive(Boolean isActive) {
        return store.values().stream()
                .filter(user -> user.getIsActive().equals(isActive))
                .collect(Collectors.toList());
    }

    @Override
    public void delete(Long id) {
        store.remove(id);
    }

    @Override
    public void deleteAll() {
        store.clear();
    }
}
