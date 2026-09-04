package com.maesamco.content.problem.presentation.dto.request;

import com.maesamco.content.problem.domain.enums.ProblemDifficulty;
import com.maesamco.content.problem.domain.enums.ProblemSource;
import com.maesamco.content.problem.domain.enums.ProblemStatus;
import com.maesamco.content.problem.domain.enums.ProblemType;
import com.maesamco.content.problem.domain.enums.ProgrammingLanguage;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 문제 목록 조회 시 사용하는 검색 조건을 전달합니다.
 *
 * <p>각 검색 조건은 선택적으로 전달할 수 있으며,
 * 여러 조건이 함께 전달되면 조합하여 문제 목록을 조회합니다.</p>
 *
 * <p>페이징 및 정렬 조건은 {@code PageableFactory}를 통해
 * Spring Data JPA의 Pageable 객체로 변환하여 사용합니다.</p>
 */
@Getter
@NoArgsConstructor
public class ProblemSearchRequest {

    /**
     * 문제 언어 검색 조건입니다.
     */
    private ProgrammingLanguage language;

    /**
     * 문제 난이도 검색 조건입니다.
     */
    private ProblemDifficulty difficulty;

    /**
     * 문제 유형 검색 조건입니다.
     */
    private ProblemType type;

    /**
     * 문제 상태 검색 조건입니다.
     */
    private ProblemStatus problemStatus;

    /**
     * 문제 출처 검색 조건입니다.
     */
    private ProblemSource source;

    /**
     * 조회할 페이지 번호입니다.
     *
     * <p>0부터 시작하며, 유효하지 않은 값은
     * PageableFactory의 기본값으로 대체합니다.</p>
     */
    private Integer page;

    /**
     * 한 페이지에 조회할 문제 개수입니다.
     *
     * <p>유효하지 않은 값은 PageableFactory의
     * 기본 크기로 대체합니다.</p>
     */
    private Integer size;

    /**
     * 정렬 기준으로 사용할 필드명입니다.
     *
     * <p>값이 없거나 비어 있으면 PageableFactory의
     * 기본 정렬 필드를 사용합니다.</p>
     */
    private String sort;

    /**
     * 정렬 방향입니다.
     *
     * <p>{@code ASC} 또는 {@code DESC} 값을 사용하며,
     * 유효하지 않은 값은 기본 정렬 방향으로 대체합니다.</p>
     */
    private String direction;
}