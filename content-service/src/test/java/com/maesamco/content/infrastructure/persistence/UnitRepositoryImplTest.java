package com.maesamco.content.infrastructure.persistence;

import com.maesamco.content.domain.entity.Unit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UnitRepositoryImplTest {

    @Mock SpringDataUnitRepository springDataUnitRepository;
    @InjectMocks UnitRepositoryImpl unitRepository;

    @Test
    void save_success() {
        Unit entity = mock(Unit.class);
        when(springDataUnitRepository.save(entity)).thenReturn(entity);
        assertThat(unitRepository.save(entity)).isSameAs(entity);
    }

    @Test
    void findById_success() {
        UUID id = UUID.randomUUID();
        Unit entity = mock(Unit.class);
        when(springDataUnitRepository.findByIdAndDeletedAtIsNull(id)).thenReturn(Optional.of(entity));
        assertThat(unitRepository.findById(id)).containsSame(entity);
    }

    @Test
    void findByIdForUpdate_success() {
        UUID id = UUID.randomUUID();
        Unit entity = mock(Unit.class);
        when(springDataUnitRepository.findByIdForUpdate(id)).thenReturn(Optional.of(entity));
        assertThat(unitRepository.findByIdForUpdate(id)).containsSame(entity);
    }

    @Test
    void findMaxDisplayOrderByCurriculumId_success() {
        UUID curriculumId = UUID.randomUUID();
        when(springDataUnitRepository.findMaxDisplayOrderByCurriculumId(curriculumId)).thenReturn(7);
        assertThat(unitRepository.findMaxDisplayOrderByCurriculumId(curriculumId)).isEqualTo(7);
    }

    @Test
    void searchUnits_success() {
        UUID curriculumId = UUID.randomUUID();
        Pageable pageable = PageRequest.of(0, 20);
        Page<Unit> page = Page.empty(pageable);
        when(springDataUnitRepository.findByCurriculumIdAndDeletedAtIsNullOrderByDisplayOrderAscIdAsc(curriculumId, pageable)).thenReturn(page);
        assertThat(unitRepository.searchUnits(curriculumId, pageable)).isSameAs(page);
    }

    @Test
    void findActiveSiblings_success() {
        UUID curriculumId = UUID.randomUUID();
        List<Unit> siblings = List.of(mock(Unit.class), mock(Unit.class));
        when(springDataUnitRepository.findByCurriculumIdAndDeletedAtIsNullOrderByDisplayOrderAscIdAsc(curriculumId)).thenReturn(siblings);
        assertThat(unitRepository.findActiveSiblings(curriculumId)).isSameAs(siblings);
    }

    @Test
    void reorder_assignsNegativeThenFinalOrdersWithFlushInBetween() {
        Unit first = mock(Unit.class);
        Unit second = mock(Unit.class);

        unitRepository.reorder(List.of(first, second));

        InOrder inOrder = inOrder(first, second, springDataUnitRepository);
        inOrder.verify(first).changeDisplayOrder(-1);
        inOrder.verify(second).changeDisplayOrder(-2);
        inOrder.verify(springDataUnitRepository).flush();
        inOrder.verify(first).changeDisplayOrder(1);
        inOrder.verify(second).changeDisplayOrder(2);
        inOrder.verify(springDataUnitRepository).flush();
    }
}
