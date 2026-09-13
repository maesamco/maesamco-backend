package com.maesamco.content.quicktest;

import com.maesamco.content.curriculum.application.port.CurriculumFinder;
import com.maesamco.content.curriculum.domain.entity.Curriculum;
import com.maesamco.content.unit.application.port.UnitFinder;
import com.maesamco.content.unit.application.service.UnitService;
import com.maesamco.content.unit.domain.entity.Unit;
import com.maesamco.content.unit.domain.enums.ProgrammingLanguage;
import com.maesamco.content.unit.domain.repository.UnitRepository;
import com.maesamco.content.unit.presentation.dto.request.UnitCreateRequest;
import com.maesamco.content.unit.presentation.dto.request.UnitUpdateRequest;
import com.maesamco.content.unit.presentation.dto.response.UnitResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UnitServiceTest {

    @Mock
    private UnitRepository unitRepository;

    @Mock
    private UnitFinder unitFinder;

    @Mock
    private CurriculumFinder curriculumFinder;

    @InjectMocks
    private UnitService unitService;


    @Test
    @DisplayName("유닛 단건 조회 시 유닛 정보를 반환한다.")
    void getUnit_success() {

        // given
        UUID unitId = UUID.randomUUID();
        UUID curriculumId = UUID.randomUUID();

        Unit unit = mock(Unit.class);

        when(unitFinder.findById(unitId)).thenReturn(unit);
        when(unit.getId()).thenReturn(unitId);
        when(unit.getCurriculumId()).thenReturn(curriculumId);
        when(unit.getTitle()).thenReturn("Java 기본 문법");
        when(unit.getLanguage()).thenReturn(ProgrammingLanguage.JAVA);
        when(unit.getDisplayOrder()).thenReturn(1);

        // when
        UnitResponse response = unitService.getUnit(unitId);

        // then
        assertThat(response.getId()).isEqualTo(unitId);
        assertThat(response.getCurriculumId()).isEqualTo(curriculumId);
        assertThat(response.getTitle()).isEqualTo("Java 기본 문법");
        assertThat(response.getLanguage()).isEqualTo(ProgrammingLanguage.JAVA);
        assertThat(response.getDisplayOrder()).isEqualTo(1);

        System.out.println("===== 유닛 단건 조회 결과 =====");
        System.out.println("unitId = " + response.getId());
        System.out.println("curriculumId = " + response.getCurriculumId());
        System.out.println("title = " + response.getTitle());
        System.out.println("language = " + response.getLanguage());
        System.out.println("displayOrder = " + response.getDisplayOrder());
    }


    @Test
    @DisplayName("커리큘럼에 새로운 유닛을 생성한다.")
    void createUnit_success() {

        // given
        UUID curriculumId = UUID.randomUUID();

        Curriculum curriculum = mock(Curriculum.class);
        UnitCreateRequest request = mock(UnitCreateRequest.class);

        when(request.getCurriculumId()).thenReturn(curriculumId);
        when(request.getTitle()).thenReturn("조건문");
        when(request.getLanguage()).thenReturn(ProgrammingLanguage.JAVA);

        when(curriculumFinder.findById(curriculumId)).thenReturn(curriculum);
        when(unitRepository.countByCurriculumId(curriculumId)).thenReturn(2L);
        when(unitRepository.save(any(Unit.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // when
        unitService.createUnit(request);

        // then
        ArgumentCaptor<Unit> captor = ArgumentCaptor.forClass(Unit.class);

        org.mockito.Mockito.verify(unitRepository).save(captor.capture());

        Unit savedUnit = captor.getValue();

        assertThat(savedUnit.getCurriculumId()).isEqualTo(curriculumId);
        assertThat(savedUnit.getTitle()).isEqualTo("조건문");
        assertThat(savedUnit.getLanguage()).isEqualTo(ProgrammingLanguage.JAVA);
        assertThat(savedUnit.getDisplayOrder()).isEqualTo(3);

        System.out.println("===== 유닛 생성 결과 =====");
        System.out.println("curriculumId = " + savedUnit.getCurriculumId());
        System.out.println("title = " + savedUnit.getTitle());
        System.out.println("language = " + savedUnit.getLanguage());
        System.out.println("displayOrder = " + savedUnit.getDisplayOrder());
    }


    @Test
    @DisplayName("유닛의 제목을 수정한다.")
    void updateUnit_success() {

        // given
        UUID unitId = UUID.randomUUID();
        UUID curriculumId = UUID.randomUUID();

        Unit unit = Unit.create(
                curriculumId,
                "기존 제목",
                ProgrammingLanguage.JAVA,
                1
        );

        UnitUpdateRequest request = mock(UnitUpdateRequest.class);

        when(unitFinder.findById(unitId)).thenReturn(unit);
        when(request.getTitle()).thenReturn("수정된 제목");
        when(request.getLanguage()).thenReturn(null);

        // when
        unitService.updateUnit(unitId, request);

        // then
        assertThat(unit.getTitle()).isEqualTo("수정된 제목");
        assertThat(unit.getLanguage()).isEqualTo(ProgrammingLanguage.JAVA);
        assertThat(unit.getDisplayOrder()).isEqualTo(1);

        System.out.println("===== 유닛 수정 결과 =====");
        System.out.println("title = " + unit.getTitle());
        System.out.println("language = " + unit.getLanguage());
        System.out.println("displayOrder = " + unit.getDisplayOrder());
    }
}