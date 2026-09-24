package com.maesamco.content.support;

import com.maesamco.content.global.security.authorization.RequireAdmin;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 스프링 컨텍스트를 띄우지 않고 클래스패스의 모든 {@code @RestController}를 스캔해서
 * 핸들러(HTTP 메서드 + 경로)와 {@link RequireAdmin} 적용 여부를 뽑아낸다(이슈 #311).
 *
 * <p>#313 이후 매핑 애노테이션은 {@code *ApiDocs} 인터페이스에 있으므로
 * {@link AnnotatedElementUtils#findMergedAnnotation}으로 인터페이스까지 따라간다.
 * {@link RequireAdmin}은 구현체 쪽에 붙으므로 메서드/클래스 양쪽에서 찾는다.</p>
 */
public final class ControllerEndpointScanner {

    private static final String BASE_PACKAGE = "com.maesamco.content.presentation";

    private ControllerEndpointScanner() {
    }

    public record Endpoint(Class<?> controller, String methodName, String httpMethod, String path, boolean requiresAdmin) {

        public String signature() {
            return httpMethod + " " + path;
        }

        @Override
        public String toString() {
            return controller.getSimpleName() + "#" + methodName + " [" + signature() + "]";
        }
    }

    /** 클래스패스의 모든 {@code @RestController} 클래스를 이름순으로 반환한다. */
    public static List<Class<?>> controllerClasses() {
        ClassPathScanningCandidateComponentProvider provider =
                new ClassPathScanningCandidateComponentProvider(false);
        provider.addIncludeFilter(new AnnotationTypeFilter(RestController.class));

        return provider.findCandidateComponents(BASE_PACKAGE).stream()
                .map(definition -> load(definition.getBeanClassName()))
                .sorted(Comparator.comparing(Class::getName))
                .collect(Collectors.toList());
    }

    public static List<Endpoint> scan() {
        List<Endpoint> endpoints = new ArrayList<>();

        for (Class<?> controller : controllerClasses()) {
            RequestMapping classMapping = AnnotatedElementUtils.findMergedAnnotation(controller, RequestMapping.class);
            String basePath = classMapping == null ? "" : firstPath(classMapping);

            for (Method method : controller.getMethods()) {
                RequestMapping methodMapping = AnnotatedElementUtils.findMergedAnnotation(method, RequestMapping.class);
                if (methodMapping == null || method.getDeclaringClass() == Object.class) {
                    continue;
                }

                boolean requiresAdmin = AnnotatedElementUtils.hasAnnotation(method, RequireAdmin.class)
                        || AnnotatedElementUtils.hasAnnotation(controller, RequireAdmin.class);

                String path = basePath + firstPath(methodMapping);
                // 메서드 조건이 비어 있으면 모든 HTTP 메서드를 받는 것이므로 보수적으로 변경 요청으로 취급한다.
                String[] httpMethods = methodMapping.method().length == 0
                        ? new String[]{"ANY"}
                        : Arrays.stream(methodMapping.method()).map(RequestMethod::name).toArray(String[]::new);

                for (String httpMethod : httpMethods) {
                    endpoints.add(new Endpoint(controller, method.getName(), httpMethod, path, requiresAdmin));
                }
            }
        }

        endpoints.sort(Comparator.comparing(Endpoint::path).thenComparing(Endpoint::httpMethod));
        return endpoints;
    }

    private static String firstPath(RequestMapping mapping) {
        String[] paths = mapping.path().length > 0 ? mapping.path() : mapping.value();
        return paths.length == 0 ? "" : paths[0];
    }

    private static Class<?> load(String className) {
        try {
            return Class.forName(className);
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException("컨트롤러 클래스를 불러오지 못했습니다: " + className, e);
        }
    }
}
