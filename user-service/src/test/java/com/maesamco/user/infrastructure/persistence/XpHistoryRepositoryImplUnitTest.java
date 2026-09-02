package com.maesamco.user.infrastructure.persistence;

import com.maesamco.user.domain.entity.XpHistory;
import com.maesamco.user.domain.repository.XpHistoryRepository;
import com.maesamco.user.global.exception.BusinessException;
import com.maesamco.user.global.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * XpHistoryRepository 구현체의 예외 변환 단위 테스트입니다.
 */
class XpHistoryRepositoryImplUnitTest {

    @Test
    @DisplayName("DB 제약 위반을 XP 이력 중복 예외로 변환한다")
    void save_translatesDataIntegrityViolationException() {
        // given
        SpringDataXpHistoryRepository springDataRepository =
                mock(SpringDataXpHistoryRepository.class);
        XpHistory xpHistory = mock(XpHistory.class);
        XpHistoryRepository xpHistoryRepository =
                new XpHistoryRepositoryImpl(springDataRepository);

        given(springDataRepository.saveAndFlush(xpHistory))
                .willThrow(
                        new DataIntegrityViolationException(
                                "XP history constraint violation"
                        )
                );

        // when & then
        assertThatThrownBy(() -> xpHistoryRepository.save(xpHistory))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> {
                            assertThat(exception.getErrorCode())
                                    .isEqualTo(
                                            ErrorCode.XP_HISTORY_ALREADY_EXISTS
                                    );
                            assertThat(exception.getMessage())
                                    .isEqualTo(
                                            "이미 처리된 XP 이력입니다."
                                    );
                        }
                );

        verify(springDataRepository).saveAndFlush(xpHistory);
    }
}
