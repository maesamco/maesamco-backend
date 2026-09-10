package com.maesamco.content.testcase.domain.repository;

import java.util.UUID;

public interface TestCaseOrderRepository {
    /** 특정 문제의 공개 여부에 따른 테스트케이스 중 가장 큰 순서를 반환합니다. */
    int findMaxTestCaseOrderByProblemIdAndIsPublic(UUID problemId, boolean isPublic);
}
