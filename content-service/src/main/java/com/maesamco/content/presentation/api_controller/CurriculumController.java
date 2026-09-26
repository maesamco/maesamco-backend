package com.maesamco.content.presentation.api_controller;

import com.maesamco.content.application.persistence_service.CurriculumService;
import com.maesamco.content.application.result.CurriculumResult;
import com.maesamco.content.global.common.pagination.PageQuery;
import com.maesamco.content.global.common.pagination.PageResult;
import com.maesamco.content.global.response.PageResponse;
import com.maesamco.content.global.response.SuccessResponse;
import com.maesamco.content.global.util.PageQueryFactory;
import com.maesamco.content.presentation.request.CurriculumCreateRequest;
import com.maesamco.content.presentation.request.CurriculumUpdateRequest;
import com.maesamco.content.presentation.response.CurriculumCreateResponse;
import com.maesamco.content.presentation.response.CurriculumResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import com.maesamco.content.global.security.authorization.RequireAdmin;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * 커리큘럼 생성, 조회, 수정, 삭제를 위한 API를 제공합니다.
 *
 * <p>커리큘럼 생성, 단건 조회, 목록 조회, 수정, 삭제는
 * {@link CurriculumService}에 위임합니다.</p>
 *
 * <p>모든 정상 응답은 {@link SuccessResponse}로 감싸 반환하며,
 * 목록 조회 결과는 {@link PageResponse}를 사용해 페이징 정보를 함께 제공합니다.</p>
 */
@RestController
@RequiredArgsConstructor
public class CurriculumController implements CurriculumApiDocs {

    /** 커리큘럼 생성, 조회, 수정, 삭제 비즈니스 로직을 담당하는 서비스입니다. */
    private final CurriculumService curriculumService;

    /**
     * 새로운 커리큘럼을 생성합니다.
     *
     * <p>요청 본문의 커리큘럼 생성 정보를 검증한 뒤,
     * 커리큘럼 생성 로직을 {@link CurriculumService}에 위임합니다.</p>
     *
     * @param request 커리큘럼 생성 요청 정보
     * @return 생성된 커리큘럼 정보를 포함한 성공 응답
     */
    @Override
    @RequireAdmin
    public ResponseEntity<SuccessResponse<CurriculumCreateResponse>> createCurriculum(CurriculumCreateRequest request) {
        CurriculumResult result = curriculumService.createCurriculum(request.toCommand());
        CurriculumCreateResponse response = CurriculumCreateResponse.from(result);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(SuccessResponse.success(response));
    }

    /**
     * 커리큘럼 ID를 기준으로 단일 커리큘럼을 조회합니다.
     *
     * <p>커리큘럼 ID를 기준으로 커리큘럼을 조회하고,
     * 조회 결과를 {@link CurriculumResponse}로 반환합니다.</p>
     *
     * @param curriculumId 조회할 커리큘럼의 고유 ID
     * @return 조회된 커리큘럼 정보를 포함한 성공 응답
     */
    @Override
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<SuccessResponse<CurriculumResponse>> getCurriculum(UUID curriculumId) {
        CurriculumResponse response = CurriculumResponse.from(curriculumService.getCurriculumForUser(curriculumId));

        return ResponseEntity.ok(
                SuccessResponse.success(response)
        );
    }

    /**
     * 커리큘럼 목록을 조회합니다.
     *
     * <p>커리큘럼 목록은 표시 순서(displayOrder)를 기준으로
     * 오름차순 정렬하여 조회합니다.</p>
     *
     * <p>조회 결과는 {@link PageResponse}로 변환하여
     * 커리큘럼 목록과 페이징 정보를 함께 반환합니다.</p>
     *
     * @param page 조회할 페이지 번호
     * @param size 한 페이지에 조회할 커리큘럼 개수
     * @return 페이징된 커리큘럼 목록
     */
    @Override
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<SuccessResponse<PageResponse<CurriculumResponse>>> getCurriculums(Integer page, Integer size) {
        PageQuery pageQuery = PageQueryFactory.of(page, size, null, null);

        PageResult<CurriculumResult> result = curriculumService.searchCurriculumsForUser(pageQuery);
        PageResponse<CurriculumResponse> response = PageResponse.from(result, CurriculumResponse::from);

        return ResponseEntity.ok(
                SuccessResponse.success(response)
        );
    }

    /**
     * 지정한 커리큘럼의 정보를 수정합니다.
     *
     * <p>커리큘럼 ID로 수정 대상을 식별하고,
     * 요청 본문에 전달된 수정 정보를 반영합니다.</p>
     *
     * <p>수정 요청에 포함된 값만 변경합니다.</p>
     *
     * @param curriculumId 수정할 커리큘럼의 고유 ID
     * @param request 커리큘럼 수정 요청 정보
     * @return 수정된 커리큘럼 정보를 포함한 성공 응답
     */
    @Override
    @RequireAdmin
    public ResponseEntity<SuccessResponse<CurriculumResponse>> updateCurriculum(UUID curriculumId, CurriculumUpdateRequest request) {
        CurriculumResponse response = CurriculumResponse.from(
                curriculumService.updateCurriculum(curriculumId, request.toCommand())
        );

        return ResponseEntity.ok(
                SuccessResponse.success(response)
        );
    }

    /**
     * 지정한 커리큘럼을 삭제합니다.
     *
     * <p>커리큘럼 ID를 기준으로 삭제 대상 커리큘럼을 식별한 뒤
     * 커리큘럼 삭제 로직을 {@link CurriculumService}에 위임합니다.</p>
     *
     * <p>삭제 시 실제 데이터를 제거하지 않고,
     * 삭제 시각과 삭제 사용자 정보를 기록하는 Soft Delete 방식으로 처리합니다.</p>
     *
     * @param curriculumId 삭제할 커리큘럼의 고유 ID
     * @param userId 삭제를 요청한 사용자의 고유 ID
     * @return 응답 데이터가 없는 성공 응답
     */
    @Override
    @RequireAdmin
    public ResponseEntity<SuccessResponse<Void>> deleteCurriculum(UUID curriculumId, UUID userId) {
        curriculumService.deleteCurriculum(curriculumId, userId);

        return ResponseEntity.ok(
                SuccessResponse.empty()
        );
    }
}

/**
 * 1. Java 컴파일
 *    ↓
 * 2. Spring 애플리케이션 실행
 *    ↓
 * 3. Spring이 @RestController / @Controller Bean 탐색
 *    ↓
 * 4. @PreAuthorize 때문에 AOP Proxy 생성
 *    ↓
 * 5. 인터페이스를 구현하고 있으면 JDK Proxy가 만들어질 수 있음
 *    ↓
 * 6. Spring MVC가 그 Proxy를 Controller로 탐지
 *    ↓
 * 7. @RequestMapping / @GetMapping 등을 읽어서 Handler 등록
 *
 * */