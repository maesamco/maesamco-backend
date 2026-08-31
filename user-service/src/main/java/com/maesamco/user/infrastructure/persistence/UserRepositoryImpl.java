package com.maesamco.user.infrastructure.persistence;

import com.maesamco.user.domain.entity.User;
import com.maesamco.user.domain.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * 도메인의 UserRepository를 Spring Data JPA로 구현하는 영속성 어댑터입니다.
 *
 * <p>도메인 계층의 Repository 요청을 SpringDataUserRepository에 위임하여
 * 도메인 계층과 JPA 기술 사이의 의존성을 분리합니다.</p>
 */
@Repository
@RequiredArgsConstructor
public class UserRepositoryImpl implements UserRepository {

    /**
     * 실제 JPA 저장과 조회를 담당하는 내부 Repository입니다.
     */
    private final SpringDataUserRepository springDataUserRepository;

    /**
     * {@inheritDoc}
     */
    @Override
    public User save(User user) {
        return springDataUserRepository.save(user);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Optional<User> findById(UUID userId) {
        return springDataUserRepository.findById(userId);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Optional<User> findByEmailLookupHash(
            String emailLookupHash
    ) {
        return springDataUserRepository.findByEmailLookupHash(
                emailLookupHash
        );
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean existsByEmailLookupHash(String emailLookupHash) {
        return springDataUserRepository.existsByEmailLookupHash(
                emailLookupHash
        );
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean existsByNickname(String nickname) {
        return springDataUserRepository.existsByNickname(nickname);
    }
}