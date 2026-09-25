package com.maesamco.user.application.service;

import com.maesamco.user.domain.entity.User;
import com.maesamco.user.domain.entity.UserInterestConcept;
import com.maesamco.user.domain.repository.UserInterestConceptRepository;
import com.maesamco.user.domain.repository.UserRepository;
import com.maesamco.user.global.exception.BusinessException;
import com.maesamco.user.global.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GetMyInterestsServiceTest {

    private static final UUID USER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID CONCEPT_A = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID CONCEPT_B = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");

    @Mock
    private UserRepository userRepository;

    @Mock
    private UserInterestConceptRepository interestConceptRepository;

    @Mock
    private User user;

    @InjectMocks
    private GetMyInterestsService getMyInterestsService;

    private UserInterestConcept interest(UUID conceptId) {
        UserInterestConcept interest = org.mockito.Mockito.mock(UserInterestConcept.class);
        when(interest.getConceptId()).thenReturn(conceptId);
        return interest;
    }

    @Test
    @DisplayName("활성 사용자의 관심 개념 ID를 개념 ID 순으로 반환한다")
    void getMyInterests_returnsConceptIdsSorted() {
        // 스텁 안에서 다른 목을 스텁하면 Mockito가 미완성 스텁으로 보므로 먼저 만들어 둔다.
        UserInterestConcept interestB = interest(CONCEPT_B);
        UserInterestConcept interestA = interest(CONCEPT_A);
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        when(interestConceptRepository.findAllByUserId(USER_ID)).thenReturn(List.of(interestB, interestA));

        GetMyInterestsResult result = getMyInterestsService.getMyInterests(USER_ID);

        assertThat(result.interestConceptIds()).containsExactly(CONCEPT_A, CONCEPT_B);
        assertThat(result.count()).isEqualTo(2);
    }

    @Test
    @DisplayName("관심 개념이 없으면 빈 목록과 0을 반환한다")
    void getMyInterests_returnsEmptyWhenNone() {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        when(interestConceptRepository.findAllByUserId(USER_ID)).thenReturn(List.of());

        GetMyInterestsResult result = getMyInterestsService.getMyInterests(USER_ID);

        assertThat(result.interestConceptIds()).isEmpty();
        assertThat(result.count()).isZero();
    }

    @Test
    @DisplayName("사용자가 없으면 USER_NOT_FOUND")
    void getMyInterests_throwsWhenUserMissing() {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> getMyInterestsService.getMyInterests(USER_ID))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.USER_NOT_FOUND);
        verifyNoInteractions(interestConceptRepository);
    }

    @Test
    @DisplayName("활성 상태가 아닌 사용자는 관심 개념을 조회하지 못한다")
    void getMyInterests_throwsWhenUserNotActive() {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        doThrow(new BusinessException(ErrorCode.USER_NOT_ACTIVE)).when(user).assertActive();

        assertThatThrownBy(() -> getMyInterestsService.getMyInterests(USER_ID))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.USER_NOT_ACTIVE);
        verifyNoInteractions(interestConceptRepository);
    }

    @Test
    @DisplayName("읽기 전용 트랜잭션으로 실행한다")
    void getMyInterests_isReadOnlyTransactional() throws Exception {
        Transactional transactional = GetMyInterestsService.class
                .getMethod("getMyInterests", UUID.class)
                .getAnnotation(Transactional.class);

        assertThat(transactional).isNotNull();
        assertThat(transactional.readOnly()).isTrue();
    }
}
