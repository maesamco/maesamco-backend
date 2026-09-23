package com.maesamco.content.application.command_service;

import com.maesamco.content.application.command.ProblemProgressSyncCommand;
import com.maesamco.content.application.finder.ProblemProgressFinder;
import com.maesamco.content.application.finder.ProblemVersionFinder;
import com.maesamco.content.domain.entity.problem.ProblemProgress;
import com.maesamco.content.domain.entity.problem.ProblemProgressStatus;
import com.maesamco.content.domain.entity.problem.ProblemVersion;
import com.maesamco.content.domain.repository.problem.ProblemProgressRepository;
import com.maesamco.content.global.exception.BusinessException;
import com.maesamco.content.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ProblemProgressCommandService {

    private static final String COMPLETED_STATUS = "COMPLETED";

    private final ProblemProgressFinder problemProgressFinder;
    private final ProblemVersionFinder problemVersionFinder;
    private final ProblemProgressRepository problemProgressRepository;

    @Transactional
    public void sync(ProblemProgressSyncCommand command) {
        // COMPLETED가 아니면 채점 결과가 없으므로 ProblemProgress에 반영하지 않습니다.
        if (!COMPLETED_STATUS.equals(command.status())) {
            return;
        }

        // COMPILE_ERROR, RUNTIME_ERROR, TIME_LIMIT_EXCEEDED, MEMORY_LIMIT_EXCEEDED는 WRONG으로 간주합니다.
        ProblemProgressStatus progressStatus = resolveProgressStatus(command.result());

        problemProgressFinder.getByUserIdAndProblemId(command.userId(), command.problemId())
                .ifPresentOrElse(
                        problemProgress -> updateProgress(problemProgress, command, progressStatus),
                        () -> createProgress(command, progressStatus)
                );
    }

    private ProblemProgressStatus resolveProgressStatus(String result) {
        // switch문에서 NullPointerException 방지
        if (result == null) {
            throw new BusinessException(ErrorCode.PROBLEM_PROGRESS_INVALID_SUBMISSION_RESULT);
        }

        return switch (result) {
            case "CORRECT" ->
                    ProblemProgressStatus.CORRECT;

            case "WRONG",
                 "COMPILE_ERROR",
                 "RUNTIME_ERROR",
                 "TIME_LIMIT_EXCEEDED",
                 "MEMORY_LIMIT_EXCEEDED" ->
                    ProblemProgressStatus.WRONG;

            default ->
                    throw new BusinessException(
                            ErrorCode.PROBLEM_PROGRESS_INVALID_SUBMISSION_RESULT
                    );
        };
    }

    private void createProgress(ProblemProgressSyncCommand command, ProblemProgressStatus progressStatus) {
        // 문제에 속한 ProblemVersion이 존재하는지 확인해야만 합니다.
        ProblemVersion problemVersion =
                problemVersionFinder.getByProblemIdAndId(command.problemId(), command.problemVersionId());

        ProblemProgress problemProgress =
                ProblemProgress.create(
                        command.userId(), command.problemId(),
                        problemVersion.getVersionNo(), command.attemptNo(),
                        progressStatus, command.judgedAt()
                );

        // 최초 생성이므로 save가 필요합니다.
        problemProgressRepository.save(problemProgress);
    }

    private void updateProgress(ProblemProgress problemProgress, ProblemProgressSyncCommand command, ProblemProgressStatus progressStatus) {
        // 과거 또는 동일 attempt 이벤트는 최신 상태를 변경하지 않습니다.
        if (!problemProgress.isNewerAttempt(command.attemptNo())) {
            // 다만 더 이른 이벤트라면 최초 채점 시각은 보정합니다.
            problemProgress.changeCreatedAt(command.judgedAt());
            return;
        }

        // 최신 attempt의 상태를 변경하기 전에
        // 해당 문제에 속한 ProblemVersion이 실제로 존재하는지 검증합니다.
        ProblemVersion problemVersion =
                problemVersionFinder.getByProblemIdAndId(command.problemId(), command.problemVersionId());

        // ProblemVersion 검증이 성공한 최신 이벤트만 상태에 반영합니다.
        problemProgress.changeCreatedAt(command.judgedAt());

        // 최신 제출에서 사용된 문제 버전 번호를 반영합니다.
        problemProgress.changeVersionNo(problemVersion.getVersionNo());

        // 마지막으로 반영된 제출 시도 번호를 갱신합니다.
        problemProgress.changeAttemptNo(command.attemptNo());

        // 최신 제출의 채점 결과를 반영합니다.
        if (progressStatus == ProblemProgressStatus.CORRECT) {
            problemProgress.changeStatusCorrect();
        } else {
            problemProgress.changeStatusWrong();
        }

        // solvedAt 역시 검증이 성공한 최신 attempt에 대해서만 반영합니다.
        problemProgress.changeSolvedAt(progressStatus, command.judgedAt());

        // 기존 엔티티는 JPA dirty checking으로 갱신하므로 save하지 않습니다.
    }
}