package com.maesamco.judge.application.port;

public enum JudgeExecutionStatus {
    IN_QUEUE, PROCESSING, ACCEPTED, WRONG_ANSWER, TIME_LIMIT_EXCEEDED,
    COMPILE_ERROR, RUNTIME_ERROR, INTERNAL_ERROR, UNKNOWN;

    public static JudgeExecutionStatus fromJudge0Id(int id) {
        return switch (id) {
            case 1 -> IN_QUEUE;
            case 2 -> PROCESSING;
            case 3 -> ACCEPTED;
            case 4 -> WRONG_ANSWER;
            case 5 -> TIME_LIMIT_EXCEEDED;
            case 6 -> COMPILE_ERROR;
            case 7, 8, 9, 10, 11, 12 -> RUNTIME_ERROR;
            case 13 -> INTERNAL_ERROR;
            default -> UNKNOWN;
        };
    }
}