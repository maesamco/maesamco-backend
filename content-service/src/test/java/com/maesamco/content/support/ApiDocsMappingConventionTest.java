package com.maesamco.content.support;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.web.bind.annotation.RequestMapping;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@code *ApiDocs} 인터페이스를 구현하는 컨트롤러의 "매핑은 인터페이스에만 둔다" 규칙을 지킨다(#313 리뷰 P4).
 *
 * <p>Swagger 문서와 요청 매핑({@code @GetMapping} 등)은 {@code *ApiDocs}에 선언하고 구현체는
 * {@code @Override}와 보안 애노테이션만 갖는다. 구현체에 매핑을 다시 붙이면 인터페이스와 이중 정의가 되어
 * 어느 쪽이 실제 경로인지 알 수 없고, 메서드 보안 프록시가 JDK 동적 프록시로 결정되는 설정에서는
 * 구현체의 매핑이 {@code HandlerMethod}에 보이지 않아 라우트가 조용히 사라진다(#313 CI 실패 원인).
 * 지금은 {@code SecurityConfig}가 {@code proxyTargetClass = true}라 동작하지만, 그 설정에 기대지 않고
 * 규칙 자체를 정적으로 확인한다. 스프링 컨텍스트를 띄우지 않으므로 빠르다.</p>
 */
class ApiDocsMappingConventionTest {

    @Test
    @DisplayName("*ApiDocs를 구현하는 컨트롤러의 핸들러 메서드는 매핑을 인터페이스 메서드에 두고 구현체에는 다시 붙이지 않는다")
    void controllersImplementingApiDocs_keepMappingsOnInterfaceOnly() {
        List<String> violations = new ArrayList<>();
        int checkedControllers = 0;

        for (Class<?> controller : ControllerEndpointScanner.controllerClasses()) {
            List<Class<?>> apiDocs = Arrays.stream(controller.getInterfaces())
                    .filter(type -> type.getSimpleName().endsWith("ApiDocs"))
                    .toList();
            if (apiDocs.isEmpty()) {
                continue;
            }
            checkedControllers++;

            // 클래스 레벨 매핑도 인터페이스에만 둔다.
            if (AnnotatedElementUtils.getMergedAnnotation(controller, RequestMapping.class) != null) {
                violations.add(controller.getSimpleName() + " — 클래스에 @RequestMapping이 있다 (인터페이스로 옮길 것)");
            }

            for (Method implMethod : controller.getDeclaredMethods()) {
                Method docsMethod = findDocsMethod(apiDocs, implMethod);
                if (docsMethod == null) {
                    continue;
                }

                // getMergedAnnotation은 해당 메서드에 직접 붙은 것만 본다(인터페이스는 따라가지 않는다).
                if (AnnotatedElementUtils.getMergedAnnotation(implMethod, RequestMapping.class) != null) {
                    violations.add(controller.getSimpleName() + "#" + implMethod.getName()
                            + " — 구현체 메서드에 매핑이 붙어 있다 (" + docsMethod.getDeclaringClass().getSimpleName() + "로 옮길 것)");
                }
                if (AnnotatedElementUtils.findMergedAnnotation(docsMethod, RequestMapping.class) == null) {
                    violations.add(controller.getSimpleName() + "#" + implMethod.getName()
                            + " — " + docsMethod.getDeclaringClass().getSimpleName() + " 메서드에 매핑이 없다");
                }
            }
        }

        assertThat(checkedControllers)
                .as("*ApiDocs를 구현하는 컨트롤러를 하나도 찾지 못했다 — 스캔 대상이 바뀌었는지 확인할 것")
                .isPositive();
        assertThat(violations).as("매핑 규칙 위반").isEmpty();
    }

    private static Method findDocsMethod(List<Class<?>> apiDocs, Method implMethod) {
        for (Class<?> docs : apiDocs) {
            try {
                return docs.getMethod(implMethod.getName(), implMethod.getParameterTypes());
            } catch (NoSuchMethodException ignored) {
                // 다음 인터페이스에서 찾는다.
            }
        }
        return null;
    }
}
