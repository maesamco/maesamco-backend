package com.maesamco.user.application.service;

import com.maesamco.user.domain.entity.UserInterestConcept;
import com.maesamco.user.domain.repository.UserInterestConceptRepository;
import com.maesamco.user.global.exception.BusinessException;
import com.maesamco.user.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * 사용자 관심 개념의 등록과 조회 유즈케이스를 처리하는
 * Application Service입니다.
 *
 * <p>도메인 객체의 생성과 Repository 호출을 조율하고,
 * 동일한 사용자와 개념의 중복 등록을 방지합니다.</p>
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserInterestConceptService {

    /**
     * 사용자 관심 개념의 저장과 조회를 담당하는
     * 도메인 Repository입니다.
     */
    private final UserInterestConceptRepository
            interestConceptRepository;

    /**
     * 사용자의 관심 개념을 등록합니다.
     *
     * <p>활성 상태의 동일한 사용자·개념 조합이 이미 존재하면
     * 중복 등록 예외를 발생시킵니다.</p>
     *
     * @param userId 사용자 식별자
     * @param conceptId Content Service의 개념 식별자
     * @return 등록된 사용자 관심 개념
     */
    @Transactional
    public UserInterestConcept register(
            UUID userId,
            UUID conceptId
    ) {
        validateNotDuplicated(
                userId,
                conceptId
        );

        UserInterestConcept interestConcept =
                UserInterestConcept.create(
                        userId,
                        conceptId
                );

        return interestConceptRepository.save(
                interestConcept
        );
    }

    /**
     * 특정 사용자가 등록한 활성 관심 개념 목록을 조회합니다.
     *
     * @param userId 사용자 식별자
     * @return 사용자의 활성 관심 개념 목록
     */
    public List<UserInterestConcept> getAllByUserId(
            UUID userId
    ) {
        return interestConceptRepository.findAllByUserId(
                userId
        );
    }

    /**
     * 동일한 사용자와 개념 조합이 이미 등록되어 있는지 검증합니다.
     */
    private void validateNotDuplicated(
            UUID userId,
            UUID conceptId
    ) {
        boolean alreadyExists =
                interestConceptRepository
                        .existsByUserIdAndConceptId(
                                userId,
                                conceptId
                        );

        if (alreadyExists) {
            throw new BusinessException(
                    ErrorCode.USER_INTEREST_CONCEPT_ALREADY_EXISTS
            );
        }
    }
}