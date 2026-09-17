package com.maesamco.user.infrastructure.persistence;

import com.maesamco.user.domain.entity.User;
import com.maesamco.user.domain.entity.UserRole;
import com.maesamco.user.domain.entity.UserStatus;
import com.maesamco.user.domain.repository.UserRepository;
import com.maesamco.user.global.exception.BusinessException;
import com.maesamco.user.global.exception.ErrorCode;
import com.maesamco.user.global.util.DataIntegrityViolations;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import java.util.List;
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

    private static final String ACTIVE_EMAIL_UNIQUE_INDEX =
            "uk_p_users_active_email_lookup_hash";

    private static final String ACTIVE_NICKNAME_UNIQUE_INDEX =
            "uk_p_users_active_nickname_ci";

    /**
     * 실제 JPA 저장과 조회를 담당하는 내부 Repository입니다.
     */
    private final SpringDataUserRepository springDataUserRepository;

    /**
     * {@inheritDoc}
     *
     * <p>저장 내용을 즉시 flush하여 동시 회원가입 중 발생하는
     * 이메일·닉네임 UNIQUE 충돌을 이 메서드 안에서 감지합니다.</p>
     */
    @Override
    public User save(User user) {
        try {
            return springDataUserRepository.saveAndFlush(user);
        } catch (DataIntegrityViolationException exception) {
            if (DataIntegrityViolations.isUniqueViolation(
                    exception,
                    ACTIVE_EMAIL_UNIQUE_INDEX
            )) {
                throw new BusinessException(
                        ErrorCode.USER_DUPLICATE_EMAIL
                );
            }

            if (DataIntegrityViolations.isUniqueViolation(
                    exception,
                    ACTIVE_NICKNAME_UNIQUE_INDEX
            )) {
                throw new BusinessException(
                        ErrorCode.USER_DUPLICATE_NICKNAME
                );
            }

            throw exception;
        }
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
    public Optional<User> findByIdForUpdate(
            UUID userId
    ) {
        return springDataUserRepository.findByIdForUpdate(
                userId
        );
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
    public Optional<User> findByEmailLookupHashForUpdate(
            String emailLookupHash
    ) {
        return springDataUserRepository
                .findByEmailLookupHashForUpdate(
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
    public boolean existsByNicknameIgnoreCase(String nickname) {
        return springDataUserRepository
                .existsByNicknameIgnoreCase(nickname);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<UUID> findQuizTargetUserIds(
            UUID cursor,
            int limit
    ) {
        validatePositiveLimit(
                limit
        );

        Pageable pageable =
                PageRequest.of(
                        0,
                        limit
                );

        List<UUID> userIds;

        if (cursor == null) {
            userIds =
                    springDataUserRepository
                            .findQuizTargetUserIds(
                                    UserStatus.ACTIVE,
                                    UserRole.USER,
                                    pageable
                            );
        } else {
            userIds =
                    springDataUserRepository
                            .findQuizTargetUserIdsAfter(
                                    UserStatus.ACTIVE,
                                    UserRole.USER,
                                    cursor,
                                    pageable
                            );
        }

        return List.copyOf(
                userIds
        );
    }

    /**
     * DB 조회 제한이 유효한지 방어적으로 검증합니다.
     */
    private void validatePositiveLimit(
            int limit
    ) {
        if (limit <= 0) {
            throw new BusinessException(
                    ErrorCode.INVALID_INPUT_VALUE,
                    "Daily Quiz 대상 사용자 조회 제한은 1 이상이어야 합니다."
            );
        }
    }
}
