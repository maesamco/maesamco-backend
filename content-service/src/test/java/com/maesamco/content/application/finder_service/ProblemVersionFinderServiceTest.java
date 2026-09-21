package com.maesamco.content.application.finder_service;

import com.maesamco.content.application.finder_service.ProblemVersionFinderService;
import com.maesamco.content.domain.entity.problem.ProblemVersion;
import com.maesamco.content.domain.repository.problem.ProblemVersionRepository;
import com.maesamco.content.global.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProblemVersionFinderServiceTest {

    @Mock
    private ProblemVersionRepository problemVersionRepository;

    @InjectMocks
    private ProblemVersionFinderService problemVersionFinderService;

    @Test
    @DisplayName("문제 버전 ID로 문제 버전을 조회한다")
    void getById_success() {
        // given
        UUID problemVersionId = UUID.randomUUID();
        ProblemVersion problemVersion = mock(ProblemVersion.class);

        when(problemVersionRepository.findById(problemVersionId))
                .thenReturn(Optional.of(problemVersion));

        // when
        ProblemVersion result =
                problemVersionFinderService.getById(problemVersionId);

        // then
        assertThat(result).isSameAs(problemVersion);

        verify(problemVersionRepository)
                .findById(problemVersionId);
    }

    @Test
    @DisplayName("존재하지 않는 문제 버전을 조회하면 BusinessException이 발생한다")
    void getById_notFound_throwsException() {
        // given
        UUID problemVersionId = UUID.randomUUID();

        when(problemVersionRepository.findById(problemVersionId))
                .thenReturn(Optional.empty());

        // when & then
        assertThatThrownBy(
                () -> problemVersionFinderService.getById(problemVersionId)
        )
                .isInstanceOf(BusinessException.class);

        verify(problemVersionRepository)
                .findById(problemVersionId);
    }
}