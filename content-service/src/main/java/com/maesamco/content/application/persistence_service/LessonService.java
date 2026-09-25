package com.maesamco.content.application.persistence_service;

import com.maesamco.content.application.result.TagResult;
import com.maesamco.content.application.finder.LessonFinder;
import com.maesamco.content.application.finder.UnitFinder;
import com.maesamco.content.domain.entity.Lesson;
import com.maesamco.content.domain.entity.Tag;
import com.maesamco.content.domain.entity.TagAttribute;
import com.maesamco.content.domain.repository.LessonRepository;
import com.maesamco.content.domain.repository.problem.ProblemQueryRepository;
import com.maesamco.content.domain.repository.problem.ProblemTagRepository;
import com.maesamco.content.global.exception.BusinessException;
import com.maesamco.content.global.exception.ErrorCode;
import com.maesamco.content.global.response.PageResponse;
import com.maesamco.content.presentation.request.LessonCreateRequest;
import com.maesamco.content.presentation.request.LessonUpdateRequest;
import com.maesamco.content.presentation.response.LessonCreateResponse;
import com.maesamco.content.presentation.response.LessonResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/** 레슨 생성, 조회, 수정, 삭제를 담당하는 서비스 */
@Service
@RequiredArgsConstructor
public class LessonService {

    private final LessonRepository lessonRepository;
    private final LessonFinder lessonFinder;
    private final UnitFinder unitFinder;
    private final ProblemQueryRepository problemQueryRepository;
    private final ProblemTagRepository problemTagRepository;

    /** 레슨 생성 */
    @Transactional(rollbackFor = Exception.class)
    public LessonCreateResponse createLesson(LessonCreateRequest request) {

        /*
         * 동일 Unit 아래에서 동시에 Lesson을 생성하면 같은 displayOrder가
         * 계산될 수 있으므로 부모 Unit을 먼저 비관적 락으로 조회합니다.
         */
        unitFinder.lockById(request.getUnitId());

        int displayOrder =
                Math.addExact(
                        lessonRepository
                                .findMaxDisplayOrderByUnitId(
                                        request.getUnitId()
                                ),
                        1
                );

        Lesson lesson = Lesson.create(
                request.getUnitId(),
                request.getTitle(),
                request.getDescription(),
                request.getContent(),
                request.getLanguage(),
                displayOrder
        );

        Lesson savedLesson = lessonRepository.save(lesson);

        return LessonCreateResponse.from(savedLesson);
    }

    /** 레슨 단건 조회 */
    @Transactional(readOnly = true)
    public LessonResponse getLesson(UUID lessonId) {

        Lesson lesson = lessonFinder.getById(lessonId);

        return LessonResponse.from(lesson);
    }

    /** 특정 유닛 레슨 목록 조회 */
    @Transactional(readOnly = true)
    public PageResponse<LessonResponse> searchLessons(UUID unitId, Pageable pageable) {

        // 존재하지 않는 유닛에 대한 조회 방지
        unitFinder.getById(unitId);

        Page<Lesson> lessons = lessonRepository.searchLessons(unitId, pageable);

        return PageResponse.from(lessons, LessonResponse::from);
    }

    /**
     * 학습자용 레슨 단건 조회입니다(#359).
     * 레슨·유닛·커리큘럼 중 공개(PUBLISHED)되지 않은 항목이 있으면 삭제된 것과 같이 NOT_FOUND로 응답합니다.
     */
    @Transactional(readOnly = true)
    public LessonResponse getLessonForUser(UUID lessonId) {

        Lesson lesson = lessonFinder.getPublishedById(lessonId);

        return LessonResponse.from(lesson);
    }

    /**
     * 학습자용 레슨 목록 조회입니다(#359).
     * 상위 유닛·커리큘럼이 모두 공개 상태일 때만 조회하며, 공개(PUBLISHED)된 레슨만 반환합니다.
     */
    @Transactional(readOnly = true)
    public PageResponse<LessonResponse> searchLessonsForUser(UUID unitId, Pageable pageable) {

        unitFinder.getPublishedById(unitId);

        Page<Lesson> lessons = lessonRepository.searchPublishedLessons(unitId, pageable);

        return PageResponse.from(lessons, LessonResponse::from);
    }

    /** 레슨 수정 */
    @Transactional(rollbackFor = Exception.class)
    public LessonResponse updateLesson(UUID lessonId, LessonUpdateRequest request) {

        Lesson lesson = lessonFinder.getById(lessonId);

        if (request.getTitle() != null) {
            lesson.changeTitle(request.getTitle());
        }

        if (request.getDescription() != null) {
            lesson.changeDescription(request.getDescription());
        }

        if (request.getContent() != null) {
            lesson.changeContent(request.getContent());
        }

        if (request.getLanguage() != null) {
            lesson.changeLanguage(request.getLanguage());
        }

        if (
                request.getDisplayOrder() != null
                        && !request.getDisplayOrder()
                        .equals(lesson.getDisplayOrder())
        ) {
            moveLesson(lesson, request.getDisplayOrder());
        }

        return LessonResponse.from(lesson);
    }

    /**
     * Lesson을 같은 Unit 안의 목표 자리로 옮기고, 형제 Lesson을 1..N으로 다시 정렬합니다(#324).
     *
     * <p>displayOrder는 "옮겨 갈 자리"로 해석합니다. 다른 형제가 쓰고 있는 번호로 옮기면
     * 그 사이의 형제들이 한 칸씩 밀리거나 당겨집니다.</p>
     *
     * <ul>
     *     <li>부모 Unit을 먼저 잠가 같은 Unit의 생성·순서 변경을 직렬화합니다.</li>
     *     <li>목표 자리가 1..(활성 형제 수)를 벗어나면 LESSON_DISPLAY_ORDER_OUT_OF_RANGE로 거절합니다.
     *     트랜잭션이 롤백되므로 같은 요청의 다른 필드 변경도 반영되지 않습니다.</li>
     *     <li>잠금을 기다리는 사이 Lesson이 삭제됐으면 LESSON_NOT_FOUND로 거절합니다.</li>
     * </ul>
     */
    private void moveLesson(Lesson lesson, int position) {

        unitFinder.lockById(lesson.getUnitId());

        List<Lesson> siblings = lessonRepository.findActiveSiblings(lesson.getUnitId());

        boolean stillActive = siblings.stream()
                .anyMatch(sibling -> sibling.getId().equals(lesson.getId()));

        if (!stillActive) {
            throw new BusinessException(ErrorCode.LESSON_NOT_FOUND);
        }

        if (!SiblingDisplayOrders.isInRange(position, siblings.size())) {
            throw new BusinessException(ErrorCode.LESSON_DISPLAY_ORDER_OUT_OF_RANGE);
        }

        lessonRepository.reorder(
                SiblingDisplayOrders.moveTo(
                        siblings,
                        Lesson::getId,
                        lesson.getId(),
                        position
                )
        );
    }

    /** 레슨 삭제 */
    @Transactional(rollbackFor = Exception.class)
    public void deleteLesson(UUID lessonId, UUID userId) {

        Lesson lesson = lessonFinder.getById(lessonId);

        // 생성·순서 변경과 같은 부모 락으로 삭제 및 번호 압축을 직렬화한다.
        unitFinder.lockById(lesson.getUnitId());
        List<Lesson> siblings = lessonRepository.findActiveSiblings(lesson.getUnitId());
        if (siblings.stream().noneMatch(sibling -> sibling.getId().equals(lessonId))) {
            throw new BusinessException(ErrorCode.LESSON_NOT_FOUND);
        }

        lesson.softDelete(userId);
        lessonRepository.reorder(siblings.stream()
                .filter(sibling -> !sibling.getId().equals(lessonId))
                .toList());
    }

    /**
     * 레슨이 다루는 개념(태그) 목록을 조회합니다(이슈 #291, 파생값).
     *
     * <p>레슨이 태그를 직접 소유하지 않고, lessonId로 연결된 문제들의
     * 태그 중 attribute=CONCEPT인 것만 모아서 중복 없이 반환합니다.
     * 원본(문제-태그 연결)을 별도로 복제 저장하지 않기 때문에, 문제의
     * 태그가 바뀌면 이 조회 결과도 항상 최신 상태를 반영합니다.</p>
     *
     * <p>레슨에 연결된 문제가 하나도 없으면 빈 목록을 반환합니다.</p>
     */
    @Transactional(readOnly = true)
    public List<TagResult> getLessonConcepts(UUID lessonId) {

        // 존재하지 않는 레슨에 대한 조회 방지
        lessonFinder.getById(lessonId);

        return findLessonConcepts(lessonId);
    }

    /**
     * 학습자용 레슨 개념 조회입니다(#359).
     * 레슨·유닛·커리큘럼이 모두 공개(PUBLISHED) 상태일 때만 조회합니다.
     */
    @Transactional(readOnly = true)
    public List<TagResult> getLessonConceptsForUser(UUID lessonId) {

        lessonFinder.getPublishedById(lessonId);

        return findLessonConcepts(lessonId);
    }

    private List<TagResult> findLessonConcepts(UUID lessonId) {

        List<UUID> problemIds = problemQueryRepository.findProblemIdsByLessonId(lessonId);

        if (problemIds.isEmpty()) {
            return List.of();
        }

        List<Tag> concepts = problemTagRepository
                .findDistinctTagsByProblemIdsAndAttribute(problemIds, TagAttribute.CONCEPT);

        return concepts.stream()
                .map(TagResult::from)
                .toList();
    }

    /**
     * 레슨을 학습자에게 공개합니다(#359). 이미 공개 상태면 그대로 둡니다.
     * 상위 유닛·커리큘럼이 비공개면 공개해도 학습자에게는 보이지 않습니다.
     */
    @Transactional(rollbackFor = Exception.class)
    public LessonResponse publishLesson(UUID lessonId) {

        Lesson lesson = lessonFinder.getById(lessonId);

        lesson.publish();

        return LessonResponse.from(lesson);
    }

    /** 레슨을 학습자 조회에서 내립니다(#359). 이미 비공개 상태면 그대로 둡니다. */
    @Transactional(rollbackFor = Exception.class)
    public LessonResponse unpublishLesson(UUID lessonId) {

        Lesson lesson = lessonFinder.getById(lessonId);

        lesson.unpublish();

        return LessonResponse.from(lesson);
    }
}
