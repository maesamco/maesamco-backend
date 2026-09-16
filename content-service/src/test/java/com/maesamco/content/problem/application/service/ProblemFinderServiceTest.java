//package com.maesamco.content.problem.application.service;
//
//import com.maesamco.content.application.service.finder.ProblemFinderService;
//import com.maesamco.content.domain.entity.problem.Problem;
//import com.maesamco.content.global.exception.BusinessException;
//import com.maesamco.content.global.exception.ErrorCode;
//import org.junit.jupiter.api.BeforeEach;
//import org.junit.jupiter.api.DisplayName;
//import org.junit.jupiter.api.Test;
//import org.junit.jupiter.api.extension.ExtendWith;
//import org.mockito.Mock;
//import org.mockito.junit.jupiter.MockitoExtension;
//
//import java.util.Optional;
//import java.util.UUID;
//
//import static org.assertj.core.api.Assertions.assertThat;
//import static org.assertj.core.api.Assertions.assertThatThrownBy;
//import static org.mockito.Mockito.when;
//
//@ExtendWith(MockitoExtension.class)
//class ProblemFinderServiceTest {
//
//    @Mock
//    private ProblemRepository problemRepository;
//
//    private ProblemFinderService problemFinderService;
//
//    private final UUID problemId = UUID.randomUUID();
//
//    @BeforeEach
//    void setUp() {
//        problemFinderService = new ProblemFinderService(problemRepository);
//    }
//
//    @Test
//    @DisplayName("문제가 존재하면 문제를 반환한다")
//    void getProblem_returnsById() {
//        // given
//        Problem problem = org.mockito.Mockito.mock(Problem.class);
//
//        when(problemRepository.findById(problemId))
//                .thenReturn(Optional.of(problem));
//
//        // when
//        Problem result = problemFinderService.getById(problemId);
//
//        // then
//        assertThat(result).isSameAs(problem);
//    }
//
//    @Test
//    @DisplayName("문제가 존재하지 않으면 PROBLEM_NOT_FOUND 예외가 발생한다")
//    void getProblem_throwsWhenByIdNotFound() {
//        // given
//        when(problemRepository.findById(problemId))
//                .thenReturn(Optional.empty());
//
//        // when & then
//        assertThatThrownBy(() -> problemFinderService.getById(problemId))
//                .isInstanceOf(BusinessException.class)
//                .extracting(exception -> ((BusinessException) exception).getErrorCode())
//                .isEqualTo(ErrorCode.PROBLEM_NOT_FOUND);
//    }
//}