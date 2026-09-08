package com.maesamco.content.dailyquiz.application.service;

import com.maesamco.content.dailyquiz.application.port.DailyQuizTargetUserPort;
import com.maesamco.content.dailyquiz.application.result.DailyQuizSetGenerationResult;
import com.maesamco.content.dailyquiz.application.result.DailyQuizTargetUserPage;
import com.maesamco.content.global.exception.BusinessException;
import com.maesamco.content.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
// import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/**
 * User Service의 대상 사용자 페이지를 cursor 방식으로 순회하며
 * 사용자별 Daily Quiz 생성을 순차 실행하는 서비스
 *
 * TODO: 개념 후보 조회에 필요한 선행 Repository 구현이 병합되면
 * DailyQuizUserGenerationService와 함께 Spring Bean으로 등록합니다.
 */
// @Service
@Slf4j
@RequiredArgsConstructor
public class DailyQuizBatchExecutionService {

    private final DailyQuizTargetUserPort targetUserPort;
    private final DailyQuizUserGenerationService userGenerationService;

    public void execute(LocalDate attemptDate, int chunkSize) {
        // attemptDate와 chunkSize를 검증합니다.
        if (attemptDate == null) {
            throw new BusinessException(
                    ErrorCode.INVALID_INPUT_VALUE,
                    "퀴즈 날짜는 필수입니다."
            );
        }

        if (chunkSize < 1 || chunkSize > 1000) {
            throw new BusinessException(
                    ErrorCode.INVALID_INPUT_VALUE,
                    "Daily Quiz 배치 chunk size는 1 이상 1000 이하여야 합니다."
            );
        }

        // 첫 페이지 조회를 위해 cursor를 null로 초기화합니다.
        UUID cursor = null;


        // cursor와 chunkSize로 대상 사용자 페이지를 반복 조회합니다.
        while (true) {
            DailyQuizTargetUserPage page;

            try {
                page = targetUserPort.getTargetUsers(cursor, chunkSize);
            } catch (RuntimeException exception) {
                log.error(
                        "Daily Quiz 대상 사용자 페이지 조회 실패. attemptDate={}, cursor={}",
                        attemptDate,
                        cursor,
                        exception
                );
                return;
            }

            // 현재 페이지의 사용자들을 순차 처리합니다.
            // 사용자 한 명의 생성이 실패해도 오류를 기록하고 다음 사용자를 계속 처리합니다.
            // 생성에 성공하면 CREATED 등의 생성 결과 상태를 기록합니다.
            for (UUID userId : page.userIds()) {
                try {
                    DailyQuizSetGenerationResult result = userGenerationService.generate(userId, attemptDate);

                    log.info(
                            "Daily Quiz 사용자별 생성 완료. userId={}, attemptDate={}, status={}, questionCount={}",
                            userId,
                            attemptDate,
                            result.status(),
                            result.questionCount()
                    );
                } catch (RuntimeException exception) {
                    log.error(
                            "Daily Quiz 사용자별 생성 실패. userId={}, attemptDate={}",
                            userId,
                            attemptDate,
                            exception
                    );
                }
            }

            // hasNext=false이면 배치를 정상 종료합니다.
            if (!page.hasNext()) {
                log.info(
                        "Daily Quiz 배치 대상 사용자 처리를 완료했습니다. attemptDate={}",
                        attemptDate
                );
                return;
            }

            // 다음 cursor가 현재 cursor와 같으면 무한 반복 방지를 위해 배치를 종료합니다.
            UUID nextCursor = page.nextCursor();

            if (Objects.equals(cursor, nextCursor)) {
                log.error(
                        "Daily Quiz 대상 사용자 cursor가 갱신되지 않아 배치를 종료합니다. attemptDate={}, cursor={}",
                        attemptDate,
                        cursor
                );
                return;
            }

            cursor = nextCursor;
        }
    }
}
