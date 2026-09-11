package com.maesamco.content.tag.domain.repository;

import com.maesamco.content.tag.domain.entity.Tag;
import com.maesamco.content.tag.domain.enums.TagAttribute;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface TagSearchRepository {

    /** 전체 태그 목록 조회 */
    Page<Tag> searchTags(Pageable pageable);

    /** 특정 속성의 태그 목록 조회 */
    Page<Tag> searchTagsByAttribute(TagAttribute attribute, Pageable pageable);
}