package com.maesamco.content.support;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link ApiDocsConventionChecker}의 규칙을 실제 컨트롤러와 잘못된 구조의 가짜 컨트롤러로 검증한다(#326).
 *
 * <p>실제 컨트롤러 검사는 "지금 위반이 없다"만 확인하므로, 각 규칙이 정말 위반을 잡는지는
 * 아래 가짜 컨트롤러(Fixture)로 따로 증명한다. 스프링 컨텍스트를 띄우지 않아 빠르다.</p>
 */
class ApiDocsMappingConventionTest {

    @Test
    @DisplayName("실제 컨트롤러는 모두 매핑을 *ApiDocs에만 두고 구현을 유지한다")
    void realControllers_followConvention() {
        List<Class<?>> controllers = ControllerEndpointScanner.controllerClasses();

        assertThat(ApiDocsConventionChecker.check(controllers)).isEmpty();
    }

    @Test
    @DisplayName("모든 *ApiDocs 인터페이스는 대응하는 컨트롤러가 구현한다 (구현·파일 이탈 방지)")
    void everyApiDocs_isImplementedByItsController() {
        List<Class<?>> controllers = ControllerEndpointScanner.controllerClasses();

        Set<String> implemented = controllers.stream()
                .flatMap(controller -> java.util.Arrays.stream(controller.getInterfaces()))
                .map(Class::getSimpleName)
                .filter(name -> name.endsWith("ApiDocs"))
                .collect(Collectors.toSet());

        assertThat(implemented)
                .as("검사 대상 ApiDocs가 하나도 없거나, 스캔 범위가 바뀌었는지 확인할 것")
                .isNotEmpty();

        Set<String> declared = ControllerEndpointScanner.apiDocsInterfaces().stream()
                .map(Class::getSimpleName)
                .collect(Collectors.toSet());

        assertThat(declared)
                .as("구현하는 컨트롤러가 없는 *ApiDocs가 있다")
                .isSubsetOf(implemented);
    }

    @Test
    @DisplayName("규칙 2 — ApiDocs에 없는 구현체 전용 핸들러를 추가하면 잡는다")
    void detects_implOnlyHandler() {
        List<String> violations = ApiDocsConventionChecker.check(List.of(ExtraHandlerController.class));

        assertThat(violations).anyMatch(v -> v.contains("ExtraHandlerController#extra") && v.contains("구현체 메서드에 요청 매핑"));
    }

    @Test
    @DisplayName("규칙 2 — 기존 ApiDocs 메서드에 구현체가 매핑을 다시 붙이면 잡는다")
    void detects_duplicatedMappingOnImpl() {
        List<String> violations = ApiDocsConventionChecker.check(List.of(DuplicatedMappingController.class));

        assertThat(violations).anyMatch(v -> v.contains("DuplicatedMappingController#hello") && v.contains("구현체 메서드에 요청 매핑"));
    }

    @Test
    @DisplayName("규칙 1 — 컨트롤러가 *ApiDocs 구현을 잃으면 잡는다")
    void detects_lostImplements() {
        List<String> violations = ApiDocsConventionChecker.check(List.of(DetachedController.class));

        assertThat(violations).anyMatch(v -> v.contains("DetachedController") && v.contains("implements하지 않는다"));
    }

    @Test
    @DisplayName("규칙 4 — ApiDocs의 클래스 레벨 @RequestMapping이 사라지면 잡는다")
    void detects_missingClassLevelMappingOnDocs() {
        List<String> violations = ApiDocsConventionChecker.check(List.of(NoBasePathController.class));

        assertThat(violations).anyMatch(v -> v.contains("NoBasePathApiDocs") && v.contains("클래스 레벨 @RequestMapping"));
    }

    @Test
    @DisplayName("규칙을 지키는 컨트롤러는 위반이 없다")
    void cleanController_hasNoViolations() {
        assertThat(ApiDocsConventionChecker.check(List.of(CleanController.class))).isEmpty();
    }

    // ===== Fixture: 규칙 검증용 가짜 컨트롤러/ApiDocs (이 클래스 안에서만 쓴다) =====

    @RequestMapping("/fixture")
    interface CleanApiDocs {
        @GetMapping("/hello")
        String hello();
    }

    static class CleanController implements CleanApiDocs {
        @Override
        public String hello() {
            return "hello";
        }
    }

    @RequestMapping("/fixture")
    interface ExtraHandlerApiDocs {
        @GetMapping("/hello")
        String hello();
    }

    static class ExtraHandlerController implements ExtraHandlerApiDocs {
        @Override
        public String hello() {
            return "hello";
        }

        @GetMapping("/extra")
        public String extra() { // ApiDocs에 선언하지 않은 구현체 전용 핸들러
            return "extra";
        }
    }

    @RequestMapping("/fixture")
    interface DuplicatedMappingApiDocs {
        @GetMapping("/hello")
        String hello();
    }

    static class DuplicatedMappingController implements DuplicatedMappingApiDocs {
        @Override
        @GetMapping("/hello")
        public String hello() {
            return "hello";
        }
    }

    @RequestMapping("/fixture")
    interface DetachedApiDocs {
        @GetMapping("/hello")
        String hello();
    }

    static class DetachedController { // implements DetachedApiDocs 를 잃은 상태
        @GetMapping("/hello")
        public String hello() {
            return "hello";
        }
    }

    interface NoBasePathApiDocs { // 클래스 레벨 @RequestMapping이 없다
        @GetMapping("/hello")
        String hello();
    }

    static class NoBasePathController implements NoBasePathApiDocs {
        @Override
        public String hello() {
            return "hello";
        }
    }
}
