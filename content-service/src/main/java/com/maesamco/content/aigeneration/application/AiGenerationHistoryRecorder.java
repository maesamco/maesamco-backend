package com.maesamco.content.aigeneration.application;

import com.maesamco.content.aigeneration.domain.entity.AiGenerationHistory;
import com.maesamco.content.aigeneration.domain.entity.AiGenerationPurpose;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class AiGenerationHistoryRecorder {

    private final AiGenerationHistoryTransactionalWriter transactionalWriter;

    public void recordSuccess(
            AiGenerationPurpose purpose,
            UUID relatedId,
            Map<String, Object> requestContext,
            AiGenerationMetadata metadata
    ) {
        try {
            AiGenerationHistory history = AiGenerationHistory.success(
                    purpose,
                    relatedId,
                    requestContext,
                    metadata.modelName(),
                    metadata.promptVersion(),
                    metadata.calledAt(),
                    metadata.responseTimeMs(),
                    metadata.tokenUsage()
            );

            transactionalWriter.save(history);
        } catch (RuntimeException exception) {
            log.error(
                    "AI 생성 성공 이력 저장 실패. purpose={}, relatedId={}",
                    purpose,
                    relatedId,
                    exception
            );
        }
    }

    public void recordFailure(
            AiGenerationPurpose purpose,
            Map<String, Object> requestContext,
            AiGenerationMetadata metadata,
            Throwable failure
    ) {
        try {
            AiGenerationHistory history = AiGenerationHistory.failed(
                    purpose,
                    requestContext,
                    metadata.modelName(),
                    metadata.promptVersion(),
                    metadata.calledAt(),
                    metadata.responseTimeMs(),
                    metadata.tokenUsage(),
                    failureReason(failure)
            );

            transactionalWriter.save(history);
        } catch (RuntimeException exception) {
            log.error(
                    "AI 생성 실패 이력 저장 실패. purpose={}",
                    purpose,
                    exception
            );
        }
    }

    private static String failureReason(Throwable failure) {
        if (failure == null) {
            return "UnknownFailure";
        }

        Throwable rootCause = failure;
        while (rootCause.getCause() != null && rootCause.getCause() != rootCause) {
            rootCause = rootCause.getCause();
        }

        String message = rootCause.getMessage();

        if (message == null || message.isBlank()) {
            return rootCause.getClass().getSimpleName();
        }

        return rootCause.getClass().getSimpleName() + ": " + message;
    }
}