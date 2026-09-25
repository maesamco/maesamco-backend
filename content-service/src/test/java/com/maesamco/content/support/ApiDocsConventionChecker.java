package com.maesamco.content.support;

import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.web.bind.annotation.RequestMapping;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

/**
 * 컨트롤러와 {@code *ApiDocs} 인터페이스의 역할 분리 규칙을 정적으로 검사한다(#313 리뷰 P4, #326).
 *
 * <p>Swagger 문서와 요청 매핑({@code @GetMapping} 등)은 {@code FooApiDocs}에 선언하고
 * {@code FooController}는 {@code @Override}와 보안 애노테이션만 갖는다. 구현체에 매핑이 남아 있으면
 * 메서드 보안 프록시가 JDK 동적 프록시로 결정되는 설정에서 구현체의 매핑이 {@code HandlerMethod}에 보이지 않아
 * 라우트가 조용히 사라진다(#313 CI 실패 원인). 운영 {@code SecurityConfig}가
 * {@code proxyTargetClass = true}라 지금은 동작하지만, 그 설정에 기대지 않고 구조 규칙 자체를 지킨다.</p>
 *
 * <p>위반 목록을 반환하는 순수 함수라서 실제 컨트롤러뿐 아니라 잘못된 구조의 가짜 컨트롤러로도
 * 각 규칙이 위반을 잡는지 검증할 수 있다({@code ApiDocsMappingConventionTest}).</p>
 *
 * <p>검사하는 규칙</p>
 * <ol>
 *     <li>{@code FooController}와 같은 위치에 {@code FooApiDocs}가 있으면 반드시 구현한다
 *     (구현을 잃고 매핑을 구현체로 옮기는 회귀 차단)</li>
 *     <li>구현체 클래스와 구현체가 선언한 <b>모든</b> 메서드에 요청 매핑을 붙이지 않는다
 *     (ApiDocs에 없는 구현체 전용 핸들러 추가 차단)</li>
 *     <li>ApiDocs를 오버라이드하는 메서드는 ApiDocs 쪽에 요청 매핑이 있어야 한다</li>
 *     <li>ApiDocs 인터페이스는 base path를 클래스 레벨 {@code @RequestMapping}으로 선언한다</li>
 * </ol>
 */
public final class ApiDocsConventionChecker {

    private static final String DOCS_SUFFIX = "ApiDocs";
    private static final String CONTROLLER_SUFFIX = "Controller";

    private ApiDocsConventionChecker() {
    }

    public static List<String> check(Collection<Class<?>> controllers) {
        List<String> violations = new ArrayList<>();

        for (Class<?> controller : controllers) {
            String name = controller.getSimpleName();
            Class<?> docs = findDocsByName(controller);

            if (docs == null) {
                continue; // ApiDocs를 두지 않은 컨트롤러는 이 규칙의 대상이 아니다.
            }

            if (!docs.isAssignableFrom(controller)) {
                violations.add(name + " — " + docs.getSimpleName() + "가 있지만 implements하지 않는다 "
                        + "(구현을 잃으면 매핑·문서 분리 구조가 깨진다)");
                continue;
            }

            if (AnnotatedElementUtils.getMergedAnnotation(docs, RequestMapping.class) == null) {
                violations.add(docs.getSimpleName() + " — 클래스 레벨 @RequestMapping(base path)이 없다");
            }

            if (AnnotatedElementUtils.getMergedAnnotation(controller, RequestMapping.class) != null) {
                violations.add(name + " — 구현체 클래스에 @RequestMapping이 있다 (" + docs.getSimpleName() + "로 옮길 것)");
            }

            for (Method implMethod : controller.getDeclaredMethods()) {
                if (implMethod.isSynthetic() || implMethod.isBridge()) {
                    continue;
                }

                // getMergedAnnotation은 해당 메서드에 직접 붙은 것만 본다(인터페이스는 따라가지 않는다).
                if (AnnotatedElementUtils.getMergedAnnotation(implMethod, RequestMapping.class) != null) {
                    violations.add(name + "#" + implMethod.getName()
                            + " — 구현체 메서드에 요청 매핑이 있다 (" + docs.getSimpleName() + "에 선언할 것)");
                }

                Method docsMethod = findMethod(docs, implMethod);
                if (docsMethod != null
                        && AnnotatedElementUtils.findMergedAnnotation(docsMethod, RequestMapping.class) == null) {
                    violations.add(name + "#" + implMethod.getName()
                            + " — " + docs.getSimpleName() + " 메서드에 요청 매핑이 없다");
                }
            }
        }

        return violations;
    }

    /** {@code FooController}와 같은 패키지(또는 같은 바깥 클래스)의 {@code FooApiDocs}를 찾는다. */
    private static Class<?> findDocsByName(Class<?> controller) {
        String simpleName = controller.getSimpleName();
        if (!simpleName.endsWith(CONTROLLER_SUFFIX)) {
            return null;
        }

        String docsName = simpleName.substring(0, simpleName.length() - CONTROLLER_SUFFIX.length()) + DOCS_SUFFIX;
        String binaryName = controller.getEnclosingClass() != null
                ? controller.getEnclosingClass().getName() + "$" + docsName
                : controller.getPackageName() + "." + docsName;

        try {
            return Class.forName(binaryName, false, controller.getClassLoader());
        } catch (ClassNotFoundException e) {
            return null;
        }
    }

    private static Method findMethod(Class<?> docs, Method implMethod) {
        return Arrays.stream(docs.getMethods())
                .filter(method -> method.getName().equals(implMethod.getName()))
                .filter(method -> Arrays.equals(method.getParameterTypes(), implMethod.getParameterTypes()))
                .findFirst()
                .orElse(null);
    }
}
