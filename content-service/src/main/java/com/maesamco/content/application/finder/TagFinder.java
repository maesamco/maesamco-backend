package com.maesamco.content.application.finder;

import com.maesamco.content.domain.entity.Tag;

import java.util.UUID;

public interface TagFinder {

    /** 태그 단건 조회 */
    Tag getById(UUID tagId);
}