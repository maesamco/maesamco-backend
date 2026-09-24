package com.maesamco.content.infrastructure.persistence;

import com.maesamco.content.domain.entity.Curriculum;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CurriculumRepositoryImplTest {

    @Mock SpringDataCurriculumRepository springDataCurriculumRepository;
    @InjectMocks CurriculumRepositoryImpl curriculumRepository;

    @Test
    void save_success() {
        Curriculum entity = mock(Curriculum.class);
        when(springDataCurriculumRepository.save(entity)).thenReturn(entity);
        assertThat(curriculumRepository.save(entity)).isSameAs(entity);
    }

    @Test
    void findById_success() {
        UUID id = UUID.randomUUID();
        Curriculum entity = mock(Curriculum.class);
        when(springDataCurriculumRepository.findByIdAndDeletedAtIsNull(id)).thenReturn(Optional.of(entity));
        assertThat(curriculumRepository.findById(id)).containsSame(entity);
    }

    @Test
    void findByIdForUpdate_success() {
        UUID id = UUID.randomUUID();
        Curriculum entity = mock(Curriculum.class);
        when(springDataCurriculumRepository.findByIdForUpdate(id)).thenReturn(Optional.of(entity));
        assertThat(curriculumRepository.findByIdForUpdate(id)).containsSame(entity);
    }

    @Test
    void searchCurriculums_success() {
        Pageable pageable = PageRequest.of(0, 20);
        Page<Curriculum> page = Page.empty(pageable);
        when(springDataCurriculumRepository.findByDeletedAtIsNullOrderByIdAsc(pageable)).thenReturn(page);
        assertThat(curriculumRepository.searchCurriculums(pageable)).isSameAs(page);
    }
}