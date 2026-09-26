package com.maesamco.content.presentation.api_controller;

import com.maesamco.content.application.persistence_service.CurriculumService;
import com.maesamco.content.application.persistence_service.LessonService;
import com.maesamco.content.application.persistence_service.UnitService;
import com.maesamco.content.support.ControllerEndpointScanner;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.asm.ClassReader;
import org.springframework.asm.ClassVisitor;
import org.springframework.asm.Handle;
import org.springframework.asm.MethodVisitor;
import org.springframework.asm.SpringAsmInfo;
import org.springframework.asm.Type;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 공개 상태와 관계없이(DRAFT 포함) 조회하는 서비스 메서드는 관리자 컨트롤러에서만 호출되는지 검사합니다(#366 리뷰 P3).
 *
 * <p>학습자용 조회는 ...ForUser 메서드를 써야 하는데, 접미사 없는 메서드를 습관적으로 호출하면
 * 비공개 콘텐츠가 그대로 노출됩니다. 이 테스트는 모든 {@code @RestController}의 바이트코드를 스캔해
 * 아래 메서드를 {@link AdminContentController} 외의 컨트롤러가 호출하면(메서드 참조 포함) 실패합니다.</p>
 */
@DisplayName("관리자 전용 콘텐츠 조회 메서드 호출 가드 (#366)")
class AdminOnlyContentReadGuardTest {

    /** 공개 상태와 관계없이 조회하는 서비스 메서드(서비스 클래스 → 메서드 이름) */
    private static final Map<Class<?>, Set<String>> ADMIN_ONLY_READS = Map.of(
            CurriculumService.class, Set.of("getCurriculum", "searchCurriculums"),
            UnitService.class, Set.of("getUnit", "searchUnits"),
            LessonService.class, Set.of("getLesson", "searchLessons", "getLessonConcepts")
    );

    private static final Set<Class<?>> ALLOWED_CALLERS = Set.of(AdminContentController.class);

    @Test
    @DisplayName("공개 상태와 관계없이 조회하는 서비스 메서드는 AdminContentController에서만 호출한다")
    void adminOnlyReads_areCalledOnlyFromAdminController() {
        // given
        List<Class<?>> controllers = ControllerEndpointScanner.controllerClasses();

        // when
        List<String> violations = new ArrayList<>();
        for (Class<?> controller : controllers) {
            if (!ALLOWED_CALLERS.contains(controller)) {
                violations.addAll(findAdminOnlyReadCalls(controller));
            }
        }

        // then
        assertThat(controllers).contains(AdminContentController.class, CurriculumController.class,
                UnitController.class, LessonController.class);
        assertThat(violations)
                .as("학습자 API는 ...ForUser 메서드를 호출해야 합니다. 관리자 조회가 필요하면 AdminContentController에 두세요.")
                .isEmpty();
    }

    @Test
    @DisplayName("가드는 관리자 컨트롤러의 호출을 실제로 찾아낸다 (스캔 자체가 동작하는지 확인)")
    void scanner_detectsCallsInAdminController() {
        // when
        List<String> calls = findAdminOnlyReadCalls(AdminContentController.class);

        // then — 관리자 컨트롤러는 7개 메서드 중 6개를 호출한다(getLessonConcepts는 관리자 API 없음)
        assertThat(calls).anyMatch(call -> call.endsWith("CurriculumService#getCurriculum"));
        assertThat(calls).anyMatch(call -> call.endsWith("CurriculumService#searchCurriculums"));
        assertThat(calls).anyMatch(call -> call.endsWith("UnitService#getUnit"));
        assertThat(calls).anyMatch(call -> call.endsWith("UnitService#searchUnits"));
        assertThat(calls).anyMatch(call -> call.endsWith("LessonService#getLesson"));
        assertThat(calls).anyMatch(call -> call.endsWith("LessonService#searchLessons"));
    }

    private static List<String> findAdminOnlyReadCalls(Class<?> controller) {
        List<String> calls = new ArrayList<>();
        String resource = "/" + controller.getName().replace('.', '/') + ".class";

        try (InputStream in = controller.getResourceAsStream(resource)) {
            assertThat(in).as("클래스 파일을 찾을 수 없음: " + resource).isNotNull();

            new ClassReader(in).accept(new ClassVisitor(SpringAsmInfo.ASM_VERSION) {
                @Override
                public MethodVisitor visitMethod(int access, String name, String descriptor,
                                                 String signature, String[] exceptions) {
                    String caller = controller.getSimpleName() + "#" + name;
                    return new MethodVisitor(SpringAsmInfo.ASM_VERSION) {
                        @Override
                        public void visitMethodInsn(int opcode, String owner, String methodName,
                                                    String methodDescriptor, boolean isInterface) {
                            collectIfAdminOnlyRead(caller, owner, methodName, calls);
                        }

                        @Override
                        public void visitInvokeDynamicInsn(String methodName, String methodDescriptor,
                                                           Handle bootstrapMethod, Object... bootstrapArguments) {
                            // 메서드 참조(service::getLesson)는 invokedynamic의 Handle 인자로 들어온다.
                            for (Object argument : bootstrapArguments) {
                                if (argument instanceof Handle handle) {
                                    collectIfAdminOnlyRead(caller, handle.getOwner(), handle.getName(), calls);
                                }
                            }
                        }
                    };
                }
            }, 0);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }

        return calls;
    }

    private static void collectIfAdminOnlyRead(String caller, String owner, String methodName, List<String> calls) {
        ADMIN_ONLY_READS.forEach((service, methods) -> {
            if (Type.getInternalName(service).equals(owner) && methods.contains(methodName)) {
                calls.add(caller + " → " + service.getSimpleName() + "#" + methodName);
            }
        });
    }
}
