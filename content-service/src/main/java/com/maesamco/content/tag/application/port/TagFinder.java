package com.maesamco.content.tag.application.port;

import com.maesamco.content.tag.domain.entity.Tag;

import java.util.UUID;

public interface TagFinder {

    /** 태그 단건 조회 */
    Tag getTag(UUID tagId);
}