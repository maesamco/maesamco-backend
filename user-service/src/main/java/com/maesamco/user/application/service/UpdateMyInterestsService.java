package com.maesamco.user.application.service;

import com.maesamco.user.application.port.ConceptValidationPort;
import com.maesamco.user.domain.entity.User;
import com.maesamco.user.domain.entity.UserInterestConcept;
import com.maesamco.user.domain.repository.UserInterestConceptRepository;
import com.maesamco.user.domain.repository.UserRepository;
import com.maesamco.user.global.exception.BusinessException;
import com.maesamco.user.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * 로그인 사용자의 관심 개념 목록 전체 교체를 처리합니다.
 */
@Service
@RequiredArgsConstructor
public class UpdateMyInterestsService {

    private final UserRepository userRepository;
    private final UserInterestConceptRepository
            interestConceptRepository;
    private final ConceptValidationPort conceptValidationPort;

    /**
     * 사용자의 기존 관심 개념을 요청된 목록으로 교체합니다.
     *
     * <p>Content Service 검증이 완료된 후 기존 목록과 요청 목록의
     * 차이를 계산하여 제거 대상은 논리 삭제하고 새로운 개념만 추가합니다.</p>
     *
     * @param userId 인증된 사용자 식별자
     * @param command 관심 개념 전체 교체 명령
     * @return 최종 관심 개념 목록과 변경 시각
     */
    @Transactional
    public UpdateMyInterestsResult updateMyInterests(
            UUID userId,
            UpdateMyInterestsCommand command
    ) {
        Objects.requireNonNull(
                userId,
                "사용자 식별자는 필수입니다."
        );
        Objects.requireNonNull(
                command,
                "관심 개념 수정 명령은 필수입니다."
        );

        User user = userRepository
                .findById(userId)
                .orElseThrow(
                        () -> new BusinessException(
                                ErrorCode.USER_NOT_FOUND
                        )
                );

        user.assertActive();

        List<UUID> requestedConceptIds =
                command.conceptIds();

        if (!requestedConceptIds.isEmpty()) {
            conceptValidationPort.validateAll(
                    requestedConceptIds
            );
        }

        /*
         * Content Service 호출 중에는 DB 잠금을 점유하지 않습니다.
         * 외부 검증이 완료된 후 사용자 행을 잠가 동일 사용자의
         * 관심 개념 전체 교체 요청을 순차적으로 처리합니다.
         */
        User lockedUser =
                userRepository.findByIdForUpdate(
                        userId
                ).orElseThrow(
                        () -> new BusinessException(
                                ErrorCode.USER_NOT_FOUND
                        )
                );

        /*
         * 최초 조회 이후 계정 상태가 변경됐을 수 있으므로
         * 잠금을 획득한 상태에서 다시 확인합니다.
         */
        lockedUser.assertActive();

        if (requestedConceptIds.isEmpty()) {
            Instant deletedAt = Instant.now();

            int deletedCount =
                    interestConceptRepository
                            .softDeleteAllByUserId(
                                    userId,
                                    userId,
                                    deletedAt
                            );

            return new UpdateMyInterestsResult(
                    requestedConceptIds,
                    0,
                    deletedCount == 0
                            ? null
                            : deletedAt
            );
        }

        List<UserInterestConcept> currentInterests =
                interestConceptRepository.findAllByUserId(
                        userId
                );

        Set<UUID> requestedConceptIdSet =
                new HashSet<>(requestedConceptIds);

        Set<UUID> currentConceptIdSet =
                currentInterests
                        .stream()
                        .map(UserInterestConcept::getConceptId)
                        .collect(
                                java.util.stream.Collectors.toSet()
                        );

        List<UserInterestConcept> removedInterests =
                currentInterests
                        .stream()
                        .filter(
                                interest -> !requestedConceptIdSet.contains(
                                        interest.getConceptId()
                                )
                        )
                        .toList();

        List<UserInterestConcept> addedInterests =
                requestedConceptIds
                        .stream()
                        .filter(
                                conceptId -> !currentConceptIdSet.contains(
                                        conceptId
                                )
                        )
                        .map(
                                conceptId ->
                                        UserInterestConcept.create(
                                                userId,
                                                conceptId
                                        )
                        )
                        .toList();

        removedInterests.forEach(
                interest -> interest.softDelete(userId)
        );

        if (!removedInterests.isEmpty()) {
            interestConceptRepository.saveAllAndFlush(
                    removedInterests
            );
        }

        if (!addedInterests.isEmpty()) {
            interestConceptRepository.saveAllAndFlush(
                    addedInterests
            );
        }

        Instant updatedAt =
                resolveUpdatedAt(
                        currentInterests,
                        removedInterests,
                        addedInterests
                );

        return new UpdateMyInterestsResult(
                requestedConceptIds,
                requestedConceptIds.size(),
                updatedAt
        );
    }

    /**
     * 변경이 발생하면 현재 시각을 반환하고, 동일한 목록을 요청한 경우
     * 기존 관심 개념의 마지막 변경 시각을 반환합니다.
     */
    private Instant resolveUpdatedAt(
            List<UserInterestConcept> currentInterests,
            List<UserInterestConcept> removedInterests,
            List<UserInterestConcept> addedInterests
    ) {
        boolean changed =
                !removedInterests.isEmpty()
                        || !addedInterests.isEmpty();

        if (changed) {
            return Instant.now();
        }

        return currentInterests
                .stream()
                .map(UserInterestConcept::getUpdatedAt)
                .filter(Objects::nonNull)
                .max(Instant::compareTo)
                .orElse(null);
    }
}
