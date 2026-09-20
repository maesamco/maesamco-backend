package com.maesamco.content.application.persistence_service;

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
public class ProblemProgressService {

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

        problemProgressFinder
                .getByUserIdAndProblemId(command.userId(), command.problemId())
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
        // ProblemVersion이 존재하는지 확인
        ProblemVersion problemVersion =
                problemVersionFinder.getById(command.problemVersionId());

        ProblemProgress problemProgress =
                ProblemProgress.create(
                        command.userId(),
                        command.problemId(), problemVersion.getVersionNo(),
                        command.attemptNo(), progressStatus, command.judgedAt()
                );

        // save 필요
        problemProgressRepository.save(problemProgress);
    }

    private void updateProgress(ProblemProgress problemProgress, ProblemProgressSyncCommand command, ProblemProgressStatus progressStatus) {
        // 과거 이벤트도 최초 시각 정보에는 영향을 줄 수 있음
        problemProgress.changeCreatedAt(command.judgedAt());
        problemProgress.changeSolvedAt(progressStatus, command.judgedAt());

        // 현재 상태는 최신 attempt만 변경
        if (!problemProgress.isNewerAttempt(command.attemptNo())) {
            problemProgressRepository.save(problemProgress);
            return;
        }

        // 여기부터 최신 상태 변경
        ProblemVersion problemVersion =
                problemVersionFinder.getById(command.problemVersionId());

        // 최신 제출에서 사용된 문제 버전 번호를 반영합니다.
        problemProgress.changeVersionNo(problemVersion.getVersionNo());

        // 마지막으로 반영된 제출 시도 번호를 갱신합니다.
        problemProgress.changeAttemptNo(command.attemptNo());

        // 최신 제출의 채점 결과를 무조건 반영합니다.
        if (progressStatus == ProblemProgressStatus.CORRECT) {
            problemProgress.changeStatusCorrect();
        } else {
            problemProgress.changeStatusWrong();
        }

        // 직접 save 하지 않고 JPA dirty checking 사용
    }
}