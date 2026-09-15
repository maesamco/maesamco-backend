package com.maesamco.judge.global.security.hmac;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * "/internal/v1/** 아래 모든 핸들러 메서드는 {@code @AllowedInternalCallers}를
 * 가져야 한다"는 관례를 자동으로 강제한다(이슈 #185 P1-2).
 *
 * AuthApiDocsContractTest처럼 특정 메서드를 이름으로 하나하나 지목하는 방식은
 * "새 내부 컨트롤러를 추가하면서 이 테스트 자체를 깜빡하면" 아무것도 못 잡는다
 * (InternalProblemController가 실제로 그랬던 사례 — 이슈 #138 PR의 diff에 아예
 * 등장하지 않아, 개별 지목 방식이었다면 이 공백 자체가 리뷰에서만 겨우 발견됐을
 * 것이다). 이 테스트는 클래스패스를 실제로 스캔해서 전수 검사하므로, 사람이
 * 애노테이션을 빠뜨려도 CI가 자동으로 잡아준다.
 */
class InternalControllerAuthorizationContractTest {

    private static final String BASE_PACKAGE = "com.maesamco.judge";
    private static final String INTERNAL_PATH_PREFIX = "/internal/v1";

    @Test
    @DisplayName("/internal/v1/** 핸들러 메서드는 전부 @AllowedInternalCallers를 가져야 한다")
    void allInternalHandlersMustDeclareAllowedInternalCallers() {
        List<String> violations = new ArrayList<>();

        for (Class<?> controllerClass : findRestControllerClasses()) {
            String classPath = classLevelPath(controllerClass);

            for (Method method : controllerClass.getDeclaredMethods()) {
                String methodPath = methodLevelPath(method);
                if (methodPath == null) {
                    continue; // 매핑 애노테이션이 없는 메서드(헬퍼 등)는 대상이 아님
                }

                String fullPath = normalize(classPath) + normalize(methodPath);
                if (!fullPath.startsWith(INTERNAL_PATH_PREFIX)) {
                    continue;
                }

                boolean hasAnnotation = method.isAnnotationPresent(AllowedInternalCallers.class)
                        || controllerClass.isAnnotationPresent(AllowedInternalCallers.class);

                if (!hasAnnotation) {
                    violations.add(controllerClass.getSimpleName() + "#" + method.getName()
                            + " (" + fullPath + ")");
                }
            }
        }

        assertThat(violations)
                .as("다음 /internal/v1/** 핸들러에 @AllowedInternalCallers가 없습니다 — "
                                + "서명만 유효하면 어떤 내부 서비스든 호출 가능한 상태로 남아있습니다: %s",
                        violations)
                .isEmpty();
    }

    private List<Class<?>> findRestControllerClasses() {
        ClassPathScanningCandidateComponentProvider scanner =
                new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(RestController.class));

        List<Class<?>> classes = new ArrayList<>();
        for (var candidate : scanner.findCandidateComponents(BASE_PACKAGE)) {
            try {
                classes.add(Class.forName(candidate.getBeanClassName()));
            } catch (ClassNotFoundException e) {
                throw new IllegalStateException(e);
            }
        }
        return classes;
    }

    private String classLevelPath(Class<?> controllerClass) {
        RequestMapping mapping = controllerClass.getAnnotation(RequestMapping.class);
        if (mapping == null || mapping.value().length == 0) {
            return "";
        }
        return mapping.value()[0];
    }

    /** null이면 매핑 애노테이션 자체가 없다는 뜻(대상 제외), 빈 문자열이면 클래스 경로 그대로 매핑된다는 뜻. */
    private String methodLevelPath(Method method) {
        if (method.isAnnotationPresent(GetMapping.class)) {
            return firstOrEmpty(method.getAnnotation(GetMapping.class).value());
        }
        if (method.isAnnotationPresent(PostMapping.class)) {
            return firstOrEmpty(method.getAnnotation(PostMapping.class).value());
        }
        if (method.isAnnotationPresent(PutMapping.class)) {
            return firstOrEmpty(method.getAnnotation(PutMapping.class).value());
        }
        if (method.isAnnotationPresent(PatchMapping.class)) {
            return firstOrEmpty(method.getAnnotation(PatchMapping.class).value());
        }
        if (method.isAnnotationPresent(DeleteMapping.class)) {
            return firstOrEmpty(method.getAnnotation(DeleteMapping.class).value());
        }
        if (method.isAnnotationPresent(RequestMapping.class)) {
            return firstOrEmpty(method.getAnnotation(RequestMapping.class).value());
        }
        return null;
    }

    private String firstOrEmpty(String[] values) {
        return values.length == 0 ? "" : values[0];
    }

    private String normalize(String path) {
        if (path == null || path.isBlank()) {
            return "";
        }
        return path.startsWith("/") ? path : "/" + path;
    }
}