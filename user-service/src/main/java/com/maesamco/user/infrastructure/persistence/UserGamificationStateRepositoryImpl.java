package com.maesamco.user.infrastructure.persistence;

import com.maesamco.user.domain.entity.UserGamificationState;
import com.maesamco.user.domain.repository.UserGamificationStateRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * 도메인의 UserGamificationStateRepository를 Spring Data JPA로 구현하는 영속성 어댑터입니다.
 */
@Repository
@RequiredArgsConstructor
public class UserGamificationStateRepositoryImpl
        implements UserGamificationStateRepository {

    private final SpringDataUserGamificationStateRepository
            springDataRepository;

    /**
     * {@inheritDoc}
     */
    @Override
    public UserGamificationState save(UserGamificationState state) {
        return springDataRepository.save(state);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Optional<UserGamificationState> findByUserId(UUID userId) {
        return springDataRepository.findById(userId);
    }
}
