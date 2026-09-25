package com.maesamco.user.application.service;

import com.maesamco.user.domain.entity.User;
import com.maesamco.user.domain.entity.UserInterestConcept;
import com.maesamco.user.domain.repository.UserInterestConceptRepository;
import com.maesamco.user.domain.repository.UserRepository;
import com.maesamco.user.global.exception.BusinessException;
import com.maesamco.user.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * 로그인 사용자의 현재 관심 개념 조회를 처리합니다.
 *
 * <p>관심 개념은 데일리 퀴즈 출제에 쓰이는 서비스 이용 정보라서
 * 정상적으로 서비스를 이용할 수 있는 활성 사용자에게만 제공합니다.</p>
 */
@Service
@RequiredArgsConstructor
public class GetMyInterestsService {

    private final UserRepository userRepository;

    private final UserInterestConceptRepository interestConceptRepository;

    /**
     * 인증된 활성 사용자의 관심 개념 ID 목록을 조회합니다.
     *
     * <p>논리 삭제된 관심 개념은 제외되며, 응답 순서가 조회마다 달라지지 않도록
     * 개념 ID 순으로 정렬합니다.</p>
     *
     * @param userId 인증된 사용자 식별자
     * @return 현재 저장된 관심 개념 ID 목록
     */
    @Transactional(readOnly = true)
    public GetMyInterestsResult getMyInterests(
            UUID userId
    ) {
        Objects.requireNonNull(
                userId,
                "사용자 식별자는 필수입니다."
        );

        User user =
                userRepository
                        .findById(userId)
                        .orElseThrow(
                                () -> new BusinessException(
                                        ErrorCode.USER_NOT_FOUND
                                )
                        );

        user.assertActive();

        List<UUID> conceptIds =
                interestConceptRepository
                        .findAllByUserId(userId)
                        .stream()
                        .map(UserInterestConcept::getConceptId)
                        .sorted(Comparator.naturalOrder())
                        .toList();

        return GetMyInterestsResult.of(
                conceptIds
        );
    }
}
