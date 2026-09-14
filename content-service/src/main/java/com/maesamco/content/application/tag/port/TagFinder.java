package com.maesamco.content.application.tag.port;

import com.maesamco.content.domain.tag.entity.Tag;

import java.util.UUID;

public interface TagFinder {

    /** 태그 단건 조회 */
    Tag getTag(UUID tagId);
}