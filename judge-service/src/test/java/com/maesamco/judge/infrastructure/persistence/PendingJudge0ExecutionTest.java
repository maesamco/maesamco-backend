package com.maesamco.judge.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PendingJudge0ExecutionTest {

    @Test
    @DisplayName("create() — isPublic 파라미터가 true면 true로 저장된다")
    void create_storesIsPublicTrue() {
        PendingJudge0Execution execution = PendingJudge0Execution.create(
                UUID.randomUUID(), UUID.randomUUID(), "token-1", true
        );

        assertThat(execution.isPublic()).isTrue();
    }

    @Test
    @DisplayName("create() — isPublic 파라미터가 false면 false로 저장된다")
    void create_storesIsPublicFalse() {
        PendingJudge0Execution execution = PendingJudge0Execution.create(
                UUID.randomUUID(), UUID.randomUUID(), "token-2", false
        );

        assertThat(execution.isPublic()).isFalse();
    }
}