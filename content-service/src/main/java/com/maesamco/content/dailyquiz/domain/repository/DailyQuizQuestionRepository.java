package com.maesamco.content.dailyquiz.domain.repository;

import com.maesamco.content.dailyquiz.domain.entity.DailyQuizQuestion;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface DailyQuizQuestionRepository {

    DailyQuizQuestion save(DailyQuizQuestion question);

    List<DailyQuizQuestion> findActiveByAnyConcepts(List<String> conceptTags);

    /**
     * 사용자 세트에 배정된 특정 문제 버전들을 ID로 일괄 조회합니다.
     * 이미 배정된 문항은 현재 상태와 관계없이 재조회할 수 있어야 하므로
     * ACTIVE 상태 조건을 적용하지 않습니다.
     */
    List<DailyQuizQuestion> findAllById(Collection<UUID> questionIds);
}
