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

    /** 문제 언어 검색 조건입니다. */
    private ProgrammingLanguage language;

    /** 문제 난이도 검색 조건입니다. */
    private ProblemDifficulty difficulty;

    /** 문제 유형 검색 조건입니다. */
    private ProblemType type;

    /** 문제 상태 검색 조건입니다. */
    private ProblemStatus problemStatus;

    /** 문제 출처 검색 조건입니다. */
    private ProblemSource source;
}