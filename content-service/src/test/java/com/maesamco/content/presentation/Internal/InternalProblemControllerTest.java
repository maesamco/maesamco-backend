package com.maesamco.content.presentation.Internal;

import com.maesamco.content.application.result.ProblemInternalResult;
import com.maesamco.content.application.service.ProblemInternalService;
import com.maesamco.content.global.exception.BusinessException;
import com.maesamco.content.global.exception.ErrorCode;
import com.maesamco.content.presentation.internal_controller.InternalProblemController;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * {@link InternalProblemController}의 HTTP 요청/응답을 검증합니다.
 *
 * <p>이 테스트에서는 실제 ProblemInternalService의 비즈니스 로직을
 * 실행하지 않고 Mock으로 대체합니다.</p>
 *
 * <p>검증 범위는 다음과 같습니다.</p>
 *
 * <ul>
 *     <li>문제 ID 기반 내부 문제 메타데이터 조회</li>
 *     <li>문제 버전 ID 기반 내부 문제 메타데이터 조회</li>
 *     <li>ProblemInternalResult -> InternalProblemResponse 변환</li>
 *     <li>SuccessResponse 응답 구조</li>
 *     <li>Controller -> Service 파라미터 전달</li>
 *     <li>BusinessException 발생 시 HTTP 오류 응답</li>
 *     <li>잘못된 UUID PathVariable 요청 처리</li>
 * </ul>
 *
 * <p>내부 API의 HMAC 인증과 같은 Filter 동작은
 * 별도의 Security/Filter 테스트에서 검증하며,
 * 이 테스트에서는 Controller 자체의 책임에 집중하기 위해
 * Servlet Filter를 비활성화합니다.</p>
 */
@WebMvcTest(InternalProblemController.class)
@AutoConfigureMockMvc(addFilters = false)
class InternalProblemControllerTest {

    private static final String PROBLEM_URL =
            "/internal/v1/problems/{problemId}";

    private static final String PROBLEM_VERSION_URL =
            "/internal/v1/problem-versions/{problemVersionId}";

    private static final String DESCRIPTION =
            "두 정수를 입력받아 두 수의 합을 출력하는 문제입니다.";

    private static final List<String> CONCEPT_TAGS =
            List.of(
                    "구현",
                    "입출력",
                    "기초 연산"
            );

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ProblemInternalService problemInternalService;

    /*
     * ============================================================
     * 문제 단건 조회
     * GET /internal/v1/problems/{problemId}
     * ============================================================
     */

    @Nested
    @DisplayName("문제 메타데이터 조회")
    class GetProblem {

        @Test
        @DisplayName(
                "문제 ID로 내부 문제 메타데이터를 조회하면 200을 반환한다"
        )
        void getProblem_success_returns200()
                throws Exception {

            // given
            UUID problemId =
                    UUID.randomUUID();

            ProblemInternalResult result =
                    createProblemInternalResult(
                            problemId,
                            DESCRIPTION,
                            CONCEPT_TAGS
                    );

            when(
                    problemInternalService
                            .getProblemMetaData(
                                    problemId
                            )
            ).thenReturn(
                    result
            );

            // when & then
            mockMvc.perform(
                            get(
                                    PROBLEM_URL,
                                    problemId
                            )
                    )
                    .andExpect(
                            status().isOk()
                    )
                    .andExpect(
                            content()
                                    .contentTypeCompatibleWith(
                                            "application/json"
                                    )
                    )
                    .andExpect(
                            jsonPath(
                                    "$.success"
                            ).value(
                                    true
                            )
                    )
                    .andExpect(
                            jsonPath(
                                    "$.data"
                            ).exists()
                    );

            verify(
                    problemInternalService
            ).getProblemMetaData(
                    problemId
            );
        }

        @Test
        @DisplayName(
                "문제 조회 결과의 ID를 응답에 그대로 반환한다"
        )
        void getProblem_mapsIdToResponse()
                throws Exception {

            // given
            UUID problemId =
                    UUID.randomUUID();

            ProblemInternalResult result =
                    createProblemInternalResult(
                            problemId,
                            DESCRIPTION,
                            CONCEPT_TAGS
                    );

            when(
                    problemInternalService
                            .getProblemMetaData(
                                    problemId
                            )
            ).thenReturn(
                    result
            );

            // when & then
            mockMvc.perform(
                            get(
                                    PROBLEM_URL,
                                    problemId
                            )
                    )
                    .andExpect(
                            status().isOk()
                    )
                    .andExpect(
                            jsonPath(
                                    "$.data.id"
                            ).value(
                                    problemId.toString()
                            )
                    );
        }

        @Test
        @DisplayName(
                "문제 조회 결과의 description을 응답에 그대로 반환한다"
        )
        void getProblem_mapsDescriptionToResponse()
                throws Exception {

            // given
            UUID problemId =
                    UUID.randomUUID();

            ProblemInternalResult result =
                    createProblemInternalResult(
                            problemId,
                            DESCRIPTION,
                            CONCEPT_TAGS
                    );

            when(
                    problemInternalService
                            .getProblemMetaData(
                                    problemId
                            )
            ).thenReturn(
                    result
            );

            // when & then
            mockMvc.perform(
                            get(
                                    PROBLEM_URL,
                                    problemId
                            )
                    )
                    .andExpect(
                            status().isOk()
                    )
                    .andExpect(
                            jsonPath(
                                    "$.data.description"
                            ).value(
                                    DESCRIPTION
                            )
                    );
        }

        @Test
        @DisplayName(
                "문제 조회 결과의 conceptTags를 응답에 그대로 반환한다"
        )
        void getProblem_mapsConceptTagsToResponse()
                throws Exception {

            // given
            UUID problemId =
                    UUID.randomUUID();

            ProblemInternalResult result =
                    createProblemInternalResult(
                            problemId,
                            DESCRIPTION,
                            CONCEPT_TAGS
                    );

            when(
                    problemInternalService
                            .getProblemMetaData(
                                    problemId
                            )
            ).thenReturn(
                    result
            );

            // when & then
            mockMvc.perform(
                            get(
                                    PROBLEM_URL,
                                    problemId
                            )
                    )
                    .andExpect(
                            status().isOk()
                    )
                    .andExpect(
                            jsonPath(
                                    "$.data.conceptTags"
                            ).isArray()
                    )
                    .andExpect(
                            jsonPath(
                                    "$.data.conceptTags.length()"
                            ).value(
                                    3
                            )
                    )
                    .andExpect(
                            jsonPath(
                                    "$.data.conceptTags[0]"
                            ).value(
                                    "구현"
                            )
                    )
                    .andExpect(
                            jsonPath(
                                    "$.data.conceptTags[1]"
                            ).value(
                                    "입출력"
                            )
                    )
                    .andExpect(
                            jsonPath(
                                    "$.data.conceptTags[2]"
                            ).value(
                                    "기초 연산"
                            )
                    );
        }

        @Test
        @DisplayName(
                "문제 조회 시 PathVariable의 problemId를 서비스에 정확히 전달한다"
        )
        void getProblem_passesProblemIdToService()
                throws Exception {

            // given
            UUID problemId =
                    UUID.randomUUID();

            ProblemInternalResult result =
                    createProblemInternalResult(
                            problemId,
                            DESCRIPTION,
                            CONCEPT_TAGS
                    );

            when(
                    problemInternalService
                            .getProblemMetaData(
                                    problemId
                            )
            ).thenReturn(
                    result
            );

            // when
            mockMvc.perform(
                            get(
                                    PROBLEM_URL,
                                    problemId
                            )
                    )
                    .andExpect(
                            status().isOk()
                    );

            // then
            verify(
                    problemInternalService,
                    times(1)
            ).getProblemMetaData(
                    problemId
            );

            verify(
                    problemInternalService,
                    never()
            ).getProblemVersionMetaData(
                    any(UUID.class)
            );
        }

        @Test
        @DisplayName(
                "문제가 존재하지 않으면 404와 PROBLEM_NOT_FOUND를 반환한다"
        )
        void getProblem_notFound_returns404()
                throws Exception {

            // given
            UUID problemId =
                    UUID.randomUUID();

            when(
                    problemInternalService
                            .getProblemMetaData(
                                    problemId
                            )
            ).thenThrow(
                    new BusinessException(
                            ErrorCode.PROBLEM_NOT_FOUND
                    )
            );

            // when & then
            mockMvc.perform(
                            get(
                                    PROBLEM_URL,
                                    problemId
                            )
                    )
                    .andExpect(
                            status().isNotFound()
                    )
                    .andExpect(
                            jsonPath(
                                    "$.success"
                            ).value(
                                    false
                            )
                    )
                    .andExpect(
                            jsonPath(
                                    "$.error.code"
                            ).value(
                                    "PROBLEM_NOT_FOUND"
                            )
                    );

            verify(
                    problemInternalService
            ).getProblemMetaData(
                    problemId
            );

            verify(
                    problemInternalService,
                    never()
            ).getProblemVersionMetaData(
                    any(UUID.class)
            );
        }

        @Test
        @DisplayName(
                "problemId가 UUID 형식이 아니면 400을 반환하고 서비스를 호출하지 않는다"
        )
        void getProblem_invalidUuid_returns400()
                throws Exception {

            // given
            String invalidProblemId =
                    "invalid-problem-id";

            // when & then
            mockMvc.perform(
                            get(
                                    PROBLEM_URL,
                                    invalidProblemId
                            )
                    )
                    .andExpect(
                            status().isBadRequest()
                    );

            verifyNoInteractions(
                    problemInternalService
            );
        }

        @Test
        @DisplayName(
                "conceptTags가 비어 있어도 정상적으로 빈 배열을 반환한다"
        )
        void getProblem_emptyConceptTags_returnsEmptyArray()
                throws Exception {

            // given
            UUID problemId =
                    UUID.randomUUID();

            ProblemInternalResult result =
                    createProblemInternalResult(
                            problemId,
                            DESCRIPTION,
                            List.of()
                    );

            when(
                    problemInternalService
                            .getProblemMetaData(
                                    problemId
                            )
            ).thenReturn(
                    result
            );

            // when & then
            mockMvc.perform(
                            get(
                                    PROBLEM_URL,
                                    problemId
                            )
                    )
                    .andExpect(
                            status().isOk()
                    )
                    .andExpect(
                            jsonPath(
                                    "$.success"
                            ).value(
                                    true
                            )
                    )
                    .andExpect(
                            jsonPath(
                                    "$.data.conceptTags"
                            ).isArray()
                    )
                    .andExpect(
                            jsonPath(
                                    "$.data.conceptTags.length()"
                            ).value(
                                    0
                            )
                    );
        }
    }

    /*
     * ============================================================
     * 문제 버전 단건 조회
     * GET /internal/v1/problem-versions/{problemVersionId}
     * ============================================================
     */

    @Nested
    @DisplayName("문제 버전 메타데이터 조회")
    class GetProblemVersion {

        @Test
        @DisplayName(
                "문제 버전 ID로 메타데이터를 조회하면 200을 반환한다"
        )
        void getProblemVersion_success_returns200()
                throws Exception {

            // given
            UUID problemVersionId =
                    UUID.randomUUID();

            UUID problemId =
                    UUID.randomUUID();

            ProblemInternalResult result =
                    createProblemInternalResult(
                            problemId,
                            DESCRIPTION,
                            CONCEPT_TAGS
                    );

            when(
                    problemInternalService
                            .getProblemVersionMetaData(
                                    problemVersionId
                            )
            ).thenReturn(
                    result
            );

            // when & then
            mockMvc.perform(
                            get(
                                    PROBLEM_VERSION_URL,
                                    problemVersionId
                            )
                    )
                    .andExpect(
                            status().isOk()
                    )
                    .andExpect(
                            content()
                                    .contentTypeCompatibleWith(
                                            "application/json"
                                    )
                    )
                    .andExpect(
                            jsonPath(
                                    "$.success"
                            ).value(
                                    true
                            )
                    )
                    .andExpect(
                            jsonPath(
                                    "$.data"
                            ).exists()
                    );

            verify(
                    problemInternalService
            ).getProblemVersionMetaData(
                    problemVersionId
            );
        }

        @Test
        @DisplayName(
                "문제 버전 조회 결과를 InternalProblemResponse 형태로 반환한다"
        )
        void getProblemVersion_mapsResultToResponse()
                throws Exception {

            // given
            UUID problemVersionId =
                    UUID.randomUUID();

            UUID problemId =
                    UUID.randomUUID();

            ProblemInternalResult result =
                    createProblemInternalResult(
                            problemId,
                            "버전 2 시점 문제 설명",
                            List.of(
                                    "배열",
                                    "반복문"
                            )
                    );

            when(
                    problemInternalService
                            .getProblemVersionMetaData(
                                    problemVersionId
                            )
            ).thenReturn(
                    result
            );

            // when & then
            mockMvc.perform(
                            get(
                                    PROBLEM_VERSION_URL,
                                    problemVersionId
                            )
                    )
                    .andExpect(
                            status().isOk()
                    )
                    .andExpect(
                            jsonPath(
                                    "$.success"
                            ).value(
                                    true
                            )
                    )
                    .andExpect(
                            jsonPath(
                                    "$.data.id"
                            ).value(
                                    problemId.toString()
                            )
                    )
                    .andExpect(
                            jsonPath(
                                    "$.data.description"
                            ).value(
                                    "버전 2 시점 문제 설명"
                            )
                    )
                    .andExpect(
                            jsonPath(
                                    "$.data.conceptTags.length()"
                            ).value(
                                    2
                            )
                    )
                    .andExpect(
                            jsonPath(
                                    "$.data.conceptTags[0]"
                            ).value(
                                    "배열"
                            )
                    )
                    .andExpect(
                            jsonPath(
                                    "$.data.conceptTags[1]"
                            ).value(
                                    "반복문"
                            )
                    );
        }

        @Test
        @DisplayName(
                "문제 버전 조회 시 problemVersionId를 서비스에 정확히 전달한다"
        )
        void getProblemVersion_passesVersionIdToService()
                throws Exception {

            // given
            UUID problemVersionId =
                    UUID.randomUUID();

            ProblemInternalResult result =
                    createProblemInternalResult(
                            UUID.randomUUID(),
                            DESCRIPTION,
                            CONCEPT_TAGS
                    );

            when(
                    problemInternalService
                            .getProblemVersionMetaData(
                                    problemVersionId
                            )
            ).thenReturn(
                    result
            );

            // when
            mockMvc.perform(
                            get(
                                    PROBLEM_VERSION_URL,
                                    problemVersionId
                            )
                    )
                    .andExpect(
                            status().isOk()
                    );

            // then
            verify(
                    problemInternalService,
                    times(1)
            ).getProblemVersionMetaData(
                    problemVersionId
            );

            verify(
                    problemInternalService,
                    never()
            ).getProblemMetaData(
                    any(UUID.class)
            );
        }

        @Test
        @DisplayName(
                "문제 버전이 존재하지 않으면 404를 반환한다"
        )
        void getProblemVersion_notFound_returns404()
                throws Exception {

            // given
            UUID problemVersionId =
                    UUID.randomUUID();

            when(
                    problemInternalService
                            .getProblemVersionMetaData(
                                    problemVersionId
                            )
            ).thenThrow(
                    new BusinessException(
                            ErrorCode.PROBLEM_NOT_FOUND
                    )
            );

            // when & then
            mockMvc.perform(
                            get(
                                    PROBLEM_VERSION_URL,
                                    problemVersionId
                            )
                    )
                    .andExpect(
                            status().isNotFound()
                    )
                    .andExpect(
                            jsonPath(
                                    "$.success"
                            ).value(
                                    false
                            )
                    )
                    .andExpect(
                            jsonPath(
                                    "$.error.code"
                            ).value(
                                    "PROBLEM_NOT_FOUND"
                            )
                    );

            verify(
                    problemInternalService
            ).getProblemVersionMetaData(
                    problemVersionId
            );

            verify(
                    problemInternalService,
                    never()
            ).getProblemMetaData(
                    any(UUID.class)
            );
        }

        @Test
        @DisplayName(
                "problemVersionId가 UUID 형식이 아니면 400을 반환하고 서비스를 호출하지 않는다"
        )
        void getProblemVersion_invalidUuid_returns400()
                throws Exception {

            // given
            String invalidProblemVersionId =
                    "invalid-version-id";

            // when & then
            mockMvc.perform(
                            get(
                                    PROBLEM_VERSION_URL,
                                    invalidProblemVersionId
                            )
                    )
                    .andExpect(
                            status().isBadRequest()
                    );

            verifyNoInteractions(
                    problemInternalService
            );
        }

        @Test
        @DisplayName(
                "문제 버전 조회에서도 conceptTags가 비어 있으면 빈 배열을 반환한다"
        )
        void getProblemVersion_emptyConceptTags_returnsEmptyArray()
                throws Exception {

            // given
            UUID problemVersionId =
                    UUID.randomUUID();

            UUID problemId =
                    UUID.randomUUID();

            ProblemInternalResult result =
                    createProblemInternalResult(
                            problemId,
                            "태그가 없는 문제 버전",
                            List.of()
                    );

            when(
                    problemInternalService
                            .getProblemVersionMetaData(
                                    problemVersionId
                            )
            ).thenReturn(
                    result
            );

            // when & then
            mockMvc.perform(
                            get(
                                    PROBLEM_VERSION_URL,
                                    problemVersionId
                            )
                    )
                    .andExpect(
                            status().isOk()
                    )
                    .andExpect(
                            jsonPath(
                                    "$.data.id"
                            ).value(
                                    problemId.toString()
                            )
                    )
                    .andExpect(
                            jsonPath(
                                    "$.data.description"
                            ).value(
                                    "태그가 없는 문제 버전"
                            )
                    )
                    .andExpect(
                            jsonPath(
                                    "$.data.conceptTags"
                            ).isArray()
                    )
                    .andExpect(
                            jsonPath(
                                    "$.data.conceptTags.length()"
                            ).value(
                                    0
                            )
                    );
        }
    }

    /*
     * ============================================================
     * 라우팅 분리 검증
     * ============================================================
     */

    @Nested
    @DisplayName("내부 API 라우팅")
    class Routing {

        @Test
        @DisplayName(
                "/problems/{id} 요청은 현재 문제 조회 메서드만 호출한다"
        )
        void problemRoute_callsOnlyCurrentProblemServiceMethod()
                throws Exception {

            // given
            UUID problemId =
                    UUID.randomUUID();

            ProblemInternalResult result =
                    createProblemInternalResult(
                            problemId,
                            DESCRIPTION,
                            CONCEPT_TAGS
                    );

            when(
                    problemInternalService
                            .getProblemMetaData(
                                    problemId
                            )
            ).thenReturn(
                    result
            );

            // when
            mockMvc.perform(
                            get(
                                    PROBLEM_URL,
                                    problemId
                            )
                    )
                    .andExpect(
                            status().isOk()
                    );

            // then
            verify(
                    problemInternalService,
                    times(1)
            ).getProblemMetaData(
                    problemId
            );

            verify(
                    problemInternalService,
                    never()
            ).getProblemVersionMetaData(
                    any(UUID.class)
            );
        }

        @Test
        @DisplayName(
                "/problem-versions/{id} 요청은 버전 문제 조회 메서드만 호출한다"
        )
        void problemVersionRoute_callsOnlyVersionServiceMethod()
                throws Exception {

            // given
            UUID problemVersionId =
                    UUID.randomUUID();

            ProblemInternalResult result =
                    createProblemInternalResult(
                            UUID.randomUUID(),
                            DESCRIPTION,
                            CONCEPT_TAGS
                    );

            when(
                    problemInternalService
                            .getProblemVersionMetaData(
                                    problemVersionId
                            )
            ).thenReturn(
                    result
            );

            // when
            mockMvc.perform(
                            get(
                                    PROBLEM_VERSION_URL,
                                    problemVersionId
                            )
                    )
                    .andExpect(
                            status().isOk()
                    );

            // then
            verify(
                    problemInternalService,
                    times(1)
            ).getProblemVersionMetaData(
                    problemVersionId
            );

            verify(
                    problemInternalService,
                    never()
            ).getProblemMetaData(
                    any(UUID.class)
            );
        }

        @Test
        @DisplayName(
                "존재하지 않는 내부 API 경로는 서비스 호출 없이 404를 반환한다"
        )
        void unknownInternalRoute_returns404()
                throws Exception {

            // when & then
            mockMvc.perform(
                            get(
                                    "/internal/v1/unknown/{id}",
                                    UUID.randomUUID()
                            )
                    )
                    .andExpect(
                            status().isNotFound()
                    );

            verifyNoInteractions(
                    problemInternalService
            );
        }
    }

    /*
     * ============================================================
     * Fixture
     * ============================================================
     */

    /**
     * ProblemInternalResult는 Application Layer의 결과 객체입니다.
     *
     * <p>Controller 테스트에서는 Service 내부 로직을 검증할 필요가 없으므로
     * 실제 Problem / ProblemVersion 엔티티를 조립하지 않고
     * Result 객체를 Mock으로 생성합니다.</p>
     *
     * <p>이렇게 하면 Controller 테스트가 Domain Entity 생성 방식이나
     * Repository 구조에 영향을 받지 않고 Presentation Layer의 책임만
     * 검증할 수 있습니다.</p>
     */
    private ProblemInternalResult createProblemInternalResult(
            UUID id,
            String description,
            List<String> conceptTags
    ) {
        ProblemInternalResult result =
                mock(
                        ProblemInternalResult.class
                );

        when(
                result.getId()
        ).thenReturn(
                id
        );

        when(
                result.getDescription()
        ).thenReturn(
                description
        );

        when(
                result.getConceptTags()
        ).thenReturn(
                conceptTags
        );

        return result;
    }
}