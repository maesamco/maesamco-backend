package com.maesamco.content.application.persistence_service;

import com.maesamco.content.application.finder.ProblemFinder;
import com.maesamco.content.domain.entity.problem.Problem;
import com.maesamco.content.domain.entity.problem.ProblemVersion;
import com.maesamco.content.domain.repository.problem.ProblemVersionRepository;
import com.maesamco.content.global.exception.BusinessException;
import com.maesamco.content.global.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProblemVersionServiceTest {

    @Mock
    private ProblemFinder problemFinder;

    @Mock
    private ProblemVersionRepository problemVersionRepository;

    @InjectMocks
    private ProblemVersionService problemVersionService;

    private final UUID problemId =
            UUID.randomUUID();

    @Test
    @DisplayName(
            "특정 문제의 전체 버전 이력을 최신순으로 조회한다"
    )
    void getProblemVersions_success() {

        Problem problem =
                mock(Problem.class);

        ProblemVersion version2 =
                mock(ProblemVersion.class);

        ProblemVersion version1 =
                mock(ProblemVersion.class);

        when(
                problemFinder.getById(
                        problemId
                )
        ).thenReturn(
                problem
        );

        when(
                problemVersionRepository
                        .findAllByProblemIdOrderByVersionNoDesc(
                                problemId
                        )
        ).thenReturn(
                List.of(
                        version2,
                        version1
                )
        );

        List<ProblemVersion> result =
                problemVersionService
                        .getProblemVersions(
                                problemId
                        );

        assertThat(result)
                .containsExactly(
                        version2,
                        version1
                );

        verify(problemFinder)
                .getById(
                        problemId
                );

        verify(problemVersionRepository)
                .findAllByProblemIdOrderByVersionNoDesc(
                        problemId
                );
    }

    @Test
    @DisplayName(
            "특정 문제의 특정 버전을 조회한다"
    )
    void getProblemVersion_success() {

        int versionNo = 2;

        Problem problem =
                mock(Problem.class);

        ProblemVersion problemVersion =
                mock(ProblemVersion.class);

        when(
                problemFinder.getById(
                        problemId
                )
        ).thenReturn(
                problem
        );

        when(
                problemVersionRepository
                        .findByProblemIdAndVersionNo(
                                problemId,
                                versionNo
                        )
        ).thenReturn(
                Optional.of(
                        problemVersion
                )
        );

        ProblemVersion result =
                problemVersionService
                        .getProblemVersion(
                                problemId,
                                versionNo
                        );

        assertThat(result)
                .isSameAs(
                        problemVersion
                );
    }

    @Test
    @DisplayName(
            "존재하지 않는 문제 버전을 조회하면 PROBLEM_VERSION_NOT_FOUND가 발생한다"
    )
    void getProblemVersion_notFound_throwsException() {

        int versionNo = 99;

        Problem problem =
                mock(Problem.class);

        when(
                problemFinder.getById(
                        problemId
                )
        ).thenReturn(
                problem
        );

        when(
                problemVersionRepository
                        .findByProblemIdAndVersionNo(
                                problemId,
                                versionNo
                        )
        ).thenReturn(
                Optional.empty()
        );

        assertThatThrownBy(
                () ->
                        problemVersionService
                                .getProblemVersion(
                                        problemId,
                                        versionNo
                                )
        )
                .isInstanceOf(
                        BusinessException.class
                )
                .extracting(
                        exception ->
                                ((BusinessException) exception)
                                        .getErrorCode()
                )
                .isEqualTo(
                        ErrorCode.PROBLEM_VERSION_NOT_FOUND
                );
    }
}
