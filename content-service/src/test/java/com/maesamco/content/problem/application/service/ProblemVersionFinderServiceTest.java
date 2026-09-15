package com.maesamco.content.problem.application.service;

import com.maesamco.content.application.service.finder.ProblemVersionFinderService;
import com.maesamco.content.domain.entity.problem.ProblemVersion;
import com.maesamco.content.domain.repository.problem.ProblemVersionRepository;
import com.maesamco.content.global.exception.BusinessException;
import com.maesamco.content.global.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProblemVersionFinderServiceTest {

    @Mock
    private ProblemVersionRepository problemVersionRepository;

    private ProblemVersionFinderService problemVersionFinderService;

    private final UUID problemVersionId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        problemVersionFinderService = new ProblemVersionFinderService(problemVersionRepository);
    }

    @Test
    @DisplayName("문제 버전이 존재하면 문제 버전을 반환한다")
    void getProblemVersion_returnsProblemVersion() {
        // given
        ProblemVersion problemVersion = org.mockito.Mockito.mock(ProblemVersion.class);

        when(problemVersionRepository.findById(problemVersionId))
                .thenReturn(Optional.of(problemVersion));

        // when
        ProblemVersion result = problemVersionFinderService.getProblemVersion(problemVersionId);

        // then
        assertThat(result).isSameAs(problemVersion);
    }

    @Test
    @DisplayName("문제 버전이 존재하지 않으면 PROBLEM_NOT_FOUND 예외가 발생한다")
    void getProblemVersion_throwsWhenNotFound() {
        // given
        when(problemVersionRepository.findById(problemVersionId))
                .thenReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> problemVersionFinderService.getProblemVersion(problemVersionId))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.PROBLEM_NOT_FOUND);
    }
}
