package com.maesamco.content.dailyquiz.infrastructure.adapter;

import com.maesamco.content.dailyquiz.application.port.DailyQuizTargetUserPort;
import com.maesamco.content.dailyquiz.application.result.DailyQuizTargetUserPage;
import com.maesamco.content.global.exception.BusinessException;
import com.maesamco.content.global.exception.ErrorCode;
import com.maesamco.content.global.response.SuccessResponse;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * User Service에서 Daily Quiz 생성 대상 사용자를 조회하고
 * 외부 응답을 애플리케이션에서 사용하는 페이지 모델로 변환하는 Adapter
 */
@Component
public class DailyQuizTargetUserFeignAdapter implements DailyQuizTargetUserPort {

    private final UserServiceFeignClient feignClient;

    public DailyQuizTargetUserFeignAdapter(UserServiceFeignClient feignClient) {
        this.feignClient = feignClient;
    }

    @Override
    public DailyQuizTargetUserPage getTargetUsers(UUID cursor, int size) {
        // Feign Client를 사용해 cursor와 size에 해당하는 대상 사용자 페이지를 조회합니다.
        SuccessResponse<UserQuizTargetPageResponse> quizTargets = feignClient.getQuizTargets(cursor, size);

        // 공통 응답이 null이거나 success=false 또는 data=null이면 예외를 발생시킵니다.
        if (quizTargets == null || !quizTargets.success() || quizTargets.data() == null) {
            throw new BusinessException(
                    ErrorCode.FEIGN_CLIENT_ERROR,
                    "Daily Quiz 대상 사용자 조회 응답이 올바르지 않습니다."
            );
        }
        UserQuizTargetPageResponse data = quizTargets.data();

        // data의 hasNext가 null이면 응답 계약 위반이므로 예외를 발생시킵니다.
        if (data.hasNext() == null) {
            throw new BusinessException(
                    ErrorCode.FEIGN_CLIENT_ERROR,
                    "Daily Quiz 대상 사용자 조회 응답의 hasNext는 필수입니다."
            );
        }

        // 응답 DTO의 userIds, nextCursor, hasNext를 DailyQuizTargetUserPage로 변환해 반환합니다.
        return new DailyQuizTargetUserPage(
                data.userIds(),
                data.nextCursor(),
                data.hasNext()
        );
    }
}
