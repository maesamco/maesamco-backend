package com.maesamco.user.application.service;

import com.maesamco.user.domain.entity.User;
import com.maesamco.user.domain.entity.UserInterestConcept;
import com.maesamco.user.domain.entity.UserRole;
import com.maesamco.user.domain.repository.UserInterestConceptRepository;
import com.maesamco.user.domain.repository.UserRepository;
import com.maesamco.user.global.exception.BusinessException;
import com.maesamco.user.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * 내부 서비스에 필요한 사용자 정보를 조회합니다.
 *
 * <p>현재는 Content Service의 Daily Quiz 콜드스타트에 필요한
 * 활성 관심 개념 ID만 제공합니다.</p>
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class GetInternalUserService {

    private final UserRepository userRepository;

    private final UserInterestConceptRepository
            interestConceptRepository;

    /**
     * 사용자의 활성 관심 개념 ID를 조회합니다.
     *
     * @param userId 조회할 사용자 식별자
     * @return 활성 관심 개념 ID 목록
     */
    public GetInternalUserResult getInternalUser(
            UUID userId
    ) {
        Objects.requireNonNull(
                userId,
                "사용자 ID는 필수입니다."
        );

        User user =
                userRepository.findById(
                                userId
                        )
                        .orElseThrow(
                                () -> new BusinessException(
                                        ErrorCode.USER_NOT_FOUND
                                )
                        );

        validateLookupTarget(user);

        List<UUID> interestConceptIds =
                interestConceptRepository
                        .findAllByUserId(
                                user.getId()
                        )
                        .stream()
                        .map(
                                UserInterestConcept::getConceptId
                        )
                        .sorted()
                        .toList();

        return new GetInternalUserResult(
                interestConceptIds
        );
    }

    /**
     * 내부 사용자 조회 대상이 일반 활성 사용자인지 검증합니다.
     *
     * <p>관리자 계정은 Content Service에 노출하지 않기 위해
     * 존재하지 않는 사용자와 동일하게 처리합니다.</p>
     */
    private void validateLookupTarget(User user) {
        if (user.getRole() != UserRole.USER) {
            throw new BusinessException(
                    ErrorCode.USER_NOT_FOUND
            );
        }

        user.assertActive();
    }
}
