package com.flowerconnect.security;

import java.lang.reflect.Method;
import java.util.Set;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import io.swagger.v3.oas.annotations.Operation;

/**
 * OpenAPI documentation-coverage guard (plan task 14.3).
 *
 * <p>Every controller handler must carry an {@link Operation} annotation with a
 * non-empty {@code description} that documents both what the endpoint does and
 * who may call it. The description must state an access category drawn from the
 * reviewed access matrix ({@link EndpointAccessMatrix.AccessRule}): {@code
 * PUBLIC}, {@code CUSTOMER}, {@code FLORIST}, {@code ADMIN}, {@code ANY_AUTH},
 * {@code MULTI_ROLE}, {@code SIGNATURE}, or {@code SIMULATOR_NONPROD}. If a new
 * handler is added without the annotation, or without an access/role keyword in
 * its description, this test fails so API documentation cannot silently drift.
 *
 * <p>The test is intentionally scoped to FlowerConnect's own {@code
 * @RestController} classes so Spring's built-in error/resource handlers are not
 * flagged.
 */
@SpringBootTest
class OpenApiRoleCoverageTest {

    /**
     * Access-category keywords that every {@code @Operation#description} must
     * contain (matched case-insensitively). Mirrors
     * {@link EndpointAccessMatrix.AccessRule} plus common synonyms used in
     * descriptions.
     */
    private static final Set<String> ACCESS_KEYWORDS = Set.of(
            "PUBLIC", "CUSTOMER", "FLORIST", "ADMIN", "ANY_AUTH",
            "MULTI_ROLE", "SIGNATURE", "SIMULATOR_NONPROD", "AUTHENTICATED");

    @Autowired
    @Qualifier("requestMappingHandlerMapping")
    private RequestMappingHandlerMapping handlerMapping;

    @Test
    void everyControllerHandlerHasAnOperationWithAccessDocumentation() {
        var map = handlerMapping.getHandlerMethods();
        StringBuilder missing = new StringBuilder();
        int total = 0;

        for (var entry : map.entrySet()) {
            HandlerMethod handler = entry.getValue();
            Method method = handler.getMethod();
            Class<?> controller = method.getDeclaringClass();

            // Only FlowerConnect's own REST controllers; skip Spring's own
            // error/resource handlers and non-REST controllers.
            if (!controller.getName().startsWith("com.flowerconnect.")
                    || !controller.isAnnotationPresent(RestController.class)) {
                continue;
            }

            total++;
            Operation operation = method.getAnnotation(Operation.class);
            String description = operation == null ? null : operation.description();
            if (description == null || description.isBlank()
                    || !hasAccessKeyword(description)) {
                missing.append("  ")
                        .append(controller.getSimpleName())
                        .append("#")
                        .append(method.getName())
                        .append(" (")
                        .append(operation == null ? "missing @Operation"
                                : "description lacks an access/role keyword")
                        .append(")\n");
            }
        }

        Assertions.assertThat(total).as("controller handlers inspected").isGreaterThan(0);

        if (!missing.isEmpty()) {
            throw new AssertionError(
                    "The following controller handlers lack an @Operation with a non-empty "
                            + "description that states an access category/role "
                            + "(one of " + ACCESS_KEYWORDS + "):\n" + missing);
        }
    }

    private static boolean hasAccessKeyword(String description) {
        String upper = description.toUpperCase();
        return ACCESS_KEYWORDS.stream().anyMatch(upper::contains);
    }
}
