package com.maesamco.user.infrastructure.persistence;

import com.maesamco.user.domain.entity.UserGamificationState;
import com.maesamco.user.domain.repository.UserGamificationStateRepository;
import com.maesamco.user.global.exception.BusinessException;
import com.maesamco.user.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
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
        try {
            /*
             * 낙관적 락 충돌이 이 메서드 안에서 발생하도록 즉시 flush합니다.
             * 그래야 기술 예외를 도메인에서 사용하는 409 예외로 변환할 수 있습니다.
             */
            return springDataRepository.saveAndFlush(state);
        } catch (ObjectOptimisticLockingFailureException exception) {
            throw new BusinessException(
                    ErrorCode.GAMIFICATION_STATE_CONFLICT
            );
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Optional<UserGamificationState> findByUserId(UUID userId) {
        return springDataRepository.findById(userId);
    }
}
